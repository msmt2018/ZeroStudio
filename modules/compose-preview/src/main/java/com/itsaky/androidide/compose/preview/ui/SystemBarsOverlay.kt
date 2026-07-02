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

package com.itsaky.androidide.compose.preview.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 系统状态栏 + 导航栏叠加层 v3.4.
 *
 * 模拟 Android / iOS 系统栏:
 * - 状态栏 (顶部): 时钟 (HH:mm:ss) + 信号 + Wi-Fi + 电池 (含百分比) + 通知红点
 * - 导航栏 (底部): 返回 / Home / 最近 (传统三键) 或手势横杠
 *
 * v3.4 增:
 * - 时钟刷新频率从 10s 改为 1s — 真实手机秒级跳变, 用户更易感知"在动"
 * - 时钟格式从 `HH:mm` 改为 `HH:mm:ss` (秒级精度)
 * - 电池显示百分比文字 (e.g. "85%") 而不仅是填充条
 * - 电池低电阈值改为 20% (之前 15%) — 更符合 Android Material 指南
 *
 * 主题:
 * - [SystemBarsTheme.LIGHT]    : 深色图标 / 浅色背景
 * - [SystemBarsTheme.DARK]     : 浅色图标 / 深色背景
 * - [SystemBarsTheme.TRANSLUCENT_LIGHT] : 半透明 + 浅色图标
 * - [SystemBarsTheme.TRANSLUCENT_DARK]  : 半透明 + 深色图标
 * - [SystemBarsTheme.AUTO]     : 跟随 Composable 主题
 *
 * 高度按 [DeviceProfile.statusBarHeightDp] / [DeviceProfile.navigationBarHeightDp].
 *
 * @param profile 设备 profile
 * @param systemBarsTheme 主题
 * @param showStatusBar 是否显示状态栏
 * @param showNavigationBar 是否显示导航栏
 * @param useGestureNav 是否使用手势导航 (vs 传统三键)
 * @param clockProvider 时钟来源 (默认取系统时区当前时间)
 * @param batteryPercent 电量百分比 (0..100, null = 不显示)
 * @param notificationDot 是否显示通知小红点
 */
/**
 * 仅渲染状态栏的 composable. v4 拆分: 让 [com.itsaky.androidide.compose.preview.ui.PhoneOrFoldableFrame]
 * 能把状态栏 / 导航栏作为 content 的 sibling 放在 Column 里, 配合 `weight(1f)`
 * 让 preview 内容自然限定在两块系统栏之间的区域, 修复 "compose UI 越过虚拟状态栏
 * 显示在设备外面" 的 bug. 旧 [SystemBarsOverlay] 用 `fillMaxSize` + 透明
 * `Spacer.weight(1f)` 假装分隔, 实际上 preview 内容仍占满整个屏幕, 状态栏只是
 * 覆盖在上面, 用户能透过状态栏看到 preview 内容.
 */
@Composable
fun StatusBar(
    profile: DeviceProfile,
    systemBarsTheme: SystemBarsTheme = SystemBarsTheme.AUTO,
    clockProvider: () -> Date = { Date() },
    batteryPercent: Int? = 85,
    notificationDot: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = resolveThemeColors(systemBarsTheme)
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = clockProvider()
            delay(1_000)
        }
    }
    val timeText = remember(now) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
    }
    val isTranslucent = systemBarsTheme.isTranslucent()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(profile.statusBarHeightDp.dp)
    ) {
        StatusBarContent(
            time = timeText,
            foreground = fg,
            background = bg,
            isTranslucent = isTranslucent,
            batteryPercent = batteryPercent,
            notificationDot = notificationDot,
        )
    }
}

/**
 * 仅渲染导航栏的 composable. 配套 [StatusBar] 使用, 见 [StatusBar] 的注释.
 */
@Composable
fun NavigationBar(
    profile: DeviceProfile,
    systemBarsTheme: SystemBarsTheme = SystemBarsTheme.AUTO,
    useGestureNav: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = resolveThemeColors(systemBarsTheme)
    val isTranslucent = systemBarsTheme.isTranslucent()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(profile.navigationBarHeightDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        NavigationBarContent(
            foreground = fg,
            background = bg,
            isTranslucent = isTranslucent,
            useGestureNav = useGestureNav,
        )
    }
}

/**
 * 统一解析主题色: [SystemBarsTheme.AUTO] 跟随 Composable 当前背景明度.
 */
