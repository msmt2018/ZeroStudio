package com.itsaky.androidide.ui.theme.deviceconnection

import androidx.compose.ui.graphics.Color

/**
 * 设备连接页专属配色。
 *
 * 设计基调：深色科技感 + 毛玻璃层次。所有颜色独立于 connection 模块的 SeedColors，
 * 仅用于 [DeviceConnectionTheme] 包裹的 Compose 组件。
 */
data class DeviceConnectionColors(
    // 背景层
    val background: Color,
    val surfacePanel: Color,
    val surfaceHighlight: Color,
    val border: Color,

    // 主色 / 强调
    val primary: Color,
    val primaryGradientStart: Color,
    val primaryGradientEnd: Color,

    // 状态色
    val statusGreen: Color,
    val statusYellow: Color,
    val statusRed: Color,

    // 通道色（用于卡片左侧色条 / 图标）
    val channelShizuku: Color,
    val channelRoot: Color,
    val channelOtg: Color,
    val channelWifiAdb: Color,

    // 文字
    val textPrimary: Color,
    val textSecondary: Color,

    // 控制台
    val consoleBackground: Color,
    val consoleText: Color,
    val consoleCommand: Color,
    val consolePath: Color,
    val consoleError: Color,
)

internal val DeviceConnectionDarkColors = DeviceConnectionColors(
    background = Color(0xFF0D1117),
    surfacePanel = Color(0xFF161B22),
    surfaceHighlight = Color(0xFF1F2630),
    border = Color(0xFF30363D),

    primary = Color(0xFF3B82F6),
    primaryGradientStart = Color(0xFF3B82F6),
    primaryGradientEnd = Color(0xFF8B5CF6),

    statusGreen = Color(0xFF22C55E),
    statusYellow = Color(0xFFEAB308),
    statusRed = Color(0xFFEF4444),

    channelShizuku = Color(0xFF3B82F6),
    channelRoot = Color(0xFF8B5CF6),
    channelOtg = Color(0xFF06B6D4),
    channelWifiAdb = Color(0xFF10B981),

    textPrimary = Color(0xFFF0F6FC),
    textSecondary = Color(0xFF8B949E),

    consoleBackground = Color(0xFF0D1117),
    consoleText = Color(0xFFD1D5DB),
    consoleCommand = Color(0xFF22C55E),
    consolePath = Color(0xFF60A5FA),
    consoleError = Color(0xFFEF4444),
)