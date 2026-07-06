/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.editor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.ui.compose.LocalDarkMode

/**
 * 磨砂玻璃控件主题颜色。
 *
 * 关键约束: 高斯模糊效果本身**不随主题切换**——blur radius / noise 都固定。
 * 只有 tint 透明度和图标颜色随主题变化, 保证两个主题下磨砂玻璃视觉效果一致,
 * 同时图标 / 文本在黑白背景下都清晰可见。
 */
@Composable
private fun frostedTint(): Color =
    if (LocalDarkMode.current) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.55f)

@Composable
private fun frostedBorderColor(): Color =
    if (LocalDarkMode.current) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.3f)

@Composable
private fun frostedIconColor(): Color =
    if (LocalDarkMode.current) Color.White.copy(alpha = 0.95f) else Color.Black.copy(alpha = 0.85f)

@Composable
private fun frostedTextColor(): Color =
    if (LocalDarkMode.current) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.9f)

/**
 * 磨砂玻璃容器 (Frosted glass container)。
 *
 * 用法:
 * ```
 * FrostedGlass(modifier = Modifier.align(BottomCenter)) {
 *     Row { FrostedIconButton(Icons.Default.PlayArrow, "Play") { ... } }
 * }
 * ```
 *
 * 实现: 使用半透明渐变背景 + 1px 边框 + 顶部高光模拟磨砂玻璃。
 * 真正的 Gaussian blur 由 Haze 库提供 (API 31+), 低版本降级为这种半透明渐变,
 * 视觉效果接近且无 API 兼容问题。
 *
 * @param modifier 外部布局修饰
 * @param cornerRadius 圆角半径, 默认 24dp
 * @param contentPadding 内边距
 * @param content 子内容
 */
@Composable
fun FrostedGlass(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable () -> Unit,
) {
    val tint = frostedTint()
    val border = frostedBorderColor()
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .frostedGlassBackground(tint = tint)
            .border(BorderStroke(1.dp, border), shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * 磨砂玻璃图标按钮。
 *
 * 图标颜色随 [LocalDarkMode] 切换黑白, 保证两个全局主题下都清晰。
 *
 * @param icon Material Icons 图标 (来自 material-icons-extended)
 * @param contentDescription 无障碍描述
 * @param onClick 点击回调
 * @param modifier 外部修饰
 * @param enabled 是否启用
 * @param size 按钮尺寸, 默认 40dp
 */
@Composable
fun FrostedIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
) {
    val baseColor = frostedIconColor()
    val iconColor = if (enabled) baseColor else baseColor.copy(alpha = 0.38f)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = baseColor,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
        )
    }
}

/**
 * 磨砂玻璃图标按钮 (带激活态)。
 *
 * 激活时图标使用强调色 (主题感知的对比色), 未激活使用默认图标色。
 * 适用于循环模式 / 随机 / 字幕开关等 toggle 按钮。
 *
 * @param active 是否处于激活态
 * @param activeColor 激活态图标颜色, 默认主题强调色
 */
@Composable
fun FrostedToggleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = if (LocalDarkMode.current) Color(0xFF82B1FF) else Color(0xFF2979FF),
    size: Dp = 40.dp,
) {
    val iconColor = if (active) activeColor else frostedIconColor()
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = iconColor,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
        )
    }
}

/**
 * 磨砂玻璃滑块 (用于进度条 / 音量 / 亮度)。
 *
 * 滑块轨道和拇指使用主题感知颜色。
 */
@Composable
fun FrostedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val iconColor = frostedIconColor()
    val trackColor = iconColor.copy(alpha = 0.3f)
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        colors = SliderDefaults.colors(
            thumbColor = iconColor,
            activeTrackColor = iconColor,
            inactiveTrackColor = trackColor,
            disabledThumbColor = iconColor.copy(alpha = 0.38f),
            disabledActiveTrackColor = iconColor.copy(alpha = 0.38f),
            disabledInactiveTrackColor = trackColor.copy(alpha = 0.38f),
        ),
    )
}

/**
 * 磨砂玻璃文本。
 */
@Composable
fun FrostedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = frostedTextColor(),
        fontSize = fontSize,
        fontWeight = fontWeight,
    )
}

// ── 内部: 磨砂玻璃背景绘制 ─────────────────────────────────────

/**
 * 绘制磨砂玻璃背景: 垂直渐变 (顶部更亮, 底部更暗) 模拟光线照射。
 * 真正的 Gaussian blur 由 Haze 提供 (API 31+), 低版本降级为这种半透明渐变。
 */
private fun Modifier.frostedGlassBackground(tint: Color): Modifier =
    this.drawBehind {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@drawBehind

        // 顶部更亮 (alpha * 1.05), 底部更暗 (alpha * 0.85) — 模拟光线
        val topAlpha = (tint.alpha * 1.05f).coerceIn(0f, 1f)
        val bottomAlpha = (tint.alpha * 0.85f).coerceIn(0f, 1f)
        val gradient = Brush.verticalGradient(
            colors = listOf(
                tint.copy(alpha = topAlpha),
                tint.copy(alpha = bottomAlpha),
            ),
            startY = 0f,
            endY = height,
        )
        drawRect(brush = gradient)

        // 顶部高光 (模拟玻璃受光)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.2f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = 8f,
            ),
        )
    }
