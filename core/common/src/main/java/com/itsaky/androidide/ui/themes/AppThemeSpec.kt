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

// ============================================================================
// 扩展颜色定义 (用于承载 XML 中特有的终端、涟漪、选择等非标准 M3 颜色)
// ============================================================================
data class ExtendedColors(
    val ripple: Color,
    val selection: Color,
    val terminalTextColor: Color,
    val terminalBackgroundColor: Color
)

// 创建 Local 上下文以便在 Compose 树中向下传递扩展属性
val LocalExtendedColors = staticCompositionLocalOf<ExtendedColors> {
    error("No ExtendedColors provided! Make sure to wrap your content in AndroidIDETheme {}")
}

// ============================================================================
// 主题包规格定义 (数据载体)
// ============================================================================
data class AppThemeSpec(
    val id: String,
    val name: String,
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
    val lightExtended: ExtendedColors,
    val darkExtended: ExtendedColors
)
