package com.itsaky.androidide.compose.preview.data.repository

import android.content.Context
import com.itsaky.androidide.compose.preview.compiler.CompileDiagnostic
import com.itsaky.androidide.compose.preview.data.source.ProjectContext
import com.itsaky.androidide.compose.preview.domain.model.ParsedPreviewSource
import java.io.File

/**
 * Compose Preview 仓库契约.
 *
 * 之前默认走进程内 K2JVMCompiler + D8 (依赖 assets 中的 jar 压缩包), 现在完全
 * 改用现有 [com.itsaky.androidide.projects.builder.BuildService], 通过
 * `executeTasks` 跑 gradle assemble 任务让 dex 刷新. dex 来源是项目 build
 * cache (`projectContext.projectDexFiles`), [Composablerenderer][com.itsaky.androidide.compose.preview.runtime.ComposableRenderer]
 * 通过 [com.itsaky.androidide.compose.preview.runtime.ComposeClassLoader] 加载.
 *
 * 因此 [CompilationResult] 删除了之前的 `runtimeDex: File?` 字段, 改为只透传
 * `projectDexFiles`, 避免上层对运行时 dex 路径做无意义处理.
 */
interface ComposePreviewRepository {

    suspend fun initialize(context: Context, filePath: String): Result<InitializationResult>

    suspend fun compilePreview(
        source: String,
        parsedSource: ParsedPreviewSource
    ): Result<CompilationResult>

    fun computeSourceHash(source: String): String

    fun reset()
}

sealed class InitializationResult {
    data class Ready(
        val projectContext: ProjectContext
    ) : InitializationResult()

    data class NeedsBuild(
        val modulePath: String,
        val variantName: String
    ) : InitializationResult()

    data class Failed(val message: String) : InitializationResult()
}

data class CompilationResult(
    val dexFile: File,
    val className: String,
    val projectDexFiles: List<File>
)

class CompilationException(
    message: String,
    val diagnostics: List<CompileDiagnostic> = emptyList()
) : Exception(message)
