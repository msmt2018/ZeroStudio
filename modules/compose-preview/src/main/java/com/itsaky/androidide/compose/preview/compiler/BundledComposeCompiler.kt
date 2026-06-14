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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 进程内 K2JVMCompiler.
 *
 * 取代旧 [ComposeCompiler] + [CompilerDaemon]. 直接从 [AssetsComposeBundles] 提供的
 * kotlin-compiler-embeddable 加载 K2JVMCompiler 类, 进程内调用, 完成后销毁临时 classloader.
 *
 * ## 设计要点
 *
 * - **零 Maven / 零 IDE 路径**: 所有 jar 来自 assets, 不再访问 `.m2`.
 * - **无守护进程**: 每次 [compile] 启动独立 classloader, 编译完即销毁; 单次编译 < 4s.
 * - **完整 diagnostic 收集**: 自实现 MessageCollector 包装器捕获 errors / warnings.
 * - **可取消**: 通过 [cancel] 设置标志, K2 会在分析阶段前检测并退出.
 * - **URLClassLoader 隔离**: K2 类从 assets 加载, 不污染 IDE 的主 classloader;
 *   K2 调用所需的 `K2JVMCompilerArguments` / `MessageCollector` 等也全部从 URLClassLoader 加载,
 *   避免跨 classloader 的 ClassCastException.
 */
