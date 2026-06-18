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

package com.itsaky.androidide.compose.preview.data.source

/**
 * @Composable 函数信息 v3.3.
 *
 * 从 .kt 源码中抽取, 给调试模式 toolbar 下拉用. 包含:
 * - 函数名 (供 PreviewRenderEngine 反射调用)
 * - 完整签名 (含参数类型, 给用户看)
 * - 行号 (1-based, 给"跳转到源码"用, 本次不联动 IDE)
 * - 参数列表 (类型注解, 给下拉显示用)
 * - 是否有 @Preview 标注 (有则优先, 用 @Preview 风格的展示)
 */
data class ComposableFunctionInfo(
    val name: String,
    val signature: String,
    val line: Int,
    val parameterTypes: List<String>,
    val hasPreviewAnnotation: Boolean,
)
