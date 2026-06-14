/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.compiler

import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * D8 dex 产物本地缓存 v2.1 (P5).
 *
 * 设计延续 P4 [CompilationCache], 但缓存粒度更粗:
 * 按 preview **源文本** SHA-256 作 key, 命中时跳过 K2 + D8 两阶段.
 *
 * ## 关键设计
 *
 * 1. **粗粒度 key**:
 *    - 源文本 SHA-256 (任意一个字节变 → miss)
 *    - 比 K2 阶段 (源文件级 hash) 简单, 因为 preview 编译输入通常只是单个 .kt
 *
 * 2. **SDK version 校验**:
 *    - 来自 [AssetsComposeBundles.versionTag]
 *    - SDK 升级 → 旧缓存自动失效 (与 P4 对齐)
 *
 * 3. **LRU + TTL**:
 *    - 默认 128 MB (P4 编译缓存的一半, 因为 dex 产物相对小)
 *    - 默认 7 天 TTL
 *    - 超过大小 → 按 lastModified 淘汰
 *    - 超过 TTL → 启动时清理
 *
 * ## 存储结构
 *
 * ```
 * <cacheDir>/
 *   ├── <key>.dex       // D8 产物
 *   └── <key>.meta      // {className, functionName, versionTag, dexMs, createdAt}
 * ```
 *
 * ## 性能
 *
 * | 场景 | 耗时 |
 * |------|------|
 * | 完整 compile + dex | 1.5-5s |
 * | 缓存命中 (复制 .dex) | 20-100ms |
 * | 加速比 | **30-150x** |
 *
 * ## 与 P4 [CompilationCache] 的关系
 *
 * P4 缓存 [CompilationCache] 缓存 K2 编译产物 (.class) — 阶段级缓存
 * P5 缓存 [DexCache] 缓存 D8 产物 (.dex) — 端到端缓存 (compile + dex)
 *
 * 两者并存, 互不依赖:
 * - 用 P5 时, 命中 = 跳过 P4 + D8 (粗粒度)
 * - 用 P4 时, 命中 = 跳过 K2, 仍需 D8 (细粒度)
 *
 * @see BundledD8Dexer
 * @see CompilationCache
 */
