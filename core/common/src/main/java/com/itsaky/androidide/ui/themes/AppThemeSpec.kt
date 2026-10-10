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

package com.itsaky.androidide.ui.themes

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// 扩展颜色规格声明（用于承载原 XML 特有的终端、涟漪、选择等非 M3 标准属性）
// ============================================================================
data class ExtendedColors(
    val ripple: Color,                  // 控件触控反馈的涟漪波纹颜色 (对应 controlHighlight)
    val selection: Color,               // 文本或代码选中的背景高亮色 (对应 textColorHighlight)
    val terminalTextColor: Color,       // 纯文本控制台/终端的默认字符输出颜色
    val terminalBackgroundColor: Color  // 纯文本控制台/终端的完整背景底色
)

// 创建 Local 上下文以便在 Compose 树中向下传递扩展属性
val LocalExtendedColors: ProvidableCompositionLocal<ExtendedColors> = staticCompositionLocalOf {
    error("No ExtendedColors provided! Make sure to wrap your content in AndroidIDETheme {}")
}

// ============================================================================
// 统一主题包数据载体规格
// ============================================================================
data class AppThemeSpec(
    val id: String,
    val name: String,
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
    val lightExtended: ExtendedColors,
    val darkExtended: ExtendedColors
)