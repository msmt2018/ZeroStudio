package com.itsaky.androidide.ui.theme.deviceconnection

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalDeviceConnectionColors = staticCompositionLocalOf<DeviceConnectionColors> {
    error("DeviceConnectionColors not provided. Wrap content with DeviceConnectionTheme.")
}

/**
 * 设备连接页专属主题。
 *
 * - 强制深色科技感配色
 * - 通过 [LocalDeviceConnectionColors] 暴露 [DeviceConnectionColors]
 * - 同时覆盖 Material3 colorScheme，让卡片/按钮等 M3 组件也能拿到深色基调
 */
@Composable
fun DeviceConnectionTheme(content: @Composable () -> Unit) {
    val colors = DeviceConnectionDarkColors
    val materialColors = darkColorScheme(
        primary = colors.primary,
        onPrimary = colors.textPrimary,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surfacePanel,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceHighlight,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.border,
        error = colors.statusRed,
    )
    CompositionLocalProvider(LocalDeviceConnectionColors provides colors) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}

val deviceConnectionColors: DeviceConnectionColors
    @Composable
    @ReadOnlyComposable
    get() = LocalDeviceConnectionColors.current