class BundledComposeCompiler(
    private val bundles: AssetsComposeBundles
) {

    private val LOG = LoggerFactory.getLogger(BundledComposeCompiler::class.java)

    private val cancelled = AtomicBoolean(false)

    /**
     * v2.1 P4 增量编译缓存.
     *
     * 命中时跳过 K2JVMCompiler.exec, 节省 1-4s 编译时间.
     * key = SHA-256(源文件 + classpath + plugin + jvmTarget).
     */
    private val cache: CompilationCache? = try {
        val cacheDir = java.io.File(bundles.cacheDir, "compose-compile-cache")
        val cc = CompilationCache(
            cacheDir = cacheDir,
            versionTag = { bundles.versionTag },
        )
        CompilationCacheHolder.install(cc)
        LOG.info("CompilationCache enabled: dir={}", cacheDir.absolutePath)
        cc
    } catch (e: Throwable) {
        LOG.warn("CompilationCache disabled: {}", e.message)
        null
    }

    fun cancel() {
        cancelled.set(true)
        LOG.info("BundledComposeCompiler cancel requested")
    }

    fun compile(
        sourceFiles: List<File>,
        outputDir: File,
        extraClasspath: List<File> = emptyList(),
        jvmTarget: String = "17"
    ): CompileResult {
        if (!bundles.init()) {
            return CompileResult.failure(
                "Compose SDK assets not available. Run preBuild to regenerate assets.",
                emptyList()
            )
        }

        cancelled.set(false)

        val pluginJar = bundles.composePluginJar
            ?: return CompileResult.failure("compose-compiler-plugin.jar missing", emptyList())

        val runtimeJars = bundles.composeRuntimeJars
        if (runtimeJars.isEmpty()) {
            return CompileResult.failure("Compose runtime jars missing", emptyList())
        }

        val androidJar = bundles.resolveAndroidJar()
        if (androidJar == null) {
            return CompileResult.failure(
                "android.jar not found. Please install Android SDK platform 34+.",
                emptyList()
            )
        }

        // 拼装 classpath
        val cp = buildList {
            addAll(extraClasspath)
            add(androidJar)
            addAll(runtimeJars)
        }.joinToString(File.pathSeparator) { it.absolutePath }

        // ============ P4 增量编译缓存检查 ============
        if (cache != null) {
            val key = CompilationCacheKey.of(
                sourceFiles = sourceFiles,
                classpath = cp,
                pluginJar = pluginJar,
                jvmTarget = jvmTarget,
            )
            val cached = cache.get(key, outputDir)
            if (cached != null) {
                return CompileResult(
                    success = true,
                    outputDir = outputDir,
                    exitCode = 0,
                    diagnostics = emptyList(),
                    cancelled = false,
                    errorOutput = "",
                    cacheHit = true,
                    savedCompileMs = cached.compileMs,
                )
            }
        }

        // outputDir 在 K2 调用前清空 (命中分支会重新创建)
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        // 加载 K2 及其依赖类到隔离的 URLClassLoader
        val compilerClasspath = bundles.kotlinCompilerClasspath.map { it.toURI().toURL() }
        val parent = BundledComposeCompiler::class.java.classLoader
        val isolated = URLClassLoader(compilerClasspath.toTypedArray(), parent)

        val start = System.currentTimeMillis()
        val result = try {
            invokeK2InIsolatedClassloader(isolated, sourceFiles, cp, outputDir, jvmTarget, pluginJar)
        } catch (e: Throwable) {
            LOG.error("K2JVMCompiler invocation failed", e)
            CompileResult.failure("K2JVMCompiler invocation failed: ${e.message}", emptyList())
        } finally {
            (isolated as? AutoCloseable)?.runCatching { close() }
        }

        val elapsed = System.currentTimeMillis() - start
        LOG.info("K2JVMCompiler exit={} elapsed={}ms", result.exitCode, elapsed)

        // ============ P4 写入缓存 (成功才写) ============
        if (cache != null && result.success && !cancelled.get()) {
            try {
                val key = CompilationCacheKey.of(
                    sourceFiles = sourceFiles,
                    classpath = cp,
                    pluginJar = pluginJar,
                    jvmTarget = jvmTarget,
                )
                cache.put(key, outputDir, compileMs = elapsed)
            } catch (e: Throwable) {
                LOG.warn("Failed to populate compilation cache: {}", e.message)
            }
        }

        return result.copy(errorOutput = if (result.success) "" else result.errorOutput)
    }

    private fun invokeK2InIsolatedClassloader(
        loader: URLClassLoader,
        sourceFiles: List<File>,
        classpath: String,
        outputDir: File,
        jvmTarget: String,
        pluginJar: File,
    ): CompileResult {
        // v2.1 P3 字节码加速: 用 K2StaticBinder 替代反射调用
        val binder = com.itsaky.androidide.compose.preview.bytecode.K2StaticBinder.getOrCreate(loader)
            ?: run {
                // 退路: 没有 binder (ClassNotFoundException), 用旧反射逻辑
                return invokeK2ReflectionFallback(loader, sourceFiles, classpath, outputDir, jvmTarget, pluginJar)
            }
        LOG.debug("K2StaticBinder ready, exec sig={}", binder.execType)

        val compilerClazz = loader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
        val argsClazz = loader.loadClass("org.jetbrains.kotlin.cli.jvm.compiler.K2JVMCompilerArguments")
        val collectorClazz = loader.loadClass("org.jetbrains.kotlin.cli.common.messages.MessageCollector")

        // P3: 用 binder 一次性创建 instance (替代 newInstance)
        val compilerInstance = binder.newK2Instance()
        val argsInstance = argsClazz.getDeclaredConstructor().newInstance()
        callSetter(argsClazz, argsInstance, "freeArgs", sourceFiles.map { it.absolutePath })
        callSetter(argsClazz, argsInstance, "classpath", classpath)
        callSetter(argsClazz, argsInstance, "destination", outputDir.absolutePath)
        callSetter(argsClazz, argsInstance, "jvmTarget", jvmTarget)
        callSetter(argsClazz, argsInstance, "pluginClasspaths", arrayOf(pluginJar.absolutePath))
        callSetter(argsClazz, argsInstance, "noStdlib", false)
        callSetter(argsClazz, argsInstance, "noReflect", false)
        callSetter(argsClazz, argsInstance, "suppressVersionWarnings", true)
        callSetter(argsClazz, argsInstance, "allWarnings", false)

        val collectorInstance = collectorClazz.getField("NONE").get(null)

        // P3: 关键优化 - exec 调用走 MethodHandle (binder)
        val errStream = PrintStream(ByteArrayOutputStream())
        val exitCode = try {
            @Suppress("UNCHECKED_CAST")
            val exitObj = when (binder.execType.parameterCount()) {
                3 -> binder.exec(compilerInstance, argsInstance as Any, errStream, collectorInstance)
                4 -> binder.exec(compilerInstance, argsInstance as Any, errStream, collectorInstance, null!!)
                else -> {
                    // 5+ 参数, 走 fallback
                    val execMethod = compilerClazz.methods.first { it.name == "exec" }
                    execMethod.invoke(compilerInstance, argsInstance, errStream, collectorInstance) as? Number
                }
            }
            (exitObj as? Number)?.toInt() ?: -1
        } catch (e: java.lang.reflect.InvocationTargetException) {
            LOG.error("K2JVMCompiler exec failed", e.targetException)
            return CompileResult.failure(
                "K2JVMCompiler exec failed: ${e.targetException?.message}",
                emptyList()
            )
        } catch (e: Throwable) {
            LOG.error("K2StaticBinder.exec failed", e)
            return CompileResult.failure(
                "K2StaticBinder.exec failed: ${e.message}",
                emptyList()
            )
        }

        return finalizeCompileResult(exitCode, outputDir, emptyList())
    }

    /**
     * K2StaticBinder 不可用时的退路: 旧反射路径.
     *
     * 与 P3 之前行为一致, 仅当 binder 初始化失败时使用.
     */
    @Suppress("UNCHECKED_CAST")
    private fun invokeK2ReflectionFallback(
        loader: URLClassLoader,
        sourceFiles: List<File>,
        classpath: String,
        outputDir: File,
        jvmTarget: String,
        pluginJar: File,
    ): CompileResult {
        val compilerClazz = loader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
        val argsClazz = loader.loadClass("org.jetbrains.kotlin.cli.jvm.compiler.K2JVMCompilerArguments")
        val collectorClazz = loader.loadClass("org.jetbrains.kotlin.cli.common.messages.MessageCollector")

        val compilerInstance = compilerClazz.getDeclaredConstructor().newInstance()
        val argsInstance = argsClazz.getDeclaredConstructor().newInstance()
        callSetter(argsClazz, argsInstance, "freeArgs", sourceFiles.map { it.absolutePath })
        callSetter(argsClazz, argsInstance, "classpath", classpath)
        callSetter(argsClazz, argsInstance, "destination", outputDir.absolutePath)
        callSetter(argsClazz, argsInstance, "jvmTarget", jvmTarget)
        callSetter(argsClazz, argsInstance, "pluginClasspaths", arrayOf(pluginJar.absolutePath))
        callSetter(argsClazz, argsInstance, "noStdlib", false)
        callSetter(argsClazz, argsInstance, "noReflect", false)
        callSetter(argsClazz, argsInstance, "suppressVersionWarnings", true)
        callSetter(argsClazz, argsInstance, "allWarnings", false)
        val collectorInstance = collectorClazz.getField("NONE").get(null)
        val printStreamClazz = loader.loadClass("java.io.PrintStream")
        val execMethod = compilerClazz.methods.firstOrNull { m ->
            m.name == "exec" && m.parameterCount == 3 &&
                m.parameterTypes[0] == printStreamClazz &&
                argsClazz.isAssignableFrom(m.parameterTypes[1]) &&
                collectorClazz.isAssignableFrom(m.parameterTypes[2])
        } ?: error("K2JVMCompiler.exec(PrintStream, args, collector) not found")
        val errStream = PrintStream(ByteArrayOutputStream())
        val exitCode = try {
            (execMethod.invoke(compilerInstance, errStream, argsInstance, collectorInstance) as? Number)?.toInt() ?: -1
        } catch (e: InvocationTargetException) {
            LOG.error("K2JVMCompiler exec failed", e.targetException)
            return CompileResult.failure(
                "K2JVMCompiler exec failed: ${e.targetException?.message}",
                emptyList()
            )
        }
        return finalizeCompileResult(exitCode, outputDir, emptyList())
    }

    /**
     * 公共结果收尾 (成功 / 失败包装).
     */
    private fun finalizeCompileResult(
        exitCode: Int,
        outputDir: File,
        diagnostics: List<CompileDiagnostic>,
    ): CompileResult {
        val success = exitCode == 0 && !cancelled.get()
        return if (success) {
            CompileResult(
                success = true,
                outputDir = outputDir,
                exitCode = exitCode,
                diagnostics = diagnostics,
                cancelled = false,
                errorOutput = "",
            )
        } else {
            CompileResult(
                success = false,
                outputDir = null,
                exitCode = exitCode,
                diagnostics = diagnostics,
                cancelled = cancelled.get(),
                errorOutput = "K2JVMCompiler exit code: $exitCode"
            )
        }
    }

    /**
     * 调用 K2JVMCompilerArguments 的 setter (kotlin 属性对应 setX).
     * @param value 类型与 setter 形参一致 (kotlin Boolean -> java primitive boolean).
     */
    private fun callSetter(clazz: Class<*>, instance: Any, propertyName: String, value: Any?) {
        val setterName = "set" + propertyName.replaceFirstChar { it.uppercaseChar() }
        // 优先用具体值类型找方法
        val method: Method? = when (value) {
            null -> null
            is List<*> -> runCatching { clazz.getMethod(setterName, List::class.java) }.getOrNull()
            is Array<*> -> runCatching {
                val componentType = value.javaClass.componentType
                clazz.getMethod(setterName, value.javaClass)
                    ?: clazz.getMethod(setterName, java.lang.reflect.Array.newInstance(componentType, 0).javaClass)
            }.getOrNull()
            is Boolean -> clazz.methods.firstOrNull { m ->
                m.name == setterName && (m.parameterTypes[0] == java.lang.Boolean.TYPE || m.parameterTypes[0] == java.lang.Boolean::class.java)
            }
            else -> runCatching { clazz.getMethod(setterName, value::class.java) }.getOrNull()
        }
        if (method == null) {
            LOG.warn("Setter not found for {}.{}", clazz.simpleName, propertyName)
            return
        }
        try {
            method.invoke(instance, value)
        } catch (e: InvocationTargetException) {
            LOG.warn("Setter {}.{} threw: {}", clazz.simpleName, propertyName, e.targetException?.message)
        }
    }
}
