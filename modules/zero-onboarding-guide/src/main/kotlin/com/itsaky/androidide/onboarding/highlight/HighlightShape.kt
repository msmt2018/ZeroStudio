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

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 高亮遮罩形状 (Highlight mask shape).
 *
 * 设计目标: 每个 UI 控件形状都不一样, 所在位置也不同, 所以高亮形状必须**自适应**.
 * 提供 8 种内置形状 + 自动适配 (Auto) 模式.
 *
 * 形状与 UI 控件的对应:
 *  - 按钮 / 卡片 → [RoundedRect]
 *  - 圆形头像 / FAB → [Circle]
 *  - 椭圆形头像 → [Oval]
 *  - 胶囊状态标签 → [Stadium]
 *  - 多边形装饰 → [Polygon] (六边形, 八边形)
 *  - 自然 / 创意形状 → [Blob]
 *  - 圆形头像聚焦 → [Spotlight] (圆形高光 + 周围暗)
 *  - 自定义 → [Custom]
 */
sealed class HighlightShape {

  /**
   * 自适应形状: 根据 target 的宽高比自动选择最合适的形状.
   *  - 接近正方形 → Circle
   *  - 宽矩形 → RoundedRect (圆角矩形)
   *  - 极扁 → Stadium (胶囊)
   */
  data object Auto : HighlightShape()

  /** 圆角矩形 (最常见). */
  data class RoundedRect(
    val cornerRadius: Dp = 16.dp,
  ) : HighlightShape()

  /** 矩形 (无圆角). */
  data object Rect : HighlightShape()

  /** 圆形. */
  data object Circle : HighlightShape()

  /** 椭圆 (按 target 比例). */
  data object Oval : HighlightShape()

  /** 胶囊 (圆角 = 高度一半). */
  data object Stadium : HighlightShape()

  /** 多边形 (六边形, 八边形等). */
  data class Polygon(
    val sides: Int = 6,
    val cornerRadius: Dp = 0.dp,
  ) : HighlightShape()

  /** 创意 / 自然形状 (Blob). 由多个控制点 + Catmull-Rom 平滑插值生成. */
  data class Blob(
    val seed: Int = 42,
    val points: Int = 6,
    val irregularity: Float = 0.3f,
  ) : HighlightShape()

  /** 聚光 (圆形 Spotlight): 圆形高亮 + 周围暗 (像舞台聚光灯). */
  data object Spotlight : HighlightShape()

  /** 自定义形状. */
  data class Custom(
    val shape: Shape,
  ) : HighlightShape()

  companion object {
    /** 默认: 自适应 */
    val Default: HighlightShape = Auto
  }
}

/**
 * 在 [DrawScope] 中实际渲染高亮时, 将 [HighlightShape] + target Rect 转换为 [Shape].
 */
internal fun HighlightShape.toShape(
  target: Rect?,
  density: Density,
): Shape = when (this) {
  HighlightShape.Auto -> {
    if (target == null) {
      RoundedCornerShape(16.dp)
    } else {
      val aspect = target.width / target.height.coerceAtLeast(1f)
      when {
        aspect in 0.85f..1.15f -> CircleShape
        aspect > 2.5f || aspect < 0.4f -> StadiumShapeImpl
        else -> {
          val r = with(density) { (min(target.width, target.height) * 0.12f).toDp() }
          RoundedCornerShape(r)
        }
      }
    }
  }
  is HighlightShape.RoundedRect -> RoundedCornerShape(cornerRadius)
  HighlightShape.Rect -> androidx.compose.foundation.shape.RectangleShape
  HighlightShape.Circle -> CircleShape
  HighlightShape.Oval -> OvalShapeImpl
  HighlightShape.Stadium -> StadiumShapeImpl
  is HighlightShape.Polygon -> PolygonShapeImpl(sides = sides, cornerRadiusPx = with(density) { cornerRadius.toPx() })
  is HighlightShape.Blob -> BlobShapeImpl(seed = seed, points = points, irregularity = irregularity)
  HighlightShape.Spotlight -> SpotlightShapeImpl
  is HighlightShape.Custom -> shape
}

// =============================================================================
// 实际 Shape 实现 (内部)
// =============================================================================

internal object OvalShapeImpl : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    return Outline.Generic(Path().apply {
      addOval(Rect(0f, 0f, size.width, size.height))
    })
  }
}

internal object StadiumShapeImpl : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val r = min(size.width, size.height) / 2f
    return Outline.Generic(Path().apply {
      addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
          left = 0f, top = 0f, right = size.width, bottom = size.height,
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
      )
    })
  }
}

internal class PolygonShapeImpl(
  private val sides: Int,
  private val cornerRadiusPx: Float = 0f,
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val path = Path()
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val rx = w / 2f
    val ry = h / 2f
    val n = sides.coerceAtLeast(3)

    for (i in 0 until n) {
      val angle = (i * 2.0 * PI / n - PI / 2).toFloat()
      val x = cx + rx * cos(angle)
      val y = cy + ry * sin(angle)
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return Outline.Generic(path)
  }
}

internal class BlobShapeImpl(
  private val seed: Int = 42,
  private val points: Int = 6,
  private val irregularity: Float = 0.3f,
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val path = Path()
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val baseRadius = min(w, h) / 2f
    val rng = kotlin.random.Random(seed)
    val n = points.coerceAtLeast(3)
    val angles = FloatArray(n) { i -> (i * 2.0 * PI / n - PI / 2).toFloat() }
    val radii = FloatArray(n) {
      baseRadius * (1f - irregularity * rng.nextFloat())
    }

    val pts = mutableListOf<Offset>()
    for (i in 0 until n) {
      pts.add(Offset(cx + radii[i] * cos(angles[i]), cy + radii[i] * sin(angles[i])))
    }
    val samples = 8
    val smoothed = mutableListOf<Offset>()
    for (i in 0 until n) {
      val p0 = pts[(i - 1 + n) % n]
      val p1 = pts[i]
      val p2 = pts[(i + 1) % n]
      val p3 = pts[(i + 2) % n]
      for (j in 0 until samples) {
        val t = j.toFloat() / samples
        val tt = t * t
        val ttt = tt * t
        val q1 = -ttt + 2f * tt - t
        val q2 = 3f * ttt - 5f * tt + 2f
        val q3 = -3f * ttt + 4f * tt + t
        val q4 = ttt - tt
        val x = 0.5f * (p0.x * q1 + p1.x * q2 + p2.x * q3 + p3.x * q4)
        val y = 0.5f * (p0.y * q1 + p1.y * q2 + p2.y * q3 + p3.y * q4)
        smoothed.add(Offset(x, y))
      }
    }
    smoothed.forEachIndexed { idx, p ->
      if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    return Outline.Generic(path)
  }
}

internal object SpotlightShapeImpl : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val path = Path()
    val w = size.width
    val h = size.height
    val r = min(w, h) / 2f
    val cx = w / 2f
    val cy = h / 2f
    path.addOval(Rect(cx - r, cy - r, cx + r, cy + r))
    return Outline.Generic(path)
  }
}
