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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 扩展颜色定义 (用于承载 XML 中特有的终端、涟漪、选择等非标准 M3 颜色)
data class ExtendedColors(
    val ripple: Color,                  // 控件触控反馈涟漪颜色 (对应 controlHighlight)
    val selection: Color,               // 文本或代码选中高亮色 (对应 textColorHighlight)
    val terminalTextColor: Color,       // 纯文本控制台/终端默认字符输出颜色
    val terminalBackgroundColor: Color  // 纯文本控制台/终端背景底色
)

// 创建 Local 上下文以便在 Compose 树中向下传递扩展属性
val LocalExtendedColors = staticCompositionLocalOf<ExtendedColors> {
    error("No ExtendedColors provided! Make sure to wrap your content in AndroidIDETheme {}")
}

// 主题包数据规格
data class AppThemeSpec(
    val id: String,
    val name: String,
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
    val lightExtended: ExtendedColors,
    val darkExtended: ExtendedColors
)

// 内置多套 XML 风格转换过来的主题包仓库
object BuiltInThemes {

    // ========================================================================
    // 主题一：森林苔藓 (Forest Moss) - 护眼绿与自然大地色
    // ========================================================================
    val ForestMoss = AppThemeSpec(
        id = "forest_moss",
        name = "森林苔藓",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF4C662B),
            primaryContainer = Color(0xFFCDEDA3),
            inversePrimary = Color(0xFFB1D18A),
            onPrimary = Color(0xFFFFFFFF),
            onPrimaryContainer = Color(0xFF102000),
            secondary = Color(0xFF586249),
            secondaryContainer = Color(0xFFDCE7C8),
            onSecondary = Color(0xFFFFFFFF),
            onSecondaryContainer = Color(0xFF151E0B),
            tertiary = Color(0xFF386663),
            tertiaryContainer = Color(0xFFBCECE7),
            onTertiary = Color(0xFFFFFFFF),
            onTertiaryContainer = Color(0xFF00201E),
            error = Color(0xFFBA1A1A),
            background = Color(0xFFFDFCF5),
            onBackground = Color(0xFF1B1C18),
            surface = Color(0xFFFDFCF5),
            surfaceVariant = Color(0xFFE1E4D5),
            onSurface = Color(0xFF1B1C18),
            onSurfaceVariant = Color(0xFF44483D),
            inverseSurface = Color(0xFF30312C),
            inverseOnSurface = Color(0xFFF2F0E9),
            outline = Color(0xFF75796C)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFFB1D18A),
            primaryContainer = Color(0xFF354E14),
            inversePrimary = Color(0xFF4C662B),
            onPrimary = Color(0xFF233600),
            onPrimaryContainer = Color(0xFFCDEDA3),
            secondary = Color(0xFFC0CBAD),
            secondaryContainer = Color(0xFF404A33),
            onSecondary = Color(0xFF2A331E),
            onSecondaryContainer = Color(0xFFDCE7C8),
            tertiary = Color(0xFFA0D0CB),
            tertiaryContainer = Color(0xFF1F4E4B),
            onTertiary = Color(0xFF003735),
            onTertiaryContainer = Color(0xFFBCECE7),
            error = Color(0xFFFFB4AB),
            background = Color(0xFF1B1C18),
            onBackground = Color(0xFFE4E2DA),
            surface = Color(0xFF1B1C18),
            surfaceVariant = Color(0xFF44483D),
            onSurface = Color(0xFFE4E2DA),
            onSurfaceVariant = Color(0xFFC5C8BA),
            inverseSurface = Color(0xFFE4E2DA),
            inverseOnSurface = Color(0xFF1B1C18),
            outline = Color(0xFF8F9285)
        ),
        lightExtended = ExtendedColors(
            ripple = Color(0x334C662B),
            selection = Color(0x66CDEDA3),
            terminalTextColor = Color(0xFF1B1C18),
            terminalBackgroundColor = Color(0xFFFDFCF5)
        ),
        darkExtended = ExtendedColors(
            ripple = Color(0x44B1D18A),
            selection = Color(0x66354E14),
            terminalTextColor = Color(0xFFE4E2DA),
            terminalBackgroundColor = Color(0xFF1B1C18)
        )
    )

    // ========================================================================
    // 主题二：深海蔚蓝 (Deep Ocean)
    // ========================================================================
    val DeepOcean = AppThemeSpec(
        id = "deep_ocean",
        name = "深海蔚蓝",
        lightColorScheme = lightColorScheme(
            primary = Color(0xFF0061A4),
            primaryContainer = Color(0xFFD1E4FF),
            inversePrimary = Color(0xFF9ECAFF),
            onPrimary = Color(0xFFFFFFFF),
            onPrimaryContainer = Color(0xFF001D36),
            secondary = Color(0xFF535F70),
            secondaryContainer = Color(0xFFD7E3F7),
            onSecondary = Color(0xFFFFFFFF),
            onSecondaryContainer = Color(0xFF101C2B),
            tertiary = Color(0xFF6B5778),
            tertiaryContainer = Color(0xFFF2DAFF),
            onTertiary = Color(0xFFFFFFFF),
            onTertiaryContainer = Color(0xFF251432),
            error = Color(0xFFBA1A1A),
            background = Color(0xFFFDFCFF),
            onBackground = Color(0xFF1A1C1E),
            surface = Color(0xFFFDFCFF),
            surfaceVariant = Color(0xFFDFE2EB),
            onSurface = Color(0xFF1A1C1E),
            onSurfaceVariant = Color(0xFF43474E),
            inverseSurface = Color(0xFF2F3033),
            inverseOnSurface = Color(0xFFF1F0F4),
            outline = Color(0xFF73777F)
        ),
        darkColorScheme = darkColorScheme(
            primary = Color(0xFF9ECAFF),
            primaryContainer = Color(0xFF00487D),
            inversePrimary = Color(0xFF0061A4),
            onPrimary = Color(0xFF003258),
            onPrimaryContainer = Color(0xFFD1E4FF),
            secondary = Color(0xFFBBC7DB),
            secondaryContainer = Color(0xFF3C4758),
            onSecondary = Color(0xFF253140),
            onSecondaryContainer = Color(0xFFD7E3F7),
            tertiary = Color(0xFFD3E0F2),
            tertiaryContainer = Color(0xFF523F5F),
            onTertiary = Color(0xFF3B2948),
            onTertiaryContainer = Color(0xFFF2DAFF),
            error = Color(0xFFFFB4AB),
            background = Color(0xFF1A1C1E),
            onBackground = Color(0xFFE2E2E6),
            surface = Color(0xFF1A1C1E),
            surfaceVariant = Color(0xFF43474E),
            onSurface = Color(0xFFE2E2E6),
            onSurfaceVariant = Color(0xFFC3C7D0),
            inverseSurface = Color(0xFFE2E2E6),
            inverseOnSurface = Color(0xFF1A1C1E),
            outline = Color(0xFF8D9199)
        ),
        lightExtended = ExtendedColors(
            ripple = Color(0x330061A4),
            selection = Color(0x669ECAFF),
            terminalTextColor = Color(0xFF1A1C1E),
            terminalBackgroundColor = Color(0xFFFDFCFF)
        ),
        darkExtended = ExtendedColors(
            ripple = Color(0x449ECAFF),
            selection = Color(0x6600487D),
            terminalTextColor = Color(0xFFE2E2E6),
            terminalBackgroundColor = Color(0xFF1A1C1E)
        )
    )

    val allThemes = listOf(ForestMoss, DeepOcean)
}

// 全局动态控制器
enum class NightMode { 
    FOLLOW_SYSTEM, // 跟随系统
    FORCE_LIGHT,   // 强制浅色
    FORCE_DARK     // 强制深色
}

object DynamicThemeManager {
    var currentTheme by mutableStateOf(BuiltInThemes.ForestMoss)
    var nightMode by mutableStateOf(NightMode.FOLLOW_SYSTEM)
}

// 全局主题 Composable 入口
@Composable
fun AndroidIDETheme(content: @Composable () -> Unit) {
    val isDark = when (DynamicThemeManager.nightMode) {
        NightMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        NightMode.FORCE_LIGHT -> false
        NightMode.FORCE_DARK -> true
    }

    val themeSpec = DynamicThemeManager.currentTheme
    val colorScheme = if (isDark) themeSpec.darkColorScheme else themeSpec.lightColorScheme
    val extendedColors = if (isDark) themeSpec.darkExtended else themeSpec.lightExtended

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// 统一属性读取单例
object IDETheme {
    val colors: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val extended: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}