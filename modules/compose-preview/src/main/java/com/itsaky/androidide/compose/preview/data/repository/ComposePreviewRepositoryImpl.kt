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
import com.itsaky.androidide.compose.preview.compiler.PreviewDexHashStore
import com.itsaky.androidide.compose.preview.data.source.ProjectContext
import com.itsaky.androidide.compose.preview.data.source.ProjectContextSource
import com.itsaky.androidide.compose.preview.domain.model.ParsedPreviewSource
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.utils.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Compose 预览仓库 v3.1 实现.
 *
 * ## v3.1 vs v2.1
 *
 * v2.1 进程内编译链 (`AssetsComposeBundles` + `BundledComposeCompiler` + `BundledD8Dexer` +
 * `DexCache`) 已被完全移除. 这些工具要求把 `kotlin-compiler-embeddable` / `r8` /
 * `compose-compiler-plugin` / `compose-runtime` / `compose-ui` 等 jar 打包到
 * `assets/compose/compose-jars.zip`, 启动时解压到 `cacheDir/compose-sdk/` 再用, 链路脆弱:
 * 任何 jar 缺失或解压失败都会导致 "Compose runtime jars missing" 这种错误.
 *
 * v3.1 唯一路径: dex 直接来自 gradle (`BuildService.executeTasks(assemble<Variant>)` 产物),
 * 通过 `ProjectContextSource.resolveContext` 找. compose runtime / ui / material3 等
 * 类通过 IDE module 自身 compile dependency + IDE 主 APK 的 PathClassLoader 解析.
 *
 * 因此本类不再持有 `bundles / compiler / dexer / dexCache` 任何字段, 也不再维护
 * `runtimeDex` 字段.
 */
