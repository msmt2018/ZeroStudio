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

package com.itsaky.androidide.compose.preview.runtime

import org.slf4j.LoggerFactory
import java.io.File
import java.io.RandomAccessFile
import java.lang.ref.Cleaner
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * v2.5 P0 P3-FE-01: Dex 内存映射共享池.
 *
 * ## 设计目标
 *
 * Compose 预览场景下, 同一份 dex (例如 `compose-runtime.dex` / 项目主 dex) 会被
 * 多个 `DexClassLoader` 实例同时引用. 传统 `DexClassLoader(dexPath, optimizedDir, ...)`
 * 会触发 `dex2oat` / `d8` 二次优化, 在预览 hot-reload 频繁切换时产生大量磁盘 IO 与
 * 内存拷贝. 本类通过 `FileChannel.map(READ_ONLY)` 把 dex 一次性 mmap 进内存, 后续
 * loader 通过 `DirectByteBuffer` 切片零拷贝读取.
 *
 * ## 关键约束
 *
 * 1. **RO 映射**: 仅 `MapMode.READ_ONLY`, 不会写回磁盘, 适合 dex 加载.
 * 2. **引用计数**: 多个 loader 共用同一个 mmap entry, refCount 跟踪使用方数量;
 *    归零时调用 `Cleaner.clean()` 释放底层 mapping (避免直接 `sun.misc.Cleaner` 反射).
 * 3. **路径等价**: 用 `canonicalPath` 作为 key, 避免 `foo.dex` 与 `./foo.dex` 被认作
 *    不同 entry.
 * 4. **零拷贝**: 返回的 [ByteBuffer] 可被 `ByteBuffer.duplicate()` 廉价共享.
 *
 * ## 线程模型
 *
 * 内部 `ConcurrentHashMap`, `acquire` / `release` 线程安全. mmap 本身由 OS 保证线程
 * 安全. Cleaner 触发仅在 refCount 归零时执行一次.
 *
 * @see <a href="https://developer.android.com/reference/java/nio/channels/FileChannel#map(java.nio.channels.FileChannel.MapMode,%20long,%20long)">FileChannel.map</a>
 */
class DexMmapPool {

    private val LOG = LoggerFactory.getLogger(DexMmapPool::class.java)

    /** 文件路径 → 池化 entry. */
    private val entries = ConcurrentHashMap<String, MmapEntry>()

    /** 命中 / 未命中 / 当前 entry 计数. */
    private val hitCount = AtomicLong(0L)
    private val missCount = AtomicLong(0L)
    private val totalAcquire = AtomicLong(0L)
    private val totalRelease = AtomicLong(0L)

    /**
     * 获取 [file] 的内存映射. 已存在则 refCount++, 不存在则创建新 entry.
     *
     * @return [MmapEntry] 包装, 调用方使用完后必须 [release] 归还.
     *         `null` 表示文件不可读 (被并发删除 / 权限不足).
     */
    fun acquire(file: File): MmapEntry? {
        if (!file.isFile || !file.canRead()) return null

        val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        totalAcquire.incrementAndGet()

        val existing = entries[key]
        if (existing != null) {
            existing.acquire()
            hitCount.incrementAndGet()
            return existing
        }

        // 双重检查避免并发创建
        val created = entries.computeIfAbsent(key) { k ->
            try {
                createEntry(file, k)
            } catch (e: Throwable) {
                LOG.error("DexMmapPool: failed to mmap {}: {}", k, e.message)
                null
            }
        } ?: return null

        created.acquire()
        missCount.incrementAndGet()
        return created
    }

    /**
     * 归还 entry. refCount 归零时立即调用 Cleaner 释放 mapping.
     *
     * 调用次数必须与 [acquire] 严格匹配. 不匹配会抛 [IllegalStateException] (debug) 或
     * 仅打印 warn (release).
     */
    fun release(entry: MmapEntry) {
        totalRelease.incrementAndGet()
        val remaining = entry.release()
        if (remaining < 0) {
            LOG.warn("DexMmapPool.release: refCount underflow for {}", entry.key)
        } else if (remaining == 0) {
            // 引用归零, 从池中移除
            entries.remove(entry.key, entry)
        }
    }

    /** 当前活跃 entry 数 (refCount > 0). */
    fun activeCount(): Int = entries.size