@Composable
private fun resolveThemeColors(systemBarsTheme: SystemBarsTheme): Pair<Color, Color> {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resolved = when (systemBarsTheme) {
        SystemBarsTheme.AUTO -> if (isDarkTheme) SystemBarsTheme.DARK else SystemBarsTheme.LIGHT
        else -> systemBarsTheme
    }
    return resolved.colors()
}

@Composable
fun SystemBarsOverlay(
    profile: DeviceProfile,
    systemBarsTheme: SystemBarsTheme = SystemBarsTheme.AUTO,
    showStatusBar: Boolean = true,
    showNavigationBar: Boolean = true,
    useGestureNav: Boolean = false,
    clockProvider: () -> Date = { Date() },
    batteryPercent: Int? = 85,
    notificationDot: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (showStatusBar && profile.statusBarHeightDp > 0) {
            StatusBar(
                profile = profile,
                systemBarsTheme = systemBarsTheme,
                clockProvider = clockProvider,
                batteryPercent = batteryPercent,
                notificationDot = notificationDot,
            )
        } else {
            Spacer(Modifier.height(0.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        if (showNavigationBar && profile.navigationBarHeightDp > 0) {
            NavigationBar(
                profile = profile,
                systemBarsTheme = systemBarsTheme,
                useGestureNav = useGestureNav,
            )
        }
    }
}

@Composable
private fun StatusBarContent(
    time: String,
    foreground: Color,
    background: Color,
    isTranslucent: Boolean,
    batteryPercent: Int?,
    notificationDot: Boolean,
) {
    val bg = if (isTranslucent) Color.Transparent else background
    val density = LocalDensity.current
    val cellH = with(density) { 16.dp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // 背景
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isTranslucent) {
                drawRect(color = background)
            } else {
                drawRect(
                    color = background.copy(alpha = 0.6f)
                )
            }
        }

        // 左侧: 时钟 (秒级 HH:mm:ss)
        Text(
            text = time,
            color = foreground,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp),
        )

        // 右侧: 信号 / Wi-Fi / 电池 (含百分比文字) / 通知红点
        // v3.4: 用 Row + 子 composable 替代单一 Canvas, 这样能用 Text 画 "85%".
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 信号 (4 格)
            SignalCluster(foreground = foreground, cellH = cellH)
            // Wi-Fi
            WifiCluster(foreground = foreground, cellH = cellH)
            // 电池 — 含 "85%" 文字
            BatteryCluster(
                foreground = foreground,
                cellH = cellH,
                batteryPercent = batteryPercent,
            )
            // 通知红点
            if (notificationDot) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = Color(0xFFE53935))
                }
            }
        }
    }
}

/**
 * v3.4: 拆分右侧集群为可独立测试的小组件. 之前 v2.1 用单个 Canvas + drawRightCluster
 * 绘制所有内容, 没法嵌入文字 (电池百分比).
 *
 * 每个 cluster 接收 cellH (状态栏图标区域的高度) 来自适应不同 status bar 高度.
 */
@Composable
private fun SignalCluster(foreground: Color, cellH: Float) {
    val cellW = cellH * 0.5f
    Canvas(modifier = Modifier.size((cellW * 4 + 1.5f * 3).toDpCompat())) {
        val signalBarW = cellW * 0.8f
        for (i in 0 until 4) {
            val h = cellH * (0.3f + i * 0.2f)
            val x = size.width - (4 - i) * (signalBarW + 1.5f) - signalBarW
            val y = size.height / 2f + cellH * 0.3f - h
            drawRoundRect(
                color = foreground,
                topLeft = Offset(x, y),
                size = Size(signalBarW, h),
                cornerRadius = CornerRadius(1f, 1f),
            )
        }
    }
}

@Composable
private fun WifiCluster(foreground: Color, cellH: Float) {
    val cellW = cellH * 0.5f
    Canvas(modifier = Modifier.size((cellW * 1.4f).toDpCompat())) {
        for (i in 0 until 3) {
            val r = (i + 1) * cellH * 0.18f
            drawArc(
                color = foreground.copy(alpha = (i + 1) * 0.3f),
                startAngle = 220f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(size.width / 2f - r, size.height - r - cellH * 0.1f),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 1.4f),
            )
        }
        drawCircle(
            color = foreground,
            radius = cellH * 0.1f,
            center = Offset(size.width / 2f, size.height - cellH * 0.1f),
        )
    }
}

