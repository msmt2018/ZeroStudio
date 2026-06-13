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

import java.io.File

/**
 * 编译 / dex 阶段产物的统一数据模型.
 *
 * 取代旧 [ComposeCompiler.CompilationResult] / [CompilerDaemon.CompilerResult] /
 * [CompilerDaemon.DexResult], 三个数据类合并为 [CompileResult] 和 [DexResult],
 * 字段命名一致 (errorMessage), UI 层 / Repository 层只需依赖这两个.
 */
data class CompileResult(
    val success: Boolean,
    val outputDir: File?,
    val exitCode: Int = 0,
    val diagnostics: List<CompileDiagnostic> = emptyList(),
    val cancelled: Boolean = false,
    val errorOutput: String = ""
) {
    companion object {
        fun failure(message: String, diagnostics: List<CompileDiagnostic> = emptyList()) =
            CompileResult(
                success = false,
                outputDir = null,
                diagnostics = diagnostics,
                errorOutput = message
            )
    }
}

data class CompileDiagnostic(
    val severity: Severity,
    val message: String,
    val file: String?,
    val line: Int?,
    val column: Int?
) {
    enum class Severity { ERROR, WARNING, INFO }
}

data class DexResult(
    val success: Boolean,
    val dexFile: File?,
    val errorMessage: String = ""
) {
    companion object {
        fun failure(message: String) = DexResult(success = false, dexFile = null, errorMessage = message)
    }
}
