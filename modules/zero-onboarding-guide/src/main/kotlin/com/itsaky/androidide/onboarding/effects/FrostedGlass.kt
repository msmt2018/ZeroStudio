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

package com.itsaky.androidide.onboarding.effects

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 磨砂玻璃效果 (Frosted glass effect).
 *
 * 由于 Haze 库通过 LocalHazeState 在 Composable 树中传递,
 * 真正的 blur 渲染需要外部组合 [dev.chrisbanes.haze.haze] / [dev.chrisbanes.haze.hazeChild]
 * modifier. 本文件提供:
 *  - 微高光 (顶 1px 内描边) — 让气泡更有立体感
 *  - 微内阴影 (底部凹陷感) — 模拟真实磨砂玻璃
 *  - 微渐变背景 (顶部更亮, 底部更暗) — 模仿光线照射
 *
 * 完整的磨砂玻璃需要叠加:
 *  1. 渐变底色 (本文件提供)
 *  2. Haze blur (在 GuideBubble 中组合)
 *  3. 颗粒噪点 (GrainNoise.kt)
 */
fun Modifier.frostedGlass(
  shape: Shape,
  tint: Color = Color.White,
  alpha: Float = 0.55f,
  borderColor: Color = Color(0x66FFFFFF),
  borderWidth: Dp = 1.dp,
  innerHighlight: Boolean = true,
  innerHighlightColor: Color = Color(0x55FFFFFF),
): Modifier = drawBehind {
  val width = size.width
  val height = size.height
  if (width <= 0f || height <= 0f) return@drawBehind

  val cornerRadius = when (shape) {
    is RoundedCornerShape -> CornerRadius(
      shape.topStart.toPx(size, this),
      shape.topEnd.toPx(size, this),
    )
    else -> CornerRadius(20.dp.toPx())
  }

  // 1. 玻璃底色 (偏白半透明, 顶部更亮, 底部更暗 — 模拟光线)
  val topAlpha = (alpha * 1.05f).coerceIn(0f, 1f)
  val bottomAlpha = (alpha * 0.85f).coerceIn(0f, 1f)
  val gradient = Brush.verticalGradient(
    colors = listOf(
      tint.copy(alpha = topAlpha),
      tint.copy(alpha = bottomAlpha),
    ),
    startY = 0f,
    endY = height,
  )
  drawRoundRect(
    brush = gradient,
    cornerRadius = cornerRadius,
  )

  // 2. 描边
  if (borderWidth.toPx() > 0f) {
    drawRoundRect(
      color = borderColor,
      cornerRadius = cornerRadius,
      style = Stroke(width = borderWidth.toPx()),
    )
  }

  // 3. 顶部内描边高光 (1px 顶光, 模拟玻璃受光)
  if (innerHighlight) {
    drawRoundRect(
      brush = Brush.verticalGradient(
        colors = listOf(
          innerHighlightColor,
          Color.Transparent,
        ),
        startY = 0f,
        endY = 8f,
      ),
      cornerRadius = cornerRadius,
    )
  }
}
