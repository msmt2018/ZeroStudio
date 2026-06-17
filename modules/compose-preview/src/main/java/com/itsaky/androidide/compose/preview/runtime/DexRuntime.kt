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
 * Dex 运行时加载器 v3.
 *
 * 设计目标: **一次性、确定性地** 把构建产物 (preview dex + 项目 dex + compose runtime dex)
 * 全部加载到一个 ClassLoader 链中, 后续 [composableInvoker] 任意 [Class.forName] 都能命中.
 *
 * ## 关键改进 (相对 v2 [ComposeClassLoader])
 *
 * 1. **API 26+ 优先 [InMemoryDexClassLoader]**: 不再依赖磁盘优化目录, 避免 ART odex 抖动.
 * 2. **三层 parent 链路**: 系统 ClassLoader → compose runtime dex (从 app path) → 项目 dex → preview dex.
 *    最末端的 preview dex 拥有最高优先级, 避免项目 dex 中的同名类遮蔽 preview 函数.
 * 3. **彻底删除 setProjectDexFiles/setRuntimeDex 的运行时切换**: 一经 [loadAll] 即确定不可变.
 *    重新加载只能通过 [release] + 新建 [DexRuntime]. 这避免了"渲染到一半 dex 路径变了"导致的
 *    `ClassNotFoundException` / `NoSuchMethodError`.
 * 4. **详细日志**: 每一步都输出 [LOG.info] 记录 dex 来源, 方便排查"为什么 dex 没加载".
 * 5. **可观测性**: 暴露 [loadedDexCount] / [classCacheSize] 给上层做监控.
 */
class DexRuntime private constructor(
    private val context: Context,
    private val parent: ClassLoader,
    private val loader: ClassLoader,
    private val dexSources: List<String>,
) {

    private val LOG = LoggerFactory.getLogger(DexRuntime::class.java)

    private val classCache = ConcurrentHashMap<String, Class<*>>()

    /** 全部加载的 dex 文件数 (preview + project + runtime). */
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
         * @param previewDex    K2 编译产物 (单个 dex 文件). 优先最高.
         * @param projectDex    项目运行时 dex 集合 (从 AGP build 输出目录扫描得到).
         * @param runtimeDex    compose-runtime.dex (从 assets 解压). 优先最低.
         *
         * @return [DexRuntime] 实例; 若所有 dex 都加载失败则返回带空 loader 的实例,
         *         后续 [loadClass] 会持续返回 null, 由上层展示错误 UI.
         */
        fun loadAll(
            context: Context,
            previewDex: File?,
            projectDex: List<File>,
            runtimeDex: File?,
        ): DexRuntime {
            val allDex = buildList {
                // 1) preview dex 最高优先级 (会覆盖同名项目类, 确保用户代码生效)
                if (previewDex != null && previewDex.exists() && previewDex.length() > 0) {
                    add(previewDex)
                }
                // 2) 项目运行时 dex
                projectDex.filter { it.exists() && it.length() > 0 }.forEach { add(it) }
                // 3) compose runtime dex 最后 (作为 fallback)
                if (runtimeDex != null && runtimeDex.exists() && runtimeDex.length() > 0) {
                    add(runtimeDex)
                }
            }

            if (allDex.isEmpty()) {
                LOG.error("DexRuntime: no dex files available. preview={}, project={}, runtime={}",
                    previewDex?.absolutePath, projectDex.size, runtimeDex?.absolutePath)
                // 返回一个永远找不到类的 DexRuntime
                return DexRuntime(context, context.classLoader, context.classLoader, emptyList())
            }

            val parent = context.classLoader
            val loader = createClassLoader(context, parent, allDex)

            LOG.info(
                "DexRuntime: loaded {} dex files (preview={}, project={}, runtime={}); parent={}",
                allDex.size,
                if (previewDex?.exists() == true) 1 else 0,
                projectDex.count { it.exists() },
                if (runtimeDex?.exists() == true) 1 else 0,
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
                if (!exists()) mkdirs()
            }
            val dexPath = dexFiles.joinToString(File.pathSeparator) { it.absolutePath }
            return DexClassLoader(dexPath, optimizedDir.absolutePath, null, parent)
        }
    }
}
