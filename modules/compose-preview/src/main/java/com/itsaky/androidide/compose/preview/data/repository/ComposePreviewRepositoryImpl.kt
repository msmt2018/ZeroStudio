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

package com.itsaky.androidide.compose.preview.data.repository

import android.content.Context
import com.itsaky.androidide.compose.preview.compiler.AssetsComposeBundles
import com.itsaky.androidide.compose.preview.compiler.BundledComposeCompiler
import com.itsaky.androidide.compose.preview.compiler.BundledD8Dexer
import com.itsaky.androidide.compose.preview.compiler.CompileDiagnostic
import com.itsaky.androidide.compose.preview.compiler.DexCache
import com.itsaky.androidide.compose.preview.compiler.DexResult
import com.itsaky.androidide.compose.preview.data.source.ProjectContext
import com.itsaky.androidide.compose.preview.data.source.ProjectContextSource
import com.itsaky.androidide.compose.preview.domain.model.ParsedPreviewSource
import com.itsaky.androidide.compose.preview.runtime.LiveStatePersistenceManager
import com.itsaky.androidide.compose.preview.runtime.SourceChangeWatcher
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.builder.BuildService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 仓库实现 v2.
 *
 * 重写 [ComposePreviewRepositoryImpl], 用 [AssetsComposeBundles] + [BundledComposeCompiler] +
 * [BundledD8Dexer] 取代旧 `ComposeClasspathManager` + `ComposeCompiler` + `CompilerDaemon` + `ComposeDexCompiler`.
 *
 * ## 主要变化
 *
 * 1. **零外部依赖**: 编译/dex 全部走 assets 自带 jar; 不再读 `.m2` 或 IDE 私有路径.
 * 2. **无守护进程**: 每次 compile 独立起 isolated classloader, 编译完即关.
 * 3. **取消可响应**: [cancel] 会通过 [BundledComposeCompiler.cancel] 传播.
 * 4. **降级路径**: 进程内 D8 不可用时 (例如 R8 jar 缺失), 走 `useGradleDex` 让 gradle 来 dex.
 * 5. **缓存键含 SDK 版本**: SDK 升级时旧缓存自动失效.
 */