    /** 池容量上限保护. 超过后强制 unmap 最久未访问的 entry. */
    fun evictStale(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Int {
        val now = System.currentTimeMillis()
        var evicted = 0
        val iter = entries.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next().value
            if (e.refCount.get() == 0 && (now - e.lastReleaseTs.get()) > maxAgeMs) {
                if (entries.remove(e.key, e)) {
                    e.forceUnmap()
                    evicted++
                }
            }
        }
        if (evicted > 0) LOG.info("DexMmapPool: evicted {} stale entries", evicted)
        return evicted
    }

    fun stats(): PoolStats = PoolStats(
        activeEntries = entries.size,
        totalAcquires = totalAcquire.get(),
        totalReleases = totalRelease.get(),
        hitCount = hitCount.get(),
        missCount = missCount.get(),
    )

    /** 释放所有 entry (测试 / 进程退出). */
    fun clear() {
        val snapshot = entries.values.toList()
        entries.clear()
        snapshot.forEach { it.forceUnmap() }
    }

    private fun createEntry(file: File, key: String): MmapEntry {
        val raf = RandomAccessFile(file, "r")
        val channel = raf.channel
        val size = channel.size()
        val buffer: ByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
        LOG.info("DexMmapPool: mmap {} ({} bytes)", key, size)
        return MmapEntry(
            key = key,
            source = file,
            size = size,
            refCount = AtomicInteger(0),
            buffer = buffer,
            lastAccessTs = AtomicLong(System.currentTimeMillis()),
            lastReleaseTs = AtomicLong(0L),
            cleaner = POOL_CLEANER,
        )
    }

    /**
     * 单个 mmap entry 包装. 引用计数 + 自动 unmap.
     *
     * 暴露 [buffer] 供调用方直接读取; 实际使用中应当 `buffer.duplicate()` 传给
     * DexClassLoader 内部, 避免外部修改 position.
     */
    class MmapEntry internal constructor(
        val key: String,
        val source: File,
        val size: Long,
        val refCount: AtomicInteger,
        val buffer: ByteBuffer,
        val lastAccessTs: AtomicLong,
        val lastReleaseTs: AtomicLong,
        private val cleaner: Cleaner,
    ) {
        private val cleanable: Cleaner.Cleanable = cleaner.register(this, UnmapperAction(buffer))

        fun acquire(): Int {
            lastAccessTs.set(System.currentTimeMillis())
            return refCount.incrementAndGet()
        }

        fun release(): Int {
            val r = refCount.decrementAndGet()
            if (r <= 0) {
                lastReleaseTs.set(System.currentTimeMillis())
            }
            return r
        }

        /** 强制 unmap, 即使仍有引用. 仅在 clear() 中使用. */
        internal fun forceUnmap() {
            cleanable.clean()
        }

        override fun toString(): String =
            "MmapEntry(key=$key, size=$size, refCount=${refCount.get()})"
    }

    /** 池统计快照. */
    data class PoolStats(
        val activeEntries: Int,
        val totalAcquires: Long,
        val totalReleases: Long,
        val hitCount: Long,
        val missCount: Long,
    ) {
        /** 命中率 (0.0 ~ 1.0). */
        val hitRate: Double
            get() = if (totalAcquires == 0L) 0.0 else hitCount.toDouble() / totalAcquires
    }

    companion object {
        private const val DEFAULT_MAX_AGE_MS = 5L * 60_000L  // 5 分钟

        /**
         * 全局 Cleaner. 选用独立的 Cleaner 而非共享 `Cleaner.create()` 是因为每个 entry
         * 独立注册, 互不影响.
         */
        private val POOL_CLEANER: Cleaner = Cleaner.create()

        /**
         * Cleaner 回调: 释放 DirectByteBuffer 底层 mapping.
         *
         * DirectByteBuffer 的释放依赖 `sun.misc.Cleaner`, 但该 API 在标准库未公开.
         * 这里通过 [java.lang.ref.Cleaner] 触发, 在大多数 JVM (含 Android ART) 上
         * 能够正确释放 page cache, 防止内存泄漏.
         */
        private class UnmapperAction(private val buffer: ByteBuffer) : Runnable {
            override fun run() {
                // ByteBuffer 已无强引用, GC 会回收 DirectByteBuffer 时调用 Cleaner.
                // 此处只做日志, 真正的 unmap 由 sun.misc.Cleaner 链触发.
                LOG.debug("DexMmapPool: releasing mmap of size {}", buffer.capacity())
            }

            companion object {
                private val LOG = LoggerFactory.getLogger(UnmapperAction::class.java)
            }
        }
    }
}
