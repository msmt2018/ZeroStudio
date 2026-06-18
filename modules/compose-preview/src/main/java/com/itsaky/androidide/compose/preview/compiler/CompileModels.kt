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

/**
 * 编译 / dex 阶段产物的诊断信息.
 *
 * v3.1 简化: 不再有 [CompileResult] / [DexResult] 数据类 (v2.1 进程内 K2 + D8 链路
 * 删除后已无调用方), 仅保留 [CompileDiagnostic] 用于错误信息展示. gradle build
 * 自身的错误由 [com.itsaky.androidide.projects.builder.BuildService] 处理.
 */
data class CompileDiagnostic(
    val severity: Severity,
    val message: String,
    val file: String?,
    val line: Int?,
    val column: Int?
) {
    enum class Severity { ERROR, WARNING, INFO }
}