class ComposePreviewRepositoryImpl(
    private val projectContextSource: ProjectContextSource = ProjectContextSource()
) : ComposePreviewRepository {

    private val LOG = LoggerFactory.getLogger(ComposePreviewRepositoryImpl::class.java)

    private val useGradleDexTagRegex = Regex(
        """@compose-preview-use-gradle-dex\s*:\s*(true|false)""",
        RegexOption.IGNORE_CASE
    )

    private var bundles: AssetsComposeBundles? = null
    private var compiler: BundledComposeCompiler? = null
    private var dexer: BundledD8Dexer? = null
    private var dexCache: DexCache? = null
    private var workDir: File? = null

    private var runtimeDex: File? = null
    private var projectContext: ProjectContext? = null
    private var openedFilePath: String? = null
    private var cachedClasspath: List<File>? = null

    override suspend fun initialize(
        context: Context,
        filePath: String
    ): Result<InitializationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = projectContextSource.resolveContext(filePath)
            projectContext = ctx
            openedFilePath = filePath

            if (ctx.needsBuild && ctx.modulePath != null) {
                LOG.warn("No intermediate classes found - build required before initialization")
                return@runCatching InitializationResult.NeedsBuild(ctx.modulePath, ctx.variantName)
            }

            val assetBundles = initializeInfrastructure(context)
            if (!assetBundles.init()) {
                return@runCatching InitializationResult.Failed(
                    "Failed to initialize Compose SDK from assets. " +
                        "Ensure preBuild ran successfully."
                )
            }

            runtimeDex = assetBundles.composeRuntimeDex
            if (runtimeDex == null) {
                LOG.error("Compose runtime DEX missing in assets")
                return@runCatching InitializationResult.Failed(
                    "Compose runtime DEX missing. Re-run preBuild to regenerate assets."
                )
            }
            LOG.info("Repository initialized. runtimeDex={} sdkVer={}",
                runtimeDex?.absolutePath, assetBundles.versionTag)

            // v2.2 P4: 启动 LiveStatePersistenceManager, 加载磁盘快照
            installLiveStatePersistence(assetBundles, ctx)

            InitializationResult.Ready(runtimeDex, ctx)
        }
    }

    /**
     * v2.2 P4: 装入 LiveStatePersistenceManager, 加载磁盘持久化状态.
     *
     * - 项目目录 = `ctx.modulePath` 的 parent 链中找到含 .androidide 的目录, 或自身
     * - 启动 scheduler, 异步加载 .androidide/live-state.json
     */
    private fun installLiveStatePersistence(
        @Suppress("UNUSED_PARAMETER") bundles: AssetsComposeBundles,
        ctx: ProjectContext,
    ) {
        val projectPath = ctx.modulePath
        if (projectPath.isNullOrBlank()) {
            LOG.info("No project path, skipping LiveStatePersistence")
            return
        }
        val projectDir = File(projectPath)
        if (!projectDir.exists() || !projectDir.isDirectory) {
            LOG.info("Project path does not exist or is not a directory: {}", projectPath)
            return
        }
        val mgr = LiveStatePersistenceManager.install(projectDir)
        mgr.startScheduler()
        mgr.load()
    }

    private fun initializeInfrastructure(context: Context): AssetsComposeBundles {
        val cacheDir = context.cacheDir
        val work = File(cacheDir, "compose_preview_work").apply { mkdirs() }
        workDir = work

        val assetBundles = AssetsComposeBundles(context).also { bundles = it }
        dexCache = DexCache(File(cacheDir, "compose_dex_cache")) { assetBundles.versionTag }
            .also { DexCacheHolder.install(it) }
        compiler = BundledComposeCompiler(assetBundles)
        dexer = BundledD8Dexer(assetBundles)
        return assetBundles
    }

    private fun <T> requireInitialized(value: T?, name: String): T {
        return value ?: throw IllegalStateException("Repository not initialized: $name is null. Call initialize() first.")
    }

    private data class SourceCompileResult(
        val success: Boolean,
        val dexFile: File?,
        val error: String,
        val diagnostics: List<CompileDiagnostic> = emptyList()
    )

    override suspend fun compilePreview(
        source: String,
        parsedSource: ParsedPreviewSource
    ): Result<CompilationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cache = requireInitialized(dexCache, "dexCache")
            val compiler = requireInitialized(this@ComposePreviewRepositoryImpl.compiler, "compiler")
            val dexer = requireInitialized(this@ComposePreviewRepositoryImpl.dexer, "dexer")
            val workDir = requireInitialized(this@ComposePreviewRepositoryImpl.workDir, "workDir")
            val context = requireInitialized(projectContext, "projectContext")
            val useGradleDex = shouldUseGradleDex(source)

            val fileName = parsedSource.className?.removeSuffix("Kt") ?: "Preview"
            val generatedClassName = "${fileName}Kt"
            val fullClassName = "${parsedSource.packageName}.$generatedClassName"

            val sourceHash = cache.computeSourceHash(source)

            if (useGradleDex) {
                LOG.info("Using gradle-dex mode for {}", fullClassName)
                return@runCatching compileUsingGradleDexMode(fullClassName, context)
            }

            // 1) 缓存命中 (含 SDK version 校验)
            cache.getCachedDex(sourceHash)?.let { cached ->
                LOG.info("Cache hit for hash: {} (function={})", sourceHash, cached.functionName)
                return@runCatching CompilationResult(
                    dexFile = cached.dexFile,
                    className = cached.className,
                    runtimeDex = runtimeDex,
                    projectDexFiles = context.projectDexFiles
                )
            }

            // 2) 准备源文件
            val sourceDir = File(workDir, "src")
            val packageDir = File(sourceDir, parsedSource.packageName.replace('.', '/'))
            packageDir.mkdirs()
            val sourceFile = File(packageDir, "$fileName.kt")
            sourceFile.writeText(source)

            val classesDir = File(workDir, "classes").apply { mkdirs() }
            val dexDir = File(workDir, "dex").apply { mkdirs() }

            // 3) 编译
            val classpath = cachedClasspath ?: context.compileClasspaths.also { cachedClasspath = it }
            val compileResult = compiler.compile(
                sourceFiles = listOf(sourceFile),
                outputDir = classesDir,
                extraClasspath = classpath,
            )
            if (!compileResult.success) {
                LOG.error("Compilation failed: {}", compileResult.errorOutput)
                throw CompilationException(
                    message = compileResult.errorOutput.ifEmpty { "Compilation failed" },
                    diagnostics = compileResult.diagnostics
                )
            }

            // 4) Dex
            val dexStart = System.currentTimeMillis()
            val dexResult = dexer.dexToDex(classesDir, dexDir)
            val dexMs = System.currentTimeMillis() - dexStart
            if (!dexResult.success || dexResult.dexFile == null) {
                LOG.error("DEX compilation failed: {}", dexResult.errorMessage)
                throw CompilationException(
                    message = dexResult.errorMessage.ifEmpty { "DEX compilation failed" }
                )
            }

            // 5) 缓存
            try {
                cache.cacheDex(
                    sourceHash,
                    dexResult.dexFile,
                    fullClassName,
                    parsedSource.previewConfigs.firstOrNull()?.functionName ?: "",
                    dexMs = dexMs,
                )
            } catch (e: Exception) {
                LOG.warn("Failed to cache DEX (non-fatal): {}", e.message)
            }

            LOG.info("Preview ready: {} ({} previews, {} project DEX files)",
                fullClassName, parsedSource.previewConfigs.size, context.projectDexFiles.size)

            CompilationResult(
                dexFile = dexResult.dexFile,
                className = fullClassName,
                runtimeDex = runtimeDex,
                projectDexFiles = context.projectDexFiles
            )
        }
    }

    /**
     * v2.2 P3 Live Edit 重新编译.
     *
     * 与 [compilePreview] 行为差异:
     * - **跳过 DexCache**: 不读不写 — 每次都走完整 K2 + D8 路径
     * - **独立 output dir**: `workDir/recompile-classes` + `workDir/recompile-dex`, 不污染
     *   普通编译产物
     * - **CompilationCache 仍然命中**: 源码未变 + classpath 未变时 K2 走 cache, < 100ms 返回
     * - 失败抛 [CompilationException] (与 [compilePreview] 一致)
     */
    override suspend fun recompile(
        source: String,
        parsedSource: ParsedPreviewSource
    ): Result<CompilationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val compiler = requireInitialized(this@ComposePreviewRepositoryImpl.compiler, "compiler")
            val dexer = requireInitialized(this@ComposePreviewRepositoryImpl.dexer, "dexer")
            val workDir = requireInitialized(this@ComposePreviewRepositoryImpl.workDir, "workDir")
            val context = requireInitialized(projectContext, "projectContext")

            val fileName = parsedSource.className?.removeSuffix("Kt") ?: "Preview"
            val generatedClassName = "${fileName}Kt"
            val fullClassName = "${parsedSource.packageName}.$generatedClassName"

            // 1) 写源到独立目录 (不复用 src/, 避免和普通编译交叉污染)
            val sourceDir = File(workDir, "recompile-src")
            val packageDir = File(sourceDir, parsedSource.packageName.replace('.', '/'))
            packageDir.mkdirs()
            val sourceFile = File(packageDir, "$fileName.kt")
            sourceFile.writeText(source)

            // 2) 独立 output dirs
            val classesDir = File(workDir, "recompile-classes").apply {
                deleteRecursively()
                mkdirs()
            }
            val dexDir = File(workDir, "recompile-dex").apply { mkdirs() }

            // 3) 编译 (CompilationCache 仍命中, K2 跳过实际编译)
            val classpath = cachedClasspath ?: context.compileClasspaths.also { cachedClasspath = it }
            val compileResult = compiler.compile(
                sourceFiles = listOf(sourceFile),
                outputDir = classesDir,
                extraClasspath = classpath,
            )
            if (!compileResult.success) {
                LOG.error("Recompile failed: {}", compileResult.errorOutput)
                throw CompilationException(
                    message = compileResult.errorOutput.ifEmpty { "Recompile failed" },
                    diagnostics = compileResult.diagnostics
                )
            }

            // 4) Dex
            val dexResult = dexer.dexToDex(classesDir, dexDir)
            if (!dexResult.success || dexResult.dexFile == null) {
                LOG.error("Recompile DEX failed: {}", dexResult.errorMessage)
                throw CompilationException(
                    message = dexResult.errorMessage.ifEmpty { "Recompile DEX failed" }
                )
            }

            LOG.info("Hot-reload ready: {} ({} previews)", fullClassName, parsedSource.previewConfigs.size)

            CompilationResult(
                dexFile = dexResult.dexFile,
                className = fullClassName,
                runtimeDex = runtimeDex,
                projectDexFiles = context.projectDexFiles
            )
        }
    }

    fun cancel() {
        compiler?.cancel()
    }

    private fun shouldUseGradleDex(source: String): Boolean {
        val raw = useGradleDexTagRegex.find(source)?.groupValues?.get(1) ?: return false
        return raw.equals("true", ignoreCase = true)
    }

    private fun compileUsingGradleDexMode(
        fullClassName: String,
        context: ProjectContext
    ): CompilationResult {
        runGradleDexTasks(context)

        val refreshedContext = openedFilePath
            ?.let { projectContextSource.resolveContext(it) }
            ?: context
        projectContext = refreshedContext

        val dexFiles = refreshedContext.projectDexFiles.filter { it.exists() }
        if (dexFiles.isEmpty()) {
            throw CompilationException(
                message = "No project DEX files found after Gradle build. Please run an assemble task first."
            )
        }

        return CompilationResult(
            dexFile = dexFiles.first(),
            className = fullClassName,
            runtimeDex = runtimeDex,
            projectDexFiles = dexFiles
        )
    }

    private fun runGradleDexTasks(context: ProjectContext) {
        val buildService = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
            ?: throw CompilationException("BuildService is unavailable for gradle-dex mode.")
        if (buildService.isBuildInProgress) {
            throw CompilationException("Build is already in progress. Try gradle-dex preview again when build finishes.")
        }

        val capitalizedVariant = context.variantName.replaceFirstChar { it.uppercaseChar() }
        val modulePath = context.modulePath?.takeIf { it.isNotBlank() }
        val taskPrefix = modulePath?.let { "$it:" } ?: ""
        val candidateTasks = listOf(
            "${taskPrefix}mergeProjectDex$capitalizedVariant",
            "${taskPrefix}mergeDex$capitalizedVariant",
            "${taskPrefix}dexBuilder$capitalizedVariant",
            "${taskPrefix}assemble$capitalizedVariant"
        )

        val errors = mutableListOf<String>()
        for (task in candidateTasks) {
            try {
                LOG.info("Running Gradle tooling task for gradle-dex mode: {}", task)
                val result = buildService.executeTasks(task).get(15, TimeUnit.MINUTES)
                if (result.isSuccessful) {
                    LOG.info("Gradle task succeeded for gradle-dex mode: {}", task)
                    return
                }
                errors += "$task -> unsuccessful"
            } catch (e: Exception) {
                errors += "$task -> ${e.message}"
            }
        }

        throw CompilationException(
            "Failed to run dex-related Gradle tooling tasks for variant '$capitalizedVariant': ${errors.joinToString("; ")}"
        )
    }

    override fun computeSourceHash(source: String): String {
        val cache = dexCache
        if (cache == null) {
            LOG.warn("DexCache not initialized, using non-deterministic hash fallback")
            return source.hashCode().toString()
        }
        return cache.computeSourceHash(source)
    }

    /**
     * v2.2 P4: 32-bit FNV-1a hash. 与 [SourceChangeWatcher.fnv1aHash] 算法一致.
     */
    override fun computeSourceFnvHash(source: String): Int = SourceChangeWatcher.fnv1aHash(source)

    override fun reset() {
        compiler?.cancel()
        compiler = null
        dexer = null
        dexCache = null
        bundles = null
        cachedClasspath = null
        projectContext = null
        openedFilePath = null
        runtimeDex = null
        DexCacheHolder.reset()
        LOG.debug("Repository reset")
    }
}
