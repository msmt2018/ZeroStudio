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

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 旧版高亮样式 (向后兼容, 内部路由到新系统).
 *
 * 推荐直接使用 [HighlightShape] + [HighlightTheme] + [HighlightAnimation] 三维度组合.
 */
data class HighlightStyle(
  val scrimColor: Color = Color(0x99000000),
  val borderColor: Color = Color(0xFFFFFFFF),
  val borderWidth: Dp = 2.dp,
  val cornerRadius: Dp = 16.dp,
  val padding: Dp = 8.dp,
  val pulse: Boolean = true,
  val pulseColor: Color = Color(0xFFFFFFFF),
  val pulseWidth: Dp = 4.dp,
  val pulseDurationMs: Int = 1400,
  val glow: Boolean = true,
  val glowColor: Color = Color(0x66FFFFFF),
  val glowRadius: Dp = 12.dp,
  val shape: HighlightShape = HighlightShape.Auto,
  val theme: HighlightTheme = HighlightTheme.Default,
  val animation: HighlightAnimation = HighlightAnimation.Default,
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
 * 高亮框选遮罩 (Highlight frame mask) — 三维度系统.
 *
 * 设计原则 (重要):
 *  - **不改变被框选目标的外观** (颜色 / 折叠 / 形状). 目标区域用 [BlendMode.DstOut] 切割,
 *    露出下层原始 UI 控件.
 *  - **形状自适应**: 通过 [shape] 参数 (默认 [HighlightShape.Auto]) 根据 target 形状自动选择.
 *  - **多主题**: 通过 [theme] 参数选择视觉风格 (实色 / 虚线 / 霓虹 / 胶带 / 聚光 / 磨砂).
 *  - **多动画**: 通过 [animation] 参数选择动画 (脉冲 / 旋转 / 呼吸 / 扫描 / 波动 / 微光).
 *
 * @param targetRect 目标区域 (相对 Window 的坐标)
 * @param shape 高亮形状
 * @param theme 高亮主题
 * @param animation 高亮动画
 * @param padding 围绕 target 的额外 padding
 * @param modifier 外部 modifier
 */
@Composable
fun HighlightFrame(
  targetRect: Rect?,
  modifier: Modifier = Modifier,
  shape: HighlightShape = HighlightShape.Auto,
  theme: HighlightTheme = HighlightTheme.Default,
  animation: HighlightAnimation = HighlightAnimation.Default,
  padding: Dp = 8.dp,
) {
  // 计算动画状态
  val animState = rememberHighlightAnimation(animation)

  Canvas(modifier = modifier) {
    if (targetRect == null) {
      // 无目标: 全屏 scrim
      drawScrim(theme, target = null, padding = 0f)
      return@Canvas
    }

    val paddingPx = padding.toPx()

    // 扩展后的目标区域
    val expandedRect = Rect(
      left = targetRect.left - paddingPx,
      top = targetRect.top - paddingPx,
      right = targetRect.right + paddingPx,
      bottom = targetRect.bottom + paddingPx,
    )

    // 1. 绘制 scrim (背景蒙层) + 用 BlendMode.DstOut 切出 target
    drawScrimWithCutout(theme, expandedRect, shape, density = this)

    // 2. 在 target 边缘绘制主题边框
    drawThemeBorder(theme, expandedRect, shape, density = this)

    // 3. 绘制动画
    drawAnimationEffect(animation, animState, theme, expandedRect, shape, density = this)
  }
}

/**
 * 向后兼容: 旧版 [HighlightStyle] 入口.
 */
@Composable
fun HighlightFrame(
  targetRect: Rect?,
  style: HighlightStyle,
  modifier: Modifier = Modifier,
) {
  HighlightFrame(
    targetRect = targetRect,
    modifier = modifier,
    shape = style.shape,
    theme = style.theme,
    animation = style.animation,
    padding = style.padding,
  )
}

/**
 * 记住动画状态 (根据 [HighlightAnimation] 类型).
 */
@Composable
private fun rememberHighlightAnimation(animation: HighlightAnimation): HighlightAnimState {
  val transition = rememberInfiniteTransition(label = "highlight-anim")

  return when (animation) {
    HighlightAnimation.None -> HighlightAnimState.NONE

    is HighlightAnimation.Pulse -> {
      val a by transition.animateFloat(
        initialValue = animation.minAlpha,
        targetValue = animation.maxAlpha,
        animationSpec = infiniteRepeatable(
          animation = tween(animation.durationMs / 2, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-alpha",
      )
      val s by transition.animateFloat(
        initialValue = animation.minScale,
        targetValue = animation.maxScale,
        animationSpec = infiniteRepeatable(
          animation = tween(animation.durationMs / 2, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-scale",
      )
      HighlightAnimState(alpha = a, scale = s, rawFloat = a)
    }

    is HighlightAnimation.Breathe -> {
      val a by transition.animateFloat(
        initialValue = animation.minAlpha,
        targetValue = animation.maxAlpha,
        animationSpec = infiniteRepeatable(
          animation = tween(animation.durationMs / 2, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
      )
      HighlightAnimState(alpha = a, scale = 1f, rawFloat = a)
    }

    is HighlightAnimation.Rotate -> {
      val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
          animation = tween(animation.durationMs, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
        label = "rotate",
      )
      val a = if (animation.direction == HighlightAnimation.Rotate.Direction.CounterClockwise) -angle else angle
      HighlightAnimState(alpha = 1f, scale = 1f, rawFloat = a, rotation = a)
    }

    is HighlightAnimation.Scan -> {
      val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
          animation = tween(animation.durationMs, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
        label = "scan",
      )
      HighlightAnimState(alpha = 1f, scale = 1f, rawFloat = progress, scanProgress = progress)
    }

    is HighlightAnimation.Wave -> {
      val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
          animation = tween(animation.durationMs, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
        label = "wave",
      )
      HighlightAnimState(alpha = 1f, scale = 1f, rawFloat = progress, scanProgress = progress)
    }

    is HighlightAnimation.Shimmer -> {
      val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
          animation = tween(animation.durationMs, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
      )
      HighlightAnimState(alpha = 1f, scale = 1f, rawFloat = t, scanProgress = t)
    }
  }
}

/** 动画状态 (组合所有动画参数). */
private data class HighlightAnimState(
  val alpha: Float,
  val scale: Float,
  val rawFloat: Float,
  val rotation: Float = 0f,
  val scanProgress: Float = 0f,
) {
  companion object {
    val NONE = HighlightAnimState(1f, 1f, 0f)
  }
}

// =============================================================================
// DrawScope 扩展 (实际绘制)
// =============================================================================

/**
 * 绘制 scrim (背景蒙层) 并用 BlendMode.DstOut 切出 target 区域.
 *
 * 关键: 用 DstOut 切割, 让 target 区域透明, 露出下层 UI 控件的原始外观.
 * **不会** 改变 target 颜色 / 形状 / 折叠状态.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScrimWithCutout(
  theme: HighlightTheme,
  rect: Rect,
  shape: HighlightShape,
  density: androidx.compose.ui.unit.Density,
) {
  val scrim = theme.scrimColorOrNull ?: Color(0x99000000)
  // 1. 整个画布填充 scrim
  drawRect(color = scrim)

  // 2. 用 BlendMode.DstOut 切出 target
  val shapeImpl = shape.toShape(rect, density)
  val outline = shapeImpl.createOutline(Size(rect.width, rect.height), layoutDirection, density)
  val path = when (outline) {
    is Outline.Generic -> outline.path
    is Outline.Rounded -> androidx.compose.ui.graphics.Path().apply {
      addRoundRect(outline.roundRect)
    }
    else -> null
  }
  if (path != null) {
    translate(left = rect.left, top = rect.top) {
      drawPath(
        path = path,
        color = Color.Black,
        blendMode = BlendMode.DstOut,
      )
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScrim(
  theme: HighlightTheme,
  target: Rect?,
  padding: Float,
) {
  drawRect(color = theme.scrimColorOrNull ?: Color(0x99000000))
}

/**
 * 绘制主题边框.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThemeBorder(
  theme: HighlightTheme,
  rect: Rect,
  shape: HighlightShape,
  density: androidx.compose.ui.unit.Density,
) {
  val size = Size(rect.width, rect.height)
  when (val t = theme) {
    is HighlightTheme.Solid -> {
      val shapeImpl = shape.toShape(rect, density)
      val outline = shapeImpl.createOutline(size, layoutDirection, density)
      val path = (outline as? Outline.Generic)?.path
      if (path != null) {
        translate(left = rect.left, top = rect.top) {
          drawPath(
            path = path,
            color = t.borderColor.copy(alpha = t.borderAlpha),
            style = Stroke(width = t.borderWidth.toPx()),
          )
        }
      } else {
        drawRoundRect(
          color = t.borderColor.copy(alpha = t.borderAlpha),
          size = size,
          cornerRadius = CornerRadius(16f, 16f),
          style = Stroke(width = t.borderWidth.toPx()),
        )
      }
    }
    is HighlightTheme.Dashed -> {
      val effect = PathEffect.dashPathEffect(
        floatArrayOf(t.dashLength.toPx(), t.gapLength.toPx()),
        0f,
      )
      val shapeImpl = shape.toShape(rect, density)
      val outline = shapeImpl.createOutline(size, layoutDirection, density)
      val path = (outline as? Outline.Generic)?.path
      if (path != null) {
        translate(left = rect.left, top = rect.top) {
          drawPath(
            path = path,
            color = t.borderColor,
            style = Stroke(width = t.borderWidth.toPx(), pathEffect = effect),
          )
        }
      } else {
        drawRoundRect(
          color = t.borderColor,
          topLeft = Offset(rect.left, rect.top),
          size = size,
          cornerRadius = CornerRadius(16f, 16f),
          style = Stroke(width = t.borderWidth.toPx(), pathEffect = effect),
        )
      }
    }
    is HighlightTheme.Dotted -> {
      val effect = PathEffect.dashPathEffect(
        floatArrayOf(1f, t.dotSpacing.toPx()),
        0f,
      )
      val shapeImpl = shape.toShape(rect, density)
      val outline = shapeImpl.createOutline(size, layoutDirection, density)
      val path = (outline as? Outline.Generic)?.path
      if (path != null) {
        translate(left = rect.left, top = rect.top) {
          drawPath(
            path = path,
            color = t.borderColor,
            style = Stroke(width = t.borderWidth.toPx(), pathEffect = effect),
          )
        }
      }
    }
    is HighlightTheme.Neon -> {
      val shapeImpl = shape.toShape(rect, density)
      val outline = shapeImpl.createOutline(size, layoutDirection, density)
      val path = (outline as? Outline.Generic)?.path
      // 外发光 (2 圈, 比之前 4 圈更精细, 避免边框看起来粗糙)
      if (path != null) {
        translate(left = rect.left, top = rect.top) {
          // 第 1 圈 (外层): 模糊光晕
          drawPath(
            path = path,
            color = t.glowColor.copy(alpha = 0.18f),
            style = Stroke(width = t.borderWidth.toPx() + t.glowRadius.toPx() * 0.6f),
          )
          // 第 2 圈 (中层): 较亮光晕
          drawPath(
            path = path,
            color = t.glowColor.copy(alpha = 0.35f),
            style = Stroke(width = t.borderWidth.toPx() + t.glowRadius.toPx() * 0.25f),
          )
          // 实色 (中心): 高对比主线
          drawPath(
            path = path,
            color = t.color,
            style = Stroke(width = t.borderWidth.toPx()),
          )
        }
      }
    }
    is HighlightTheme.Tape -> {
      // 四角 L 形括号
      val cornerLen = t.length.toPx()
      val thick = t.thickness.toPx()
      val l = rect.left
      val r = rect.right
      val top = rect.top
      val b = rect.bottom
      val halfThick = thick / 2f

      // 左上
      drawLine(
        color = t.color,
        start = Offset(l, top + cornerLen),
        end = Offset(l, top + halfThick),
        strokeWidth = thick,
      )
      drawLine(
        color = t.color,
        start = Offset(l, top),
        end = Offset(l + cornerLen, top),
        strokeWidth = thick,
      )
      // 右上
      drawLine(
        color = t.color,
        start = Offset(r, top + cornerLen),
        end = Offset(r, top + halfThick),
        strokeWidth = thick,
      )
      drawLine(
        color = t.color,
        start = Offset(r, top),
        end = Offset(r - cornerLen, top),
        strokeWidth = thick,
      )
      // 左下
      drawLine(
        color = t.color,
        start = Offset(l, b - cornerLen),
        end = Offset(l, b - halfThick),
        strokeWidth = thick,
      )
      drawLine(
        color = t.color,
        start = Offset(l, b),
        end = Offset(l + cornerLen, b),
        strokeWidth = thick,
      )
      // 右下
      drawLine(
        color = t.color,
        start = Offset(r, b - cornerLen),
        end = Offset(r, b - halfThick),
        strokeWidth = thick,
      )
      drawLine(
        color = t.color,
        start = Offset(r, b),
        end = Offset(r - cornerLen, b),
        strokeWidth = thick,
      )
    }
    is HighlightTheme.Corners -> {
      // 简化版的 Tape (短一些)
      val cornerLen = t.length.toPx()
      val thick = t.thickness.toPx()
      val cr = t.cornerRadius.toPx()
      val positions = listOf(
        Offset(rect.left, rect.top),     // TL
        Offset(rect.right, rect.top),    // TR
        Offset(rect.left, rect.bottom),  // BL
        Offset(rect.right, rect.bottom), // BR
      )
      positions.forEach { p ->
        val dx = if (p.x < (rect.left + rect.right) / 2) 1f else -1f
        val dy = if (p.y < (rect.top + rect.bottom) / 2) 1f else -1f
        // 横线
        drawLine(
          color = t.color,
          start = p,
          end = Offset(p.x + cornerLen * dx, p.y),
          strokeWidth = thick,
        )
        // 竖线
        drawLine(
          color = t.color,
          start = p,
          end = Offset(p.x, p.y + cornerLen * dy),
          strokeWidth = thick,
        )
      }
    }
    is HighlightTheme.Spotlight -> {
      // Spotlight: 已经在 scrim 中用圆形切出 + 边框
      val shapeImpl = shape.toShape(rect, density)
      val outline = shapeImpl.createOutline(size, layoutDirection, density)
      val path = (outline as? Outline.Generic)?.path
      if (path != null) {
        translate(left = rect.left, top = rect.top) {
          drawPath(
            path = path,
            color = t.ringColor,
            style = Stroke(width = t.ringWidth.toPx()),
          )
        }
      }
    }
    is HighlightTheme.Frosted -> {
      val shapeImpl = shape.toShape(rect, density)
      val outline = shapeImpl.createOutline(size, layoutDirection, density)
      val path = (outline as? Outline.Generic)?.path
      if (path != null) {
        translate(left = rect.left, top = rect.top) {
          // 微白色磨砂边
          drawPath(
            path = path,
            brush = Brush.verticalGradient(
              colors = listOf(
                t.borderColor.copy(alpha = t.blurAlpha + 0.3f),
                t.borderColor.copy(alpha = t.blurAlpha * 0.5f),
              ),
            ),
            style = Stroke(width = t.borderWidth.toPx()),
          )
        }
      }
    }
    is HighlightTheme.Custom -> drawThemeBorder(t.theme, rect, shape, density)
  }
}

/**
 * 绘制动画效果.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnimationEffect(
  animation: HighlightAnimation,
  state: HighlightAnimState,
  theme: HighlightTheme,
  rect: Rect,
  shape: HighlightShape,
  density: androidx.compose.ui.unit.Density,
) {
  when (animation) {
    is HighlightAnimation.Pulse -> {
      val glowAlpha = state.alpha
      val glowSize = state.scale
      val shapeImpl = shape.toShape(rect, density)
      val outline = shapeImpl.createOutline(
        Size(rect.width * glowSize, rect.height * glowSize),
        layoutDirection,
        density,
      )
      val path = (outline as? Outline.Generic)?.path
      if (path != null) {
        translate(left = rect.left, top = rect.top) {
          drawPath(
            path = path,
            color = Color.White.copy(alpha = glowAlpha * 0.3f),
            style = Stroke(width = animation.maxScale * 8f),
          )
        }
      }
    }
    is HighlightAnimation.Rotate -> {
      // 旋转的虚线 (在边框外圈)
      rotate(degrees = state.rotation, pivot = rect.center) {
        val shapeImpl = shape.toShape(rect, density)
        val outline = shapeImpl.createOutline(Size(rect.width, rect.height), layoutDirection, density)
        val path = (outline as? Outline.Generic)?.path
        if (path != null) {
          translate(left = rect.left, top = rect.top) {
            drawPath(
              path = path,
              color = Color.White.copy(alpha = 0.6f),
              style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
              ),
            )
          }
        }
      }
    }
    is HighlightAnimation.Breathe -> {
      val shapeImpl = shape.toShape(rect, density)
      val outline = shapeImpl.createOutline(Size(rect.width, rect.height), layoutDirection, density)
      val path = (outline as? Outline.Generic)?.path
      if (path != null) {
        translate(left = rect.left, top = rect.top) {
          drawPath(
            path = path,
            color = Color.White.copy(alpha = state.alpha * 0.5f),
            style = Stroke(width = 6f),
          )
        }
      }
    }
    is HighlightAnimation.Scan -> {
      val progress = state.scanProgress
      val lineX = rect.left + rect.width * progress
      drawLine(
        color = animation.lineColor.copy(alpha = animation.lineAlpha),
        start = Offset(lineX, rect.top),
        end = Offset(lineX, rect.bottom),
        strokeWidth = animation.lineWidth.toPx(),
      )
      // 两端柔和渐变 (用渐变线替代, 这里简单实现)
      drawLine(
        color = animation.lineColor.copy(alpha = animation.lineAlpha * 0.3f),
        start = Offset(lineX - 16f, rect.top),
        end = Offset(lineX - 16f, rect.bottom),
        strokeWidth = animation.lineWidth.toPx() * 2f,
      )
      drawLine(
        color = animation.lineColor.copy(alpha = animation.lineAlpha * 0.3f),
        start = Offset(lineX + 16f, rect.top),
        end = Offset(lineX + 16f, rect.bottom),
        strokeWidth = animation.lineWidth.toPx() * 2f,
      )
    }
    is HighlightAnimation.Wave -> {
      val progress = state.scanProgress
      val cx = rect.center.x
      val cy = rect.center.y
      val baseRadius = min(rect.width, rect.height) / 2f
      for (i in 0 until animation.waveCount) {
        val phase = (progress + i.toFloat() / animation.waveCount) % 1f
        val radius = baseRadius * (1f + (animation.maxRadiusMultiplier - 1f) * phase)
        drawCircle(
          color = animation.waveColor.copy(alpha = animation.waveAlpha * (1f - phase)),
          radius = radius,
          center = Offset(cx, cy),
          style = Stroke(width = 2f),
        )
      }
    }
    is HighlightAnimation.Shimmer -> {
      val t = state.scanProgress
      val shapeImpl = shape.toShape(rect, density)
      val outline = shapeImpl.createOutline(Size(rect.width, rect.height), layoutDirection, density)
      val path = (outline as? Outline.Generic)?.path
      if (path != null) {
        translate(left = rect.left, top = rect.top) {
          val shimmerColor = lerpColor(animation.baseColor, animation.highlightColor, kotlin.math.abs(t - 0.5f) * 2f)
          drawPath(
            path = path,
            color = shimmerColor,
            style = Stroke(width = 2f),
          )
        }
      }
    }
    HighlightAnimation.None -> Unit
  }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
  val tt = t.coerceIn(0f, 1f)
  return Color(
    red = a.red + (b.red - a.red) * tt,
    green = a.green + (b.green - a.green) * tt,
    blue = a.blue + (b.blue - a.blue) * tt,
    alpha = a.alpha + (b.alpha - a.alpha) * tt,
  )
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
