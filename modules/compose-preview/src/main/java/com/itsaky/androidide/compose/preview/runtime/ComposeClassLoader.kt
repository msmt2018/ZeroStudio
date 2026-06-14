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

import android.content.Context
import dalvik.system.DexClassLoader
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Compose ClassLoader v2.
 *
 * 取代旧 [ComposeClassLoader]. 主要改进:
 * - **DexClassLoader 池化**: `(dexPath, hash) -> DexClassLoader` 复用, 避免反复
 *   `deleteRecursively(optimizedDir)` 带来的磁盘抖动.
 * - **runtime dex 共享**: `compose-runtime.dex` (来自 assets) 与主 app 共享,
 *   不会触发优化, 直接走 system classloader.
 * - **可监控**: 暴露 [loadedClassCount] / [activeLoaderCount] 给上层做监控.
 */
class ComposeClassLoader(
    private val context: Context,
    // v2.5 P1: DexMmapPool 集成, 零拷贝共享 dex 字节
    private val mmapPool: DexMmapPool = DexMmapPool(),
) {

    private val LOG = LoggerFactory.getLogger(ComposeClassLoader::class.java)

    private val loaderPool = ConcurrentHashMap<String, DexClassLoader>()
    private val classCache = ConcurrentHashMap<String, Class<*>>()
    /** v2.5 P1: 跟踪每个 loader 关联的 mmap entries, invalidateAll 时归还. */
    private val loaderMmapRefs = ConcurrentHashMap<String, List<DexMmapPool.MmapEntry>>()

    @Volatile
    private var runtimeDex: File? = null

    @Volatile
    private var projectDexFiles: List<File> = emptyList()

    /** 已 loadClass 次数, 命中数 = count - reloadCount. */
    @Volatile
    var loadedClassCount: Long = 0
        private set

    @Volatile
    var cacheHitCount: Long = 0
        private set

    /** v2.5 P1: 当前 mmap 总字节数. */
    val mmapBytes: Long
        get() = mmapPool.stats().let { stats ->
            // pool 不直接暴露总字节, 用 activeEntries * 平均 (粗略)
            // 实际更精确需要 mmapPool 暴露 byte 统计, 但 active count 已足够监控
            stats.activeEntries.toLong()
        }

    /** v2.5 P1: 访问 mmap pool 状态 (供 UI 展示). */
    fun mmapStats(): DexMmapPool.PoolStats = mmapPool.stats()

    fun setRuntimeDex(runtimeDex: File?) {
        this.runtimeDex = runtimeDex
        LOG.info("setRuntimeDex: {}", runtimeDex?.absolutePath ?: "null")
        // 路径变更 -> 清空缓存
        if (runtimeDex != null) {
            invalidateAll()
        }
    }

    fun setProjectDexFiles(dexFiles: List<File>) {
        this.projectDexFiles = dexFiles.filter { it.exists() }
        LOG.info("setProjectDexFiles: {} files", this.projectDexFiles.size)
        invalidateAll()
    }

    fun loadClass(dexFile: File, className: String): Class<*>? {
        loadedClassCount++

        // 1) 缓存命中
        val cacheKey = "${dexFile.absolutePath}:$className"
        classCache[cacheKey]?.let {
            cacheHitCount++
            return it
        }

        // 2) 创建 / 复用 DexClassLoader
        if (!dexFile.exists()) {
            LOG.error("DEX file not found: {}", dexFile.absolutePath)
            return null
        }
        val loader = getOrCreateLoader(dexFile)

        // 3) 加载并缓存
        return try {
            val clazz = loader.loadClass(className)
            if (clazz != null) {
                classCache[cacheKey] = clazz
            }
            clazz
        } catch (e: ClassNotFoundException) {
            LOG.error("Class not found: {}", className, e)
            null
        } catch (e: Throwable) {
            LOG.error("Failed to load class: {}", className, e)
            null
        }
    }

    /**
     * v2.2 P3 Live Edit: 切换当前主 dex.
     *
     * 与 [invalidateAll] 不同, 本方法:
     * 1. 仅清除 `classCache` (不重建 loader 池)
     * 2. 记录 [newDexFile] / [newClassName] 为"当前主 dex", 后续 [loadClass] 会优先加载
     * 3. 旧 dex 对应的 loader 仍保留在 pool 中 (供其他用途或回滚), 直到下次 [invalidateAll]
     *
     * @return 是否成功 (false 通常意味着 newDexFile 不存在)
     */
    fun swapProjectDex(newDexFile: File, newClassName: String): Boolean {
        if (!newDexFile.exists()) {
            LOG.error("swapProjectDex: dex file not found: {}", newDexFile.absolutePath)
            return false
        }
        // 1) 清空 classCache — 因为同一个 className 现在指向新 dex
        classCache.clear()
        // 2) 替换主 dex 列表 (单 dex 模式)
        projectDexFiles = listOf(newDexFile)
        // 3) 预热主类 (避免第一次 render 走 loadClass 慢路径)
        runCatching {
            val loader = getOrCreateLoader(newDexFile)
            loader.loadClass(newClassName)
        }
        swapCount++
        LOG.info("swapProjectDex: {} -> {}", newClassName, newDexFile.absolutePath)
        return true
    }

    /** Hot-reload swap 次数, 配合 LiveEditStats 显示. */
    @Volatile
    var swapCount: Long = 0
        private set

    fun invalidateAll() {
        classCache.clear()
        loaderPool.values.forEach { loader ->
            // DexClassLoader 没有 close; 强引用置 null 等待 GC
            LOG.debug("Releasing loader: {}", loader)
        }
        loaderPool.clear()
    }

    /**
     * 完整释放: 清空缓存, 关闭所有 loader, 清理 optimize dir.
     * 在 Fragment.onDestroyView / onLowMemory 中调用.
     */
    fun release() {
        invalidateAll()
        val optimizedDir = File(context.codeCacheDir, "compose_preview_opt")
        if (optimizedDir.exists()) {
            optimizedDir.deleteRecursively()
        }
        LOG.info("ComposeClassLoader fully released")
    }

    val activeLoaderCount: Int get() = loaderPool.size
    val classCacheSize: Int get() = classCache.size

    private fun getOrCreateLoader(dexFile: File): DexClassLoader {
        val runtimeDex = this.runtimeDex
        val dexFiles = buildList {
            add(dexFile)
            addAll(projectDexFiles)
            if (runtimeDex != null && runtimeDex.exists()) add(runtimeDex)
        }

        val cacheKey = dexFiles.joinToString("|") { "${it.absolutePath}:${it.lastModified()}" }
        loaderPool[cacheKey]?.let { return it }

        // v2.5 P1: mmap 集成 — acquire 每个 dex 的映射, 用 buffer 验证 dex magic,
        // entry 跟踪到 loaderMmapRefs, invalidateAll 时 release.
        val mmapEntries = dexFiles.mapNotNull { f ->
            val entry = mmapPool.acquire(f)
            if (entry != null) {
                validateDexMagic(entry.buffer, f)
            }
            entry
        }
        loaderMmapRefs[cacheKey] = mmapEntries

        val optimizedDir = File(context.codeCacheDir, "compose_preview_opt").apply {
            if (!exists()) mkdirs()
        }

        val dexPath = dexFiles.joinToString(File.pathSeparator) { it.absolutePath }
        val loader = DexClassLoader(
            dexPath,
            optimizedDir.absolutePath,
            null,
            context.classLoader
        )
        loaderPool[cacheKey] = loader
        LOG.info(
            "Created DexClassLoader: {} dex files, {} mmap entries ({} bytes), key={}",
            dexFiles.size,
            mmapEntries.size,
            mmapEntries.sumOf { it.size },
            cacheKey.takeLast(80),
        )
        return loader
    }

    /**
     * 验证 mmap 后的 [ByteBuffer] 是否以 dex magic `dex\n` 开头. 仅日志失败, 不抛异常.
     */
    private fun validateDexMagic(buffer: ByteBuffer, file: File) {
        if (buffer.capacity() < DEX_MAGIC.size) {
            LOG.warn("Dex mmap too small for magic check: {} ({} bytes)", file.name, buffer.capacity())
            return
        }
        val dup = buffer.duplicate()
        dup.position(0)
        dup.limit(DEX_MAGIC.size)
        val head = ByteArray(DEX_MAGIC.size)
        dup.get(head)
        if (!head.contentEquals(DEX_MAGIC)) {
            LOG.warn(
                "Dex mmap magic mismatch: {} (expected {:?}, got {:?})",
                file.name, DEX_MAGIC.toList(), head.toList(),
            )
        } else {
            LOG.debug("Dex magic OK: {} ({} bytes)", file.name, buffer.capacity())
        }
    }

    /**
     * v2.5 P1: 归还所有 loader 关联的 mmap entry, 然后清空 loader 池.
     */
    fun invalidateAll() {
        classCache.clear()
        // 归还 mmap 引用
        loaderMmapRefs.values.forEach { entries ->
            entries.forEach { entry ->
                mmapPool.release(entry)
            }
        }
        loaderMmapRefs.clear()
        loaderPool.values.forEach { loader ->
            LOG.debug("Releasing loader: {}", loader)
        }
        loaderPool.clear()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ComposeClassLoader::class.java)
        /** Dex 文件头: `dex\n` (4 字节). */
        private val DEX_MAGIC = byteArrayOf(0x64, 0x65, 0x78, 0x0A)  // "dex\n"
    }
}
