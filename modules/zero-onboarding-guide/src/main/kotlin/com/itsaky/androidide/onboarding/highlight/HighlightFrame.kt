/*
 *  This file is part of ZeroStudio.
 *
 *  ZeroStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ZeroStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with ZeroStudio.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.onboarding.highlight

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.onboarding.bubble.BubbleShape

/**
 * 高亮框样式 (Highlight style).
 *
 * 控制:
 *  - 背景蒙层颜色 (默认半透明黑)
 *  - 边框颜色 + 宽度
 *  - 圆角
 *  - 脉冲动画
 *  - 阴影 / 光晕
 *  - 内边距 (target 周围的 padding)
 */
data class HighlightStyle(
  val scrimColor: Color = Color(0x99000000),         // 全屏背景蒙层
  val borderColor: Color = Color(0xFFFFFFFF),        // 边框
  val borderWidth: Dp = 2.dp,
  val cornerRadius: Dp = 16.dp,
  val padding: Dp = 8.dp,                            // target 周围 padding
  val pulse: Boolean = true,                         // 脉冲动画
  val pulseColor: Color = Color(0xFFFFFFFF),
  val pulseWidth: Dp = 4.dp,
  val pulseDurationMs: Int = 1400,
  val glow: Boolean = true,                          // 阴影光晕
  val glowColor: Color = Color(0x66FFFFFF),
  val glowRadius: Dp = 12.dp,
) {
  companion object {
    val Default = HighlightStyle()

    val Subtle = HighlightStyle(
      scrimColor = Color(0x88000000),
      borderWidth = 1.5.dp,
      pulse = false,
      glow = true,
    )

    val Strong = HighlightStyle(
      scrimColor = Color(0xCC000000),
      borderColor = Color(0xFFFFFFFF),
      borderWidth = 3.dp,
      pulse = true,
      pulseWidth = 6.dp,
      glow = true,
      glowRadius = 20.dp,
    )
  }
}

/**
 * 高亮框选遮罩 (Highlight frame mask).
 *
 * 实现原理:
 *  - 在全屏 Box 上叠加一个 Canvas
 *  - 整个画布先填充 scrimColor 半透明蒙层
 *  - 然后用 BlendMode.DstOut 切出 target 区域 (变成透明, 露出下层 UI)
 *  - 最后在 target 边缘绘制高亮边框 + 脉冲动画
 *
 * 这样可以达到 "框选目标区域, 其他地方变暗" 的视觉效果,
 * 同时目标区域会自然透出底下的 UI 控件.
 *
 * @param targetRect 目标区域 (相对 Window 的坐标)
 * @param style 样式
 * @param modifier 外部 modifier
 */
@Composable
fun HighlightFrame(
  targetRect: Rect?,
  modifier: Modifier = Modifier,
  style: HighlightStyle = HighlightStyle.Default,
  shape: BubbleShape = BubbleShape.RoundedRectangle(style.cornerRadius),
) {
  // 脉冲动画
  val pulseAlpha = if (style.pulse) {
    val transition = rememberInfiniteTransition(label = "highlight-pulse-alpha")
    val a by transition.animateFloat(
      initialValue = 0.3f,
      targetValue = 0.9f,
      animationSpec = infiniteRepeatable(
        animation = tween(style.pulseDurationMs / 2),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "pulse-alpha",
    )
    a
  } else 0f

  val pulseScale = if (style.pulse) {
    val transition = rememberInfiniteTransition(label = "highlight-pulse-scale")
    val s by transition.animateFloat(
      initialValue = 0.95f,
      targetValue = 1.05f,
      animationSpec = infiniteRepeatable(
        animation = tween(style.pulseDurationMs / 2),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "pulse-scale",
    )
    s
  } else 1f

  Canvas(modifier = modifier) {
    if (targetRect == null) {
      // 无目标: 只画全屏蒙层
      drawRect(color = style.scrimColor)
      return@Canvas
    }

    val cornerPx = style.cornerRadius.toPx()
    val borderPx = style.borderWidth.toPx()
    val pulseWidthPx = style.pulseWidth.toPx()
    val paddingPx = style.padding.toPx()
    val glowPx = style.glowRadius.toPx()

    // 1. 整个画布填充 scrim
    drawRect(color = style.scrimColor)

    // 2. 切割出 target 区域 (BlendMode.DstOut 会让 dst 像素变透明)
    val expandedRect = Rect(
      left = targetRect.left - paddingPx,
      top = targetRect.top - paddingPx,
      right = targetRect.right + paddingPx,
      bottom = targetRect.bottom + paddingPx,
    )
    val expandedSize = Size(expandedRect.width, expandedRect.height)
    val expandedCornerRadius = CornerRadius(cornerPx, cornerPx)

    drawRoundRect(
      color = Color.Black,  // color 不重要, 用 DstOut 只在意 alpha
      topLeft = Offset(expandedRect.left, expandedRect.top),
      size = expandedSize,
      cornerRadius = expandedCornerRadius,
      blendMode = BlendMode.DstOut,
    )

    // 3. 脉冲光晕
    if (style.pulse && style.glow) {
      val pulseOffset = (1f - pulseScale) * 8f
      val pulseRect = Rect(
        left = expandedRect.left - pulseOffset - glowPx / 2f,
        top = expandedRect.top - pulseOffset - glowPx / 2f,
        right = expandedRect.right + pulseOffset + glowPx / 2f,
        bottom = expandedRect.bottom + pulseOffset + glowPx / 2f,
      )
      drawRoundRect(
        brush = Brush.radialGradient(
          colors = listOf(
            style.pulseColor.copy(alpha = pulseAlpha * 0.4f),
            Color.Transparent,
          ),
          center = Offset(pulseRect.center.x, pulseRect.center.y),
          radius = maxOf(pulseRect.width, pulseRect.height) / 1.2f,
        ),
        topLeft = Offset(pulseRect.left, pulseRect.top),
        size = Size(pulseRect.width, pulseRect.height),
        cornerRadius = CornerRadius(cornerPx + pulseOffset, cornerPx + pulseOffset),
      )
    }

    // 4. 在 target 边缘绘制高亮边框
    drawRoundRect(
      color = style.borderColor,
      topLeft = Offset(expandedRect.left, expandedRect.top),
      size = expandedSize,
      cornerRadius = expandedCornerRadius,
      style = Stroke(width = borderPx),
    )

    // 5. 内部第二次细描边 (增强立体感)
    val innerInset = borderPx / 2f + 2f
    drawRoundRect(
      color = style.borderColor.copy(alpha = 0.5f),
      topLeft = Offset(expandedRect.left + innerInset, expandedRect.top + innerInset),
      size = Size(expandedRect.width - innerInset * 2f, expandedRect.height - innerInset * 2f),
      cornerRadius = CornerRadius(
        (cornerPx - innerInset).coerceAtLeast(0f),
        (cornerPx - innerInset).coerceAtLeast(0f),
      ),
      style = Stroke(width = 1f),
    )
  }
}

/**
 * 简易高亮遮罩: 仅 scrim, 无边框, 无脉冲.
 * 适合简洁的引导场景.
 */
@Composable
fun SimpleScrim(
  modifier: Modifier = Modifier,
  color: Color = Color(0x99000000),
) {
  Canvas(modifier = modifier) {
    drawRect(color = color)
  }
}
