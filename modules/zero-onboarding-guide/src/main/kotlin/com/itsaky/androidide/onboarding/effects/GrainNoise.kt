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

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * 颗粒噪点效果 (Grain noise effect).
 *
 * 纯 Compose 实现的高频随机噪点, 模拟真实磨砂玻璃表面的微小颗粒感.
 * 设计要点:
 *  - 使用伪随机点 + 极低 alpha 模拟微颗粒
 *  - 默认 grainDensity = 320 (每 1000x1000 像素约 320 个噪点)
 *  - grainColor / grainAlpha 可调
 *  - 高性能: 一次性 drawPoints, 不需要逐点 DrawScope
 *
 * 视觉效果:
 *  - 在偏白半透明磨砂底上叠加 0.05-0.10 透明度的微颗粒
 *  - 看起来有真实的玻璃质感, 而不是单纯的纯色
 */
fun Modifier.grainNoise(
  shape: Shape,
  grainColor: Color = Color(0xFFFFFFFF),
  grainAlpha: Float = 0.06f,
  grainDensity: Int = 320,
  seed: Int = 42,
  clip: Boolean = true,
): Modifier = drawBehind {
  val width = size.width
  val height = size.height
  if (width <= 0f || height <= 0f) return@drawBehind

  val rng = Random(seed)
  // 颗粒数量按面积线性缩放
  val area = (width * height) / 1_000_000f
  val count = (grainDensity * area).toInt().coerceIn(50, 4000)

  // 一次性生成所有点 (List<Offset> 是当前 drawPoints 接受的类型)
  val points = List(count) {
    Offset(rng.nextFloat() * width, rng.nextFloat() * height)
  }

  // 半透明点 — 通过 dstOver 叠加在底色上, 不会让背景变白
  drawPoints(
    points = points,
    pointMode = PointMode.Points,
    color = grainColor.copy(alpha = grainAlpha),
    strokeWidth = 1.2f,
  )
}
