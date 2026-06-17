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
import com.itsaky.androidide.compose.preview.data.source.ProjectContext
import com.itsaky.androidide.compose.preview.data.source.ProjectContextSource
import com.itsaky.androidide.compose.preview.domain.model.ParsedPreviewSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 仓库实现 v3.
 *
 * v2 走进程内 K2JVMCompiler + D8 编译 (依赖 assets 中的 jar 压缩包), 在用户机器上
 * 经常因为 jar 缺失/版本不匹配/IO 权限/gradle dex 已存在但 IDE 端不知道 等原因导致
 * 编译失败, 进而表现成 Compose Preview Activity 内的花屏 (渲染了异常堆栈) 和
 * "build 按钮状态反复切换" 的体验问题.
 *
 * v3 把 K2 + D8 + assets jar 全部移除, 完全改用现有
 * [com.itsaky.androidide.projects.builder.BuildService], 跟
 * [com.itsaky.androidide.actions.build.QuickRunWithCancellationAction] / [com.itsaky.androidide.actions.build.RunTasksAction]
 * 一致: 用户点击构建按钮时由 [com.itsaky.androidide.compose.preview.ComposePreviewActivity.triggerBuild]
 * 调 `BuildService.executeTasks(assemble$variant)`, gradle 服务端会直接走自己的
 * 构建缓存 + Android Build Cache, dex 写到项目 build 目录. dex 的来源是
 * [ProjectContext.projectDexFiles] (由 [ProjectContextSource] 从
 * [com.itsaky.androidide.projects.android.AndroidModule.getRuntimeDexFiles] 获取).
 *
 * 因此:
 * 1. 不再需要 assets 中的 jar (kotlin-compiler-embeddable / d8 / compose-*).
 * 2. 不再需要进程内 K2 编译器, 不需要 class 转 dex 的 D8 步骤.
 * 3. dex 加载使用 [com.itsaky.androidide.compose.preview.runtime.ComposeClassLoader],
 *    跟 v2 的 gradle-dex 分支相同.
 * 4. [initialize] 不再 bootstrap K2 编译环境, 只检查 `intermediateClasspaths` 判断
 *    是否需要先 build. [compilePreview] 不再触发 gradle assemble, 只取最近一次
 *    build 产物的 dex (build 由 Activity 端的 [triggerBuild] 触发). 这样 dex 路径
 *    唯一可控, 避免双触发争抢.
 */
class ComposePreviewRepositoryImpl(
    private val projectContextSource: ProjectContextSource = ProjectContextSource()
) : ComposePreviewRepository {

    private val LOG = LoggerFactory.getLogger(ComposePreviewRepositoryImpl::class.java)

    private var projectContext: ProjectContext? = null
    private var openedFilePath: String? = null

    override suspend fun initialize(
        context: Context,
        filePath: String
    ): Result<InitializationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val ctx = projectContextSource.resolveContext(filePath)
            projectContext = ctx
            openedFilePath = filePath

            if (ctx.needsBuild && ctx.modulePath != null) {
                LOG.warn(
                    "No intermediate classes found for {} - build required before preview can be loaded",
                    ctx.modulePath,
                )
                return@runCatching InitializationResult.NeedsBuild(ctx.modulePath, ctx.variantName)
            }

            if (ctx.projectDexFiles.isEmpty()) {
                LOG.warn("No project DEX files resolved for {}", filePath)
                return@runCatching InitializationResult.Failed(
                    "No project DEX files found. Run a Gradle build first to produce the dex artifacts."
                )
            }

            LOG.info(
                "Repository ready: module={}, variant={}, projectDexFiles={}",
                ctx.modulePath,
                ctx.variantName,
                ctx.projectDexFiles.size,
            )
            InitializationResult.Ready(ctx)
        }
    }

    private fun requireProjectContext(): ProjectContext =
        projectContext
            ?: error("Repository not initialized. Call initialize() first.")

    override suspend fun compilePreview(
        source: String,
        parsedSource: ParsedPreviewSource
    ): Result<CompilationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val context = requireProjectContext()
            val fileName = parsedSource.className?.removeSuffix("Kt") ?: "Preview"
            val generatedClassName = "${fileName}Kt"
            val fullClassName = "${parsedSource.packageName}.$generatedClassName"

            val dexFiles = context.projectDexFiles.filter { it.exists() }
            if (dexFiles.isEmpty()) {
                throw CompilationException(
                    "No project DEX files available. Run a Gradle build first to produce the dex artifacts."
                )
            }

            // dex 加载是 ComposeClassLoader 的职责, 这里只透传 dex 列表. UI 层会把
            // 第一个 dex 当作 target dex 透传给 ComposableRenderer, ComposeClassLoader
            // 会用全部 dex 列表构造 isolated classloader 来解析 target class.
            val targetDex = dexFiles.first()

            LOG.info(
                "Preview compiled via gradle-dex: {} (target dex: {}, {} project dex files)",
                fullClassName,
                targetDex.absolutePath,
                dexFiles.size,
            )

            CompilationResult(
                dexFile = targetDex,
                className = fullClassName,
                projectDexFiles = dexFiles,
            )
        }
    }

    override fun computeSourceHash(source: String): String {
        // source hash 之前是给 DexCache 用的, 现在没有进程内 dex 缓存, 保留接口以
        // 兼容上层调用方, 返回一个稳定的 hash 让上层做 UI 缓存键 (例如避免在用户
        // 编辑器内光标变化时重渲染).
        return java.util.UUID.nameUUIDFromBytes(source.toByteArray(Charsets.UTF_8)).toString()
    }

    override fun reset() {
        projectContext = null
        openedFilePath = null
    }
}