@Composable
private fun BatteryCluster(
    foreground: Color,
    cellH: Float,
    batteryPercent: Int?,
) {
    val cellW = cellH * 0.5f
    val batteryWidthPx = cellW * 2.4f
    val batteryHeightPx = cellH * 0.55f
    val percentText = batteryPercent?.let { "$it%" }.orEmpty()
    val isLow = (batteryPercent ?: 100) < 20
    val fillColor = if (isLow) Color(0xFFE53935) else foreground

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (percentText.isNotEmpty()) {
            Text(
                text = percentText,
                color = fillColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Canvas(
            modifier = Modifier
                .width(batteryWidthPx.toDpCompat())
                .height(batteryHeightPx.toDpCompat()),
        ) {
            val w = size.width
            val h = size.height
            // 外框
            drawRoundRect(
                color = foreground,
                topLeft = Offset(0f, 0f),
                size = Size(w * 0.85f, h),
                cornerRadius = CornerRadius(2f, 2f),
                style = Stroke(width = 1f),
            )
            // 正极
            drawRoundRect(
                color = foreground,
                topLeft = Offset(w * 0.85f + 1f, h * 0.32f),
                size = Size(w * 0.08f, h * 0.36f),
                cornerRadius = CornerRadius(0.5f, 0.5f),
            )
            // 填充
            if (batteryPercent != null) {
                val pct = batteryPercent.coerceIn(0, 100) / 100f
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(1.5f, 1.5f),
                    size = Size((w * 0.85f - 3f) * pct, h - 3f),
                    cornerRadius = CornerRadius(1f, 1f),
                )
            }
        }
    }
}

/**
 * 工具: Float 转 Dp.
 *
 * 注意: 一定要在 Density 上下文内调 (用 `with(LocalDensity.current) { ... }`).
 * `androidx.compose.ui.unit.Density` 自带 `Float.toDp(): Dp` 扩展, 我们这里
 * 透传到那个, 不自己定义同名扩展避免遮蔽/递归.
 */
@Composable
private fun Float.toDpCompat(): Dp = with(LocalDensity.current) { this@toDpCompat.toDp() }

@Composable
private fun NavigationBarContent(
    foreground: Color,
    background: Color,
    isTranslucent: Boolean,
    useGestureNav: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isTranslucent) {
                drawRect(color = background.copy(alpha = 0.6f))
            } else {
                drawRect(color = background)
            }
        }
        if (useGestureNav) {
            // 手势导航: 中央一条 108dp × 4dp 横杠
            val density = LocalDensity.current
            val barW = with(density) { 108f.dp.toPx() }
            val barH = with(density) { 4f.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
            ) {
                val x = (size.width - barW) / 2f
                val y = (size.height - barH) / 2f
                drawRoundRect(
                    color = foreground,
                    topLeft = Offset(x, y),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barH / 2f, barH / 2f),
                )
            }
        } else {
            // 传统三键: 返回 / Home / 最近
            Row3Buttons(foreground = foreground)
        }
    }
}

@Composable
private fun Row3Buttons(foreground: Color) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp)
    ) {
        val centerY = maxHeight / 2
        // 三个三角形 / 圆形 / 方形
        // 返回 (左) - 三角形
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterStart)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val s = size.minDimension * 0.18f
            val path = Path().apply {
                moveTo(cx + s, cy - s)
                lineTo(cx - s, cy)
                lineTo(cx + s, cy + s)
                close()
            }
            drawPath(path, foreground, style = Stroke(width = 1.6f))
        }
        // Home (中) - 圆形
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
        ) {
            val r = size.minDimension * 0.18f
            drawCircle(
                color = foreground,
                radius = r,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = 1.6f)
            )
        }
        // 最近 (右) - 矩形
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
        ) {
            val s = size.minDimension * 0.22f
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawRoundRect(
                color = foreground,
                topLeft = Offset(cx - s, cy - s * 0.7f),
                size = Size(s * 2f, s * 1.4f),
                cornerRadius = CornerRadius(2f, 2f),
                style = Stroke(width = 1.6f)
            )
        }
    }
}

/**
 * 状态栏 / 导航栏主题.
 */
enum class SystemBarsTheme {
    AUTO,
    LIGHT,
    DARK,
    TRANSLUCENT_LIGHT,
    TRANSLUCENT_DARK;

    fun isTranslucent(): Boolean = this == TRANSLUCENT_LIGHT || this == TRANSLUCENT_DARK

    fun colors(): Pair<Color, Color> = when (this) {
        AUTO, LIGHT -> Color(0xFFFAFAFA) to Color(0xFF202024)
        DARK -> Color(0xFF202024) to Color(0xFFFAFAFA)
        TRANSLUCENT_LIGHT -> Color(0x00FAFAFA) to Color(0xFF202024)
        TRANSLUCENT_DARK -> Color(0x00202024) to Color(0xFFFAFAFA)
    }
}

/** Color luminance 计算 (0.0 ~ 1.0). */
private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