class ComposePreviewRepositoryImpl(
    private val projectContextSource: ProjectContextSource = ProjectContextSource(),
) : ComposePreviewRepository {

    private val LOG = LoggerFactory.getLogger(ComposePreviewRepositoryImpl::class.java)

    private var projectContext: ProjectContext? = null
    private var openedFilePath: String? = null

    /**
     * dex / 源码哈希缓存. v4 引入, 避免每次进入 preview 都重跑 gradle assemble.
     * 故意在 IDE home 目录下, 跟 `build/` 完全分离, gradle clean 不会清.
     */
    private val hashStore: PreviewDexHashStore by lazy {
        val root = Environment.ANDROIDIDE_HOME ?: File(".androidide")
        PreviewDexHashStore(root)
    }

    override suspend fun initialize(
        context: Context,
        filePath: String
    ): Result<InitializationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = projectContextSource.resolveContext(filePath)
            projectContext = ctx
            openedFilePath = filePath

            // 【关键修复】build 成功后停在 NeedsBuild / Build Project 按钮页
            //
            // 优先看 dex 实际状态: 只要 dex 文件存在, 就直接走
            //   dex 加载路径, 跳过 needsBuild 强制判定.
            if (ctx.modulePath != null) {
                val existingDex = ctx.projectDexFiles.filter { it.exists() }
                if (existingDex.isNotEmpty()) {
                    LOG.info(
                        "Repository ready (via project DEX): module={}, variant={}, " +
                            "projectDexFiles={}/{}, needsBuild={}",
                        ctx.modulePath, ctx.variantName,
                        existingDex.size, ctx.projectDexFiles.size, ctx.needsBuild,
                    )
                } else if (ctx.needsBuild) {
                    LOG.warn(
                        "No dex files for {} - build required before preview can be loaded",
                        ctx.modulePath,
                    )
                    return@runCatching InitializationResult.NeedsBuild(
                        ctx.modulePath, ctx.variantName,
                    )
                } else {
                    // needsBuild=false 但 dex 也没有 — 异常状态
                    LOG.warn("No project DEX files resolved for {}", filePath)
                    return@runCatching InitializationResult.Failed(
                        "No project DEX files found. Run a Gradle build first " +
                            "to produce the dex artifacts.",
                    )
                }
            }

            InitializationResult.Ready(ctx)
        }
    }

    override suspend fun compilePreview(
        source: String,
        parsedSource: ParsedPreviewSource
    ): Result<CompilationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val context = projectContext ?: throw IllegalStateException(
                "Repository not initialized. Call initialize() first."
            )

            val fileName = parsedSource.className?.removeSuffix("Kt") ?: "Preview"
            val generatedClassName = "${fileName}Kt"
            val fullClassName = "${parsedSource.packageName}.$generatedClassName"

            // === v4 dex 哈希短路 ===
            // 用户 compose preview SDK 的 .kt 源 + 已有 dex 完全没变 → 直接跳过
            // gradle assemble, 用现有 dex 渲染. 避免 "用户改了一行代码 / 啥都没改
            // → gradle 重跑 30s+" 的浪费. androidx compose SDK 等运行时依赖不进
            // 哈希范围, 那些走 IDE PathClassLoader, 与用户代码无关.
            val cacheKey = cacheKeyFor(context)
            val userSourceFiles = collectUserSourceFiles(context)
            val projectDexFiles = context.projectDexFiles.filter { it.exists() }
            if (projectDexFiles.isNotEmpty() &&
                hashStore.isUnchanged(cacheKey, userSourceFiles, projectDexFiles)
            ) {
                LOG.info(
                    "dex hash cache hit for {} — skipping gradle assemble",
                    cacheKey,
                )
                val previewDex = projectDexFiles.first()
                return@runCatching CompilationResult(
                    dexFile = previewDex,
                    className = fullClassName,
                    projectDexFiles = projectDexFiles,
                )
            }
            LOG.info("dex hash cache miss for {} — running gradle assemble", cacheKey)

            // v3.1: 直接跑 gradle 拿 dex. 不再 K2 + D8 进程内编译.
            runGradleAssemble(context)

            // 重新解析 (gradle 跑完可能新增 dex)
            val refreshedContext = openedFilePath
                ?.let { projectContextSource.resolveContext(it) }
                ?: context
            projectContext = refreshedContext

            val dexFiles = refreshedContext.projectDexFiles.filter { it.exists() }
            if (dexFiles.isEmpty()) {
                throw CompilationException(
                    message = "No project DEX files found after Gradle build. " +
                        "Please run an assemble task first."
                )
            }

            // previewDex: 取用户 Composable 所在的 dex. mergeProjectDex* 之类合并 dex
            // 通常排在前面, 但用户代码也可能被 d8 切到 project_dex_archive. 用第一个
            // 存在的 dex 作为预览入口, 其它 dex 仍通过 projectDexFiles 一并加载.
            val previewDex = dexFiles.first()

            // 把当前 source + dex 哈希存盘, 供下次进入 preview 比对. 必须在
            // dexFiles 拿到之后才存 — 万一 gradle 跑完没产出 dex, 不写脏数据.
            val newSourceFiles = collectUserSourceFiles(refreshedContext)
            hashStore.store(cacheKey, newSourceFiles, dexFiles)
            LOG.info("Stored dex hash for {} ({} sources, {} dex files)",
                cacheKey, newSourceFiles.size, dexFiles.size)

            LOG.info(
                "Preview ready: {} ({} previews, {} project DEX files)",
                fullClassName, parsedSource.previewConfigs.size, dexFiles.size,
            )

            CompilationResult(
                dexFile = previewDex,
                className = fullClassName,
                projectDexFiles = dexFiles,
            )
        }
    }

    /**
     * 派生缓存 key: modulePath + variant. 例如 `:app-debug`.
     *
     * 故意用这两个字段拼, 不同 variant 的 dex 不能复用 (debug / release 产物不同).
     */
    private fun cacheKeyFor(context: ProjectContext): String {
        val module = context.modulePath?.removePrefix(":")?.replace(":", "/") ?: "root"
        return "$module-${context.variantName}"
    }

    /**
     * 收集用户 compose preview SDK 的 .kt 源文件. 走 [ProjectContext.modulePath]
     * 下的 `src/`, 不递归到 `build/` (那是编译产物, 由 dex 哈希单独覆盖).
     *
     * 故意只哈希用户模块, 不动 androidx 等运行时 SDK — 那部分走 IDE 主 APK 的
     * PathClassLoader, 跟用户代码完全无关, 进 build 缓存里. 即便 androidx 升级,
     * 也只影响 IDE 主 APK, 不会让这个 hash miss.
     */
    private fun collectUserSourceFiles(context: ProjectContext): List<File> {
        val modulePath = context.modulePath ?: return emptyList()
        val moduleDir = File(modulePath)
        if (!moduleDir.isDirectory) return emptyList()
        val srcDir = File(moduleDir, "src")
        if (!srcDir.isDirectory) return emptyList()
        return srcDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .toList()
    }

    /**
     * 跑 gradle `assemble<Variant>` 拿 dex 产物. v3.1 唯一构建路径.
     *
     * - 模块: `${modulePath}:assemble<Variant>`
     * - 根项目: `assemble<Variant>`
     *
     * 如果 BuildService 已经在跑, 直接报错让上层提示"build 已在进行中".
     */
    private fun runGradleAssemble(context: ProjectContext) {
        val buildService = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
            ?: throw CompilationException("BuildService is unavailable.")

        if (buildService.isBuildInProgress) {
            throw CompilationException(
                "Build is already in progress. Please wait for it to finish."
            )
        }

        val capitalizedVariant = context.variantName.replaceFirstChar { it.uppercaseChar() }
        val modulePath = context.modulePath?.takeIf { it.isNotBlank() }
        val task = if (modulePath != null) {
            "$modulePath:assemble$capitalizedVariant"
        } else {
            "assemble$capitalizedVariant"
        }

        LOG.info("Running gradle task for preview: {}", task)
        val result = runCatching {
            buildService.executeTasks(task).get(15, TimeUnit.MINUTES)
        }.getOrElse { e ->
            throw CompilationException("Gradle task '$task' failed: ${e.message}")
        }

        if (!result.isSuccessful) {
            throw CompilationException(
                "Gradle task '$task' did not complete successfully. " +
                    "Check build output for details."
            )
        }
    }

    override fun reset() {
        projectContext = null
        openedFilePath = null
        LOG.debug("Repository reset")
    }
}