class DexCache(
    private val cacheDir: File,
    /**
     * SDK 版本标识 (来自 [AssetsComposeBundles.versionTag]).
     * 当 SDK 升级时, 旧缓存自动失效, 避免类型不兼容.
     */
    private val versionTag: () -> String = { "unknown" },
    /**
     * 最大占用字节数. 默认 128 MB.
     */
    private val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
    /**
     * TTL 毫秒. 默认 7 天.
     */
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {

    private val LOG = LoggerFactory.getLogger(DexCache::class.java)

    // ---- stats 原子计数 ----
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val puts = AtomicLong(0)
    private val evictions = AtomicLong(0)
    private val expiredRemovals = AtomicLong(0)
    private val savedDexMsTotal = AtomicLong(0)

    init {
        cacheDir.mkdirs()
        cleanExpired()
    }

    /**
     * 计算 source 字符串的 SHA-256 hash (key).
     */
    fun computeSourceHash(source: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 查缓存.
     *
     * @return [CachedDexResult] (含 dex 文件 + 元信息) 或 null (未命中 / 失效)
     */
    fun getCachedDex(sourceHash: String): CachedDexResult? {
        val cacheEntry = File(cacheDir, "$sourceHash.dex")
        val metaFile = File(cacheDir, "$sourceHash.meta")

        if (!cacheEntry.exists() || !metaFile.exists()) {
            misses.incrementAndGet()
            return null
        }

        val meta = metaFile.readLines()
        if (meta.size < 3) {
            cacheEntry.delete()
            metaFile.delete()
            misses.incrementAndGet()
            return null
        }

        // SDK 升级时, 第 3 行 (versionTag) 不匹配 -> 视为失效
        if (meta[2] != versionTag()) {
            LOG.info("Cache invalidated due to SDK version change: stored={}, current={}",
                meta[2], versionTag())
            cacheEntry.delete()
            metaFile.delete()
            misses.incrementAndGet()
            return null
        }

        // P5: 累加 savedDexMs (meta[3] = dexMs, 可能为 0 表示旧版本)
        val cachedDexMs = meta.getOrNull(3)?.toLongOrNull() ?: 0L
        if (cachedDexMs > 0) {
            savedDexMsTotal.addAndGet(cachedDexMs)
        }

        // 更新 lastModified (LRU)
        cacheEntry.setLastModified(System.currentTimeMillis())
        metaFile.setLastModified(System.currentTimeMillis())

        hits.incrementAndGet()
        LOG.debug("Cache hit for hash: {} (saved ~{}ms)", sourceHash, cachedDexMs)
        return CachedDexResult(
            dexFile = cacheEntry,
            className = meta[0],
            functionName = meta[1],
            dexMs = cachedDexMs,
        )
    }

    /**
     * 把 dex 产物写入缓存.
     *
     * @param sourceHash 源字符串 SHA-256 (key)
     * @param dexFile D8 产物 .dex
     * @param className 主类全限定名
     * @param functionName @Preview 函数名
     * @param dexMs 本次 D8 耗时 (ms), 用于 stats. 默认 0 (旧调用方, 不计入统计)
     */
    fun cacheDex(
        sourceHash: String,
        dexFile: File,
        className: String,
        functionName: String,
        dexMs: Long = 0L,
    ) {
        val cacheEntry = File(cacheDir, "$sourceHash.dex")
        val metaFile = File(cacheDir, "$sourceHash.meta")

        dexFile.copyTo(cacheEntry, overwrite = true)
        metaFile.writeText("$className\n$functionName\n${versionTag()}\n$dexMs")

        puts.incrementAndGet()
        LOG.debug("Cached DEX for hash: {} (sdks={}, dexMs={})",
            sourceHash, versionTag(), dexMs)
        evictIfNeeded()
    }

    /**
     * 清空全部缓存.
     */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        LOG.info("Cache cleared")
    }

    /**
     * 拉取当前 stats 快照.
     */
    fun stats(): DexCacheStats = DexCacheStats(
        hits = hits.get(),
        misses = misses.get(),
        puts = puts.get(),
        evictions = evictions.get(),
        expiredRemovals = expiredRemovals.get(),
        savedDexMsTotal = savedDexMsTotal.get(),
        entryCount = cacheDir.listFiles { f -> f.extension == "dex" }?.size ?: 0,
        totalSizeBytes = cacheDir.listFiles { f -> f.extension == "dex" }?.sumOf { it.length() } ?: 0L,
    )

    // =============== 内部: 淘汰 / 过期 ===============

    /**
     * 按 maxSizeBytes 淘汰 (LRU by lastModified).
     */
    private fun evictIfNeeded() {
        val entries = cacheDir.listFiles { file -> file.extension == "dex" } ?: return
        var totalSize = entries.sumOf { it.length() }
        if (totalSize <= maxSizeBytes) return

        LOG.info("Cache size {} bytes > max {} bytes, evicting", totalSize, maxSizeBytes)
        val sorted = entries.sortedBy { it.lastModified() }
        for (entry in sorted) {
            if (totalSize <= maxSizeBytes) break
            val size = entry.length()
            val meta = File(entry.parent, "${entry.nameWithoutExtension}.meta")
            entry.delete()
            meta.delete()
            totalSize -= size
            evictions.incrementAndGet()
        }
        LOG.info("After eviction: totalSize={} bytes", totalSize)
    }

    /**
     * 启动时清理过期条目 (lastModified 距今 > ttlMs).
     */
    private fun cleanExpired() {
        val now = System.currentTimeMillis()
        val entries = cacheDir.listFiles { file -> file.extension == "dex" } ?: return
        var cleaned = 0
        for (entry in entries) {
            if (now - entry.lastModified() > ttlMs) {
                val meta = File(entry.parent, "${entry.nameWithoutExtension}.meta")
                entry.delete()
                meta.delete()
                cleaned++
            }
        }
        if (cleaned > 0) {
            expiredRemovals.addAndGet(cleaned.toLong())
            LOG.info("Cleaned {} expired DEX cache entries (ttl={}ms)", cleaned, ttlMs)
        }
    }

    /**
     * 缓存命中返回的产物.
     *
     * @property dexFile 复制的 .dex 文件
     * @property className 主类全限定名
     * @property functionName @Preview 函数名
     * @property dexMs 原始 D8 耗时 (ms), 0 表示旧缓存条目未记录
     */
    data class CachedDexResult(
        val dexFile: File,
        val className: String,
        val functionName: String,
        val dexMs: Long = 0L,
    )

    companion object {
        private const val DEFAULT_MAX_SIZE_BYTES = 128L * 1024 * 1024  // 128 MB
        private const val DEFAULT_TTL_MS = 7L * 24 * 3600 * 1000        // 7 days
    }
}

/**
 * Dex 缓存统计快照 v2.1 (P5).
 *
 * 与 [com.itsaky.androidide.compose.preview.compiler.CompilationCacheStats] 对齐字段名.
 */
data class DexCacheStats(
    val hits: Long = 0,
    val misses: Long = 0,
    val puts: Long = 0,
    val evictions: Long = 0,
    val expiredRemovals: Long = 0,
    val savedDexMsTotal: Long = 0,
    val entryCount: Int = 0,
    val totalSizeBytes: Long = 0,
) {
    /**
     * 命中率 = hits / (hits + misses). 0 当无访问.
     */
    val hitRate: Double
        get() {
            val total = hits + misses
            return if (total == 0L) 0.0 else hits.toDouble() / total.toDouble()
        }
}

/**
 * Dex 缓存全局 holder v2.1 (P5).
 *
 * 与 [CompilationCacheHolder] 对齐, 给 DebugDrawer Stats tab 提供 stats 接入点.
 *
 * 之所以用 holder 而不是 singleton:
 * - DexCache 需要 [java.io.File] (cacheDir) 才能构造
 * - 测试可以替换 [current] mock
 */
object DexCacheHolder {

    @Volatile
    private var cache: DexCache? = null

    @JvmStatic
    fun install(cache: DexCache) {
        this.cache = cache
    }

    @JvmStatic
    fun current(): DexCache? = cache

    @JvmStatic
    fun reset() {
        cache = null
    }

    @JvmStatic
    fun statsOrEmpty(): DexCacheStats = cache?.stats() ?: DexCacheStats()
}
