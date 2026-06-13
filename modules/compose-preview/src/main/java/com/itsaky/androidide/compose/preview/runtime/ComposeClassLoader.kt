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
class ComposeClassLoader(private val context: Context) {

    private val LOG = LoggerFactory.getLogger(ComposeClassLoader::class.java)

    private val loaderPool = ConcurrentHashMap<String, DexClassLoader>()
    private val classCache = ConcurrentHashMap<String, Class<*>>()

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
        LOG.info("Created DexClassLoader: {} dex files, key={}", dexFiles.size, cacheKey.takeLast(80))
        return loader
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ComposeClassLoader::class.java)
    }
}
