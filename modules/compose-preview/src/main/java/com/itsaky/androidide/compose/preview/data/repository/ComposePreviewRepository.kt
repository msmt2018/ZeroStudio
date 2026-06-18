package com.itsaky.androidide.compose.preview.data.repository

import android.content.Context
import com.itsaky.androidide.compose.preview.data.source.ProjectContext
import com.itsaky.androidide.compose.preview.domain.model.ParsedPreviewSource
import java.io.File

/**
 * Compose 预览仓库接口 v3.1.
 *
 * v3.1 简化:
 * - 删 `InitializationResult.Ready.runtimeDex` (compose runtime 走 PathClassLoader 父委托)
 * - 删 `CompilationResult.runtimeDex` (同上)
 * - 删 `computeSourceHash()` (不再有 DexCache 进程内 dex 缓存, dex 全部来自 gradle)
 * - compilePreview **唯一走 gradle-dex 模式**: 不再 K2 进程内编译, 不再 D8 dex
 */
interface ComposePreviewRepository {

    /**
     * 解析 [filePath] 所在 module / variant / dex 文件, 准备 gradle build.
     *
     * @return [InitializationResult.Ready] 解析成功, 可调 [compilePreview];
     *         [InitializationResult.NeedsBuild] 需要先构建项目;
     *         [InitializationResult.Failed] 不可恢复错误.
     */
    suspend fun initialize(context: Context, filePath: String): Result<InitializationResult>

    /**
     * 触发 gradle 构建 + 加载项目 dex. v3.1 唯一实现: 通过 [com.itsaky.androidide.projects.builder.BuildService]
     * 跑 `assemble<Variant>`, 拿 gradle 产物的 dex, 返回 [CompilationResult].
     */
    suspend fun compilePreview(
        source: String,
        parsedSource: ParsedPreviewSource
    ): Result<CompilationResult>

    /** 释放所有引用. ViewModel.onCleared() 调用. */
    fun reset()
}

sealed class InitializationResult {

    /** 解析成功, 可继续调用 [ComposePreviewRepository.compilePreview]. */
    data class Ready(
        val projectContext: ProjectContext
    ) : InitializationResult()

    /** 需要先跑 gradle build 才能加载 dex. */
    data class NeedsBuild(
        val modulePath: String,
        val variantName: String
    ) : InitializationResult()

    /** 不可恢复错误 (例如 ProjectContextSource 解析失败). */
    data class Failed(val message: String) : InitializationResult()
}

/**
 * 编译/构建结果. v3.1 不再含 `runtimeDex` 字段:
 * compose runtime 类通过 [android.content.Context.getClassLoader] (PathClassLoader) 解析.
 */
data class CompilationResult(
    val dexFile: File,
    val className: String,
    val projectDexFiles: List<File>
)

class CompilationException(
    message: String,
    val diagnostics: List<com.itsaky.androidide.compose.preview.compiler.CompileDiagnostic> = emptyList()
) : Exception(message)
