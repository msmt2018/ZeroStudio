package com.itsaky.androidide.compose.preview.data.repository

import android.content.Context
import com.itsaky.androidide.compose.preview.compiler.CompileDiagnostic
import com.itsaky.androidide.compose.preview.data.source.ProjectContext
import com.itsaky.androidide.compose.preview.domain.model.ParsedPreviewSource
import java.io.File

interface ComposePreviewRepository {

    suspend fun initialize(context: Context, filePath: String): Result<InitializationResult>

    suspend fun compilePreview(
        source: String,
        parsedSource: ParsedPreviewSource
    ): Result<CompilationResult>

    /**
     * v2.2 P3 Live Edit: 重新编译.
     *
     * 与 [compilePreview] 行为一致, 但:
     * - 强制走完整 K2 + D8 路径 (即使 source 命中 DexCache 也重新 dex)
     *   → 编译缓存 (CompilationCache) 仍然命中
     * - 不修改 DexCache (新 dex 不写回)
     * - 用于 hot reload; 上层拿到 dex 后调用 [ComposeClassLoader.swapProjectDex]
     */
    suspend fun recompile(
        source: String,
        parsedSource: ParsedPreviewSource
    ): Result<CompilationResult>

    /**
     * v2.2 P4: 把当前 source 的 FNV-1a hash 计算出来.
     *
     * 用于 LiveLiterals 的 stale check — 持久化值与当前 source hash 不一致视为过期.
     */
    fun computeSourceFnvHash(source: String): Int

    fun computeSourceHash(source: String): String

    fun reset()
}

sealed class InitializationResult {
    data class Ready(
        val runtimeDex: File?,
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
    val runtimeDex: File?,
    val projectDexFiles: List<File>
)

class CompilationException(
    message: String,
    val diagnostics: List<CompileDiagnostic> = emptyList()
) : Exception(message)
