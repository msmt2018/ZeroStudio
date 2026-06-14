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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp.toPx
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 系统状态栏 + 导航栏叠加层 v2.1.
 *
 * 模拟 Android / iOS 系统栏:
 * - 状态栏 (顶部): 时钟 + 信号 + Wi-Fi + 电池 + 通知红点
 * - 导航栏 (底部): 返回 / Home / 最近 (传统三键) 或手势横杠
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
    val density = LocalDensity.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resolvedTheme = when (systemBarsTheme) {
        SystemBarsTheme.AUTO -> if (isDarkTheme) SystemBarsTheme.DARK else SystemBarsTheme.LIGHT
        else -> systemBarsTheme
    }
    val (bg, fg) = resolvedTheme.colors()

    // 时钟 10s 刷新
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = clockProvider()
            delay(10_000)
        }
    }
    val timeText = remember(now) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 状态栏
        if (showStatusBar && profile.statusBarHeightDp > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(profile.statusBarHeightDp.dp)
            ) {
                StatusBarContent(
                    time = timeText,
                    foreground = fg,
                    background = bg,
                    isTranslucent = resolvedTheme.isTranslucent(),
                    batteryPercent = batteryPercent,
                    notificationDot = notificationDot,
                )
            }
        } else {
            Spacer(Modifier.height(0.dp))
        }

        // 中间留空 (由外部 content 占位)
        Spacer(modifier = Modifier.weight(1f))

        // 导航栏
        if (showNavigationBar && profile.navigationBarHeightDp > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(profile.navigationBarHeightDp.dp),
                contentAlignment = Alignment.Center
            ) {
                NavigationBarContent(
                    foreground = fg,
                    background = bg,
                    isTranslucent = resolvedTheme.isTranslucent(),
                    useGestureNav = useGestureNav,
                )
            }
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        val density = LocalDensity.current
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isTranslucent) {
                drawRect(color = background)
            } else {
                drawRect(
                    color = background.copy(alpha = 0.6f)
                )
            }
        }

        // 左侧: 时钟
        Text(
            text = time,
            color = foreground,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
        )

        // 右侧: 信号 / Wi-Fi / 电池 / 通知
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxSize()
                .padding(end = 4.dp)
        ) {
            drawRightCluster(
                foreground = foreground,
                batteryPercent = batteryPercent,
                notificationDot = notificationDot,
            )
        }
    }
}

private fun DrawScope.drawRightCluster(
    foreground: Color,
    batteryPercent: Int?,
    notificationDot: Boolean,
) {
    val right = size.width
    val centerY = size.height / 2f
    val cellH = size.height * 0.5f
    val cellW = cellH

    // 通知小红点 (在最右)
    if (notificationDot) {
        val dotR = cellH * 0.18f
        drawCircle(
            color = Color(0xFFE53935),
            radius = dotR,
            center = Offset(right - dotR, centerY)
        )
        val consumed = dotR * 2f + 4f
        // 不影响其他绘制, 仅占空间
        @Suppress("UNUSED_VARIABLE")
        val _x = consumed
    }

    // 电池 (最右第二个)
    val batteryWidth = cellW * 1.2f
    val batteryHeight = cellH * 0.55f
    val batteryLeft = right - batteryWidth - 4f
    val batteryTop = centerY - batteryHeight / 2f
    // 电池外框
    drawRoundRect(
        color = foreground,
        topLeft = Offset(batteryLeft, batteryTop),
        size = Size(batteryWidth * 0.85f, batteryHeight),
        cornerRadius = CornerRadius(2f, 2f),
        style = Stroke(width = 1f)
    )
    // 电池正极
    drawRoundRect(
        color = foreground,
        topLeft = Offset(batteryLeft + batteryWidth * 0.85f + 1f, centerY - batteryHeight * 0.18f),
        size = Size(batteryWidth * 0.10f, batteryHeight * 0.36f),
        cornerRadius = CornerRadius(0.5f, 0.5f),
    )
    // 电池填充
    if (batteryPercent != null) {
        val percent = batteryPercent.coerceIn(0, 100) / 100f
        val fillColor = if (batteryPercent < 15) Color(0xFFE53935) else foreground
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(batteryLeft + 1.5f, batteryTop + 1.5f),
            size = Size((batteryWidth * 0.85f - 3f) * percent, batteryHeight - 3f),
            cornerRadius = CornerRadius(1f, 1f),
        )
    }

    // Wi-Fi
    val wifiRight = batteryLeft - 6f
    val wifiCenterX = wifiRight - cellW * 0.4f
    val wifiCenterY = centerY
    // 简化为 3 个同心弧
    for (i in 0 until 3) {
        val r = (i + 1) * cellH * 0.18f
        drawArc(
            color = foreground.copy(alpha = (i + 1) * 0.3f),
            startAngle = 220f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(wifiCenterX - r, wifiCenterY - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = 1.4f)
        )
    }
    drawCircle(
        color = foreground,
        radius = cellH * 0.1f,
        center = Offset(wifiCenterX, wifiCenterY + cellH * 0.05f)
    )

    // 信号
    val signalRight = wifiCenterX - cellW * 0.4f - 4f
    val signalBarW = cellW * 0.16f
    val signalBarH = cellH * 0.6f
    for (i in 0 until 4) {
        val h = signalBarH * (0.3f + i * 0.2f)
        val x = signalRight - (4 - i) * (signalBarW + 1.5f) - signalBarW
        val y = centerY + signalBarH / 2f - h
        drawRoundRect(
            color = foreground,
            topLeft = Offset(x, y),
            size = Size(signalBarW, h),
            cornerRadius = CornerRadius(1f, 1f),
        )
    }
}

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
