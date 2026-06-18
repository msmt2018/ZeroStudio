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
import dalvik.system.InMemoryDexClassLoader
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Dex 运行时加载器 v3.1.
 *
 * ## v3.1 vs v3 关键简化
 *
 * v3 还有 `previewDex / projectDex / runtimeDex` 三个独立参数, v3.1 合并成单一 `dexFiles`
 * 列表. 因为 v3.1 不再走 K2 进程内编译, 也不再有 `assets/compose-jars.zip` 中的
 * `compose-runtime.dex`, 唯一的 dex 来源就是 **gradle assemble 产物** —
 * [com.itsaky.androidide.compose.preview.data.source.ProjectContext.projectDexFiles].
 *
 * ## 类加载链
 *
 * ```
 *   InMemoryDexClassLoader / DexClassLoader
 *       │
 *       ├── dexFiles    <- 项目 dex (含用户 Composable + 用户项目其它代码)
 *       │
 *       └── parent      <- IDE 主 APK 的 PathClassLoader
 *                          包含 androidx.compose.runtime / ui / foundation / material3
 *                          等 IDE compile classpath. 用户 dex 引用 androidx.compose.*
 *                          时, 通过 parent 委托解析. **不需要** 单独的 compose-runtime.dex.
 * ```
 *
 * ## 关键特性
 *
 * 1. **API 26+ 优先 [InMemoryDexClassLoader]**: 不再依赖磁盘优化目录, 避免 ART odex 抖动.
 * 2. **parent = context.classLoader**: 委托到 IDE 主 classpath, 让用户 dex 透明使用 compose runtime.
 * 3. **详细日志**: 每一步都输出 [LOG.info] 记录 dex 来源, 方便排查"为什么 dex 没加载".
 * 4. **可观测性**: 暴露 [loadedDexCount] / [classCacheSize] 给上层做监控.
 */
class DexRuntime private constructor(
    private val context: Context,
    private val parent: ClassLoader,
    private val loader: ClassLoader,
    private val dexSources: List<String>,
) {

    private val LOG = LoggerFactory.getLogger(DexRuntime::class.java)

    private val classCache = ConcurrentHashMap<String, Class<*>>()

    /** 全部加载的 dex 文件数. */
    val loadedDexCount: Int = dexSources.size

    /** 加载且缓存过的类数量. */
    @Volatile
    var classCacheSize: Int = 0
        private set

    /**
     * 加载指定类名. 先查缓存, 再走 [ClassLoader.loadClass].
     *
     * @return 已加载的 [Class], 找不到则返回 null (绝不抛异常, 避免渲染流程崩).
     */
    fun loadClass(className: String): Class<*>? {
        if (className.isBlank()) return null
        classCache[className]?.let { return it }
        return try {
            val clazz = Class.forName(className, false, loader)
            if (clazz != null) {
                classCache[className] = clazz
                classCacheSize = classCache.size
            }
            clazz
        } catch (e: ClassNotFoundException) {
            LOG.warn("Class not found in dex runtime: {}", className)
            null
        } catch (e: Throwable) {
            LOG.error("Failed to load class: {}", className, e)
            null
        }
    }

    /** dex 源文件路径, 用于调试. */
    fun dexSources(): List<String> = dexSources.toList()

    /** 释放缓存. ClassLoader 本身没有 close, 等 GC. */
    fun release() {
        classCache.clear()
        classCacheSize = 0
        LOG.info("DexRuntime released ({} dex files were loaded)", dexSources.size)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DexRuntime::class.java)

        /**
         * 把给定 dex 文件一次性加载到一个 [ClassLoader] 链中, 返回 [DexRuntime].
         *
         * @param dexFiles  gradle assemble 产物的 dex 文件 (来自
         *                  [com.itsaky.androidide.compose.preview.data.source.ProjectContext.projectDexFiles]).
         *
         * @return [DexRuntime] 实例; 若所有 dex 都加载失败则返回带空 loader 的实例,
         *         后续 [loadClass] 会持续返回 null, 由上层展示错误 UI.
         */
        fun loadAll(
            context: Context,
            dexFiles: List<File>,
        ): DexRuntime {
            val allDex = dexFiles
                .filter { it.exists() && it.length() > 0 }
                .distinctBy { it.absolutePath }

            if (allDex.isEmpty()) {
                LOG.error("DexRuntime: no dex files available")
                return DexRuntime(context, context.classLoader, context.classLoader, emptyList())
            }

            val parent = context.classLoader
            val loader = createClassLoader(context, parent, allDex)

            LOG.info(
                "DexRuntime: loaded {} dex files; parent={}",
                allDex.size,
                parent::class.java.name,
            )
            allDex.forEach { LOG.info("  - dex: {}", it.absolutePath) }

            return DexRuntime(
                context = context,
                parent = parent,
                loader = loader,
                dexSources = allDex.map { it.absolutePath },
            )
        }

        private fun createClassLoader(
            context: Context,
            parent: ClassLoader,
            dexFiles: List<File>,
        ): ClassLoader {
            // 优先使用 InMemoryDexClassLoader (API 26+): 完全跳过 dex2oat, 即时加载.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val buffers = dexFiles.map { dexFile ->
                    FileInputStream(dexFile).use { fis ->
                        val bytes = fis.readBytes()
                        ByteBuffer.wrap(bytes)
                    }
                }
                if (buffers.isNotEmpty()) {
                    val arr = buffers.toTypedArray()
                    return try {
                        InMemoryDexClassLoader(arr, parent)
                    } catch (e: Throwable) {
                        LOG.warn("InMemoryDexClassLoader failed, falling back to DexClassLoader", e)
                        createDiskClassLoader(context, parent, dexFiles)
                    }
                }
            }
            return createDiskClassLoader(context, parent, dexFiles)
        }

        private fun createDiskClassLoader(
            context: Context,
            parent: ClassLoader,
            dexFiles: List<File>,
        ): ClassLoader {
            val optimizedDir = File(context.codeCacheDir, "compose_preview_dex_runtime").apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            val dexPath = dexFiles.joinToString(File.pathSeparator) { it.absolutePath }
            return DexClassLoader(dexPath, optimizedDir.absolutePath, null, parent)
        }
    }
}
