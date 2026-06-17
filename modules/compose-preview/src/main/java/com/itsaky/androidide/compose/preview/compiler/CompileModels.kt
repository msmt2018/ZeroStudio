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
 * 编译 / 构建阶段产物的统一数据模型.
 *
 * 之前 K2 + D8 进程内编译时这里有 [CompileResult] / [DexResult] 两个数据类,
 * 现在 K2 + D8 整套已经移除, 完全改用 BuildService.executeTasks 跑 gradle
 * assemble 任务, 编译 / dex 产物直接来自项目的 build cache. 保留
 * [CompileDiagnostic] 是为了 [com.itsaky.androidide.compose.preview.data.repository.CompilationException]
 * 的诊断信息仍能透传给 UI 层, 不破坏现有 preview UI 的错误展示.
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
