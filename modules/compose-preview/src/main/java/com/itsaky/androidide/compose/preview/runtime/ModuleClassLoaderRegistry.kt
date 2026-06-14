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
import com.itsaky.androidide.compose.preview.data.source.ModuleInfo
import dalvik.system.DexClassLoader
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * v2.3 P0 Multi-module: 跨 module ClassLoader 链管理.
 *
 * ## 模型
 *
 * 为每个 module 创建一个 [DexClassLoader], parent 指向其依赖 module 的 loader (JVM 委托链).
 * class 查找时, 从主 module 的 loader 开始 → parent → ... → system classloader.
 *
 * ```
 *  app-loader      → feature-foo-loader  → core-bar-loader  → system classloader
 *  (主 module)         (1 跳依赖)             (1 跳依赖)
 * ```
 *
 * ## 单 module 兼容
 *
 * 1 个 module 时, [mainLoader] == 该 module 的 loader, 行为与 [ComposeClassLoader] 一致.
 *
 * ## 线程安全
 *
 * - [loaders] / [moduleByPath]: ConcurrentHashMap
 * - [getOrCreate] 内部同步保护 (避免 race)
 * - [release]: 全清, 调用方需保证没有正在执行的 loadClass
 */
class ModuleClassLoaderRegistry(
    /**
     * 优化目录. DexClassLoader 需要一个可写目录存 OAT 文件.
     * 通常传入 `context.codeCacheDir` 或测试用的 [java.io.File.createTempFile] 子目录.
     */
    private val optimizedRoot: File,
    /** 当依赖 module 不可用时的 fallback parent classloader. */
    private val fallbackParent: ClassLoader = ClassLoader.getSystemClassLoader(),
) {

    private val LOG = LoggerFactory.getLogger(ModuleClassLoaderRegistry::class.java)

    /** gradlePath → DexClassLoader. */
    private val loaders = ConcurrentHashMap<String, DexClassLoader>()

    /** gradlePath → ModuleInfo. */
    private val moduleByPath = ConcurrentHashMap<String, ModuleInfo>()

    /** 主 module 的 gradlePath. */
    @Volatile
    private var mainModulePath: String? = null

    /** Compose runtime dex (来自 assets). */
    @Volatile
    private var runtimeDex: File? = null

    /** 缓存: gradlePath:ClassName → Class. */
    private val classCache = ConcurrentHashMap<String, Class<*>>()

    @Volatile
    var loadCount: Long = 0
        private set

    @Volatile
    var cacheHitCount: Long = 0
        private set

    val activeLoaderCount: Int get() = loaders.size
    val mainLoader: DexClassLoader? get() = mainModulePath?.let { loaders[it] }

    fun setRuntimeDex(runtimeDex: File?) {
        this.runtimeDex = runtimeDex
        if (runtimeDex != null) {
            // 路径变化 → 全部 loader 失效 (parent chain 变, 缓存可能错)
            invalidateAll()
        }
    }

    /**
     * 装载 module 拓扑. 主 module 在 [modules] 列表的第 0 项.
     *
     * 多次调用会: 1) 释放旧拓扑 2) 重建新拓扑.
     */
    fun install(modules: List<ModuleInfo>) {
        require(modules.isNotEmpty()) { "modules must be non-empty" }
        release()

        // 注册 module info
        modules.forEach { m -> moduleByPath[m.gradlePath] = m }

        // 按 BFS 顺序创建 loader (主 module 先, 依赖后), parent = 依赖 module 的 loader
        val main = modules.first()
        mainModulePath = main.gradlePath

        // 第一遍: 收集所有 module, 找到每个 module 的 1 跳依赖中**已经创建过 loader** 的那个
        for (m in modules) {
            if (m.gradlePath in loaders) continue // 已创建 (例如被别的 module 作为 parent 提前创建)
            val parent = findFirstCreatedParent(m)
            val loader = createLoader(m, parent)
            loaders[m.gradlePath] = loader
        }

        LOG.info("Installed {} module loaders (main={})", loaders.size, main.gradlePath)
    }

    /**
     * 跨 module 加载 class. 从主 module loader 开始按 parent chain 查找.
     *
     * 找到后, 缓存 (key = mainModulePath:ClassName) 用于后续命中.
     */
    fun loadClass(className: String): Class<*>? {
        loadCount++
        val cacheKey = "${mainModulePath ?: "?"}:$className"
        classCache[cacheKey]?.let {
            cacheHitCount++
            return it
        }
        val loader = mainLoader ?: return null
        return try {
            val clazz = loader.loadClass(className)
            if (clazz != null) classCache[cacheKey] = clazz
            clazz
        } catch (e: ClassNotFoundException) {
            LOG.warn("Class not found in module chain: {}", className)
            null
        } catch (e: Throwable) {
            LOG.error("loadClass failed: {}", className, e)
            null
        }
    }

    /**
     * 按 gradlePath 直接从指定 module 的 loader 加载 (不走主 module 入口).
     * 主要给测试和 LiveLiterals 跨 module 反射用.
     */
    fun loadClassFromModule(gradlePath: String, className: String): Class<*>? {
        loadCount++
        val cacheKey = "$gradlePath:$className"
        classCache[cacheKey]?.let {
            cacheHitCount++
            return it
        }
        val loader = loaders[gradlePath] ?: return null
        return try {
            val clazz = loader.loadClass(className)
            if (clazz != null) classCache[cacheKey] = clazz
            clazz
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: Throwable) {
            LOG.error("loadClassFromModule failed: {} in {}", className, gradlePath, e)
            null
        }
    }

    fun invalidateAll() {
        classCache.clear()
        // DexClassLoader 没有 close, 强引用置 null 等待 GC
        loaders.clear()
    }

    fun release() {
        invalidateAll()
        moduleByPath.clear()
        mainModulePath = null
        if (optimizedRoot.exists()) {
            optimizedRoot.deleteRecursively()
        }
        LOG.info("ModuleClassLoaderRegistry released")
    }

    /**
     * 拿到 [m] 的直接依赖中, **当前已存在于 [loaders]** 的那个 (按 BFS 顺序, 第一个命中).
     * 若都没有, fallback 到 [fallbackParent].
     */
    private fun findFirstCreatedParent(m: ModuleInfo): ClassLoader? {
        for (depPath in m.directDependencies) {
            val depLoader = loaders[depPath]
            if (depLoader != null) return depLoader
        }
        return fallbackParent
    }

    private fun createLoader(module: ModuleInfo, parent: ClassLoader?): DexClassLoader {
        val dexFiles = buildList {
            addAll(module.dexFiles.filter { it.exists() })
            runtimeDex?.takeIf { it.exists() }?.let { add(it) }
        }
        val dexPath = dexFiles.joinToString(File.pathSeparator) { it.absolutePath }
        optimizedRoot.apply {
            if (!exists()) mkdirs()
        }
        return DexClassLoader(
            dexPath,
            optimizedRoot.absolutePath,
            null,
            parent ?: fallbackParent,
        )
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ModuleClassLoaderRegistry::class.java)
    }
}
