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

package com.itsaky.androidide.onboarding.simulation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.hypot

/**
 * 操作手势类型 (Operation gesture types).
 */
enum class GestureType {
  TAP,            // 短按
  LONG_PRESS,     // 长按
  SWIPE,          // 滑 (单方向)
  DRAG,           // 拖拽 (可往返)
  MULTI_TOUCH,    // 多点触控
  SCROLL,         // 滚动
}

/**
 * 操作路径点 (Path point).
 *
 * @param x 相对 Overlay 的 x 坐标
 * @param y 相对 Overlay 的 y 坐标
 * @param timeMs 时间戳 (毫秒, 从 0 开始)
 * @param pressure 压力 (0f-1f, 仅在模拟压力屏时使用)
 */
data class PathPoint(
  val x: Float,
  val y: Float,
  val timeMs: Int,
  val pressure: Float = 1f,
)

/**
 * 操作模拟配置 (Touch simulation).
 *
 * 描述一次完整的"模拟用户操作"行为:
 *  - 类型: tap / long-press / swipe / drag / multi-touch
 *  - 路径: 一组 PathPoint (从起点到终点)
 *  - 持续时间: 路径总时长
 *  - 自动循环: 是否重复播放
 *  - 是否显示手指 + 波纹
 *
 * @property type 手势类型
 * @property points 路径点 (从起点到终点, 按 timeMs 升序)
 * @property loop 是否循环播放
 * @property showFinger 是否显示"手指"指示器
 * @property showTrail 是否显示拖拽轨迹
 * @property showRipple 是否显示点击波纹
 * @property fingerSize 手指圆点尺寸
 * @property fingerColor 手指圆点颜色
 * @property rippleColor 波纹颜色
 * @property trailColor 轨迹颜色
 * @property loopDelayMs 循环间隔
 */
data class TouchSimulator(
  val type: GestureType,
  val points: List<PathPoint>,
  val loop: Boolean = true,
  val showFinger: Boolean = true,
  val showTrail: Boolean = true,
  val showRipple: Boolean = true,
  val fingerSize: Dp = 56.dp,
  val fingerColor: Color = Color(0xFFFFFFFF),
  val fingerInnerColor: Color = Color(0x66FFFFFF),
  val rippleColor: Color = Color(0x66FFFFFF),
  val trailColor: Color = Color(0x88FFFFFF),
  val loopDelayMs: Int = 400,
) {
  init {
    require(points.isNotEmpty()) { "TouchSimulator requires at least one point" }
    require(points.size == 1 || type != GestureType.TAP) {
      "TAP requires exactly 1 point"
    }
  }

  val durationMs: Int = points.maxOf { it.timeMs }
  val startPoint: PathPoint = points.first()
  val endPoint: PathPoint = points.last()

  companion object {
    /** 创建一个 TAP (单击) */
    fun tap(
      x: Float, y: Float,
      durationMs: Int = 240,
      loop: Boolean = true,
    ) = TouchSimulator(
      type = GestureType.TAP,
      points = listOf(
        PathPoint(x, y, 0),
        PathPoint(x, y, durationMs),
      ),
      loop = loop,
    )

    /** 创建一个 LONG_PRESS (长按) */
    fun longPress(
      x: Float, y: Float,
      durationMs: Int = 1400,
      loop: Boolean = true,
    ) = TouchSimulator(
      type = GestureType.LONG_PRESS,
      points = listOf(
        PathPoint(x, y, 0),
        PathPoint(x, y, durationMs),
      ),
      loop = loop,
    )

    /** 创建一个 SWIPE (单向滑动) */
    fun swipe(
      fromX: Float, fromY: Float,
      toX: Float, toY: Float,
      durationMs: Int = 600,
      loop: Boolean = true,
    ) = TouchSimulator(
      type = GestureType.SWIPE,
      points = listOf(
        PathPoint(fromX, fromY, 0),
        PathPoint(toX, toY, durationMs),
      ),
      loop = loop,
    )

    /** 创建一个 DRAG (拖拽, 支持多中间点) */
    fun drag(
      points: List<PathPoint>,
      loop: Boolean = false,
    ) = TouchSimulator(
      type = GestureType.DRAG,
      points = points,
      loop = loop,
    )

    /** 创建一个 SCROLL (滚动) */
    fun scroll(
      fromX: Float, fromY: Float,
      toX: Float, toY: Float,
      durationMs: Int = 400,
      loop: Boolean = true,
    ) = TouchSimulator(
      type = GestureType.SCROLL,
      points = listOf(
        PathPoint(fromX, fromY, 0),
        PathPoint(toX, toY, durationMs),
      ),
      loop = loop,
    )

    /** 创建一个 MULTI_TOUCH (多点触控, 演示用) */
    fun multiTouch(
      points: List<PathPoint>,
      loop: Boolean = true,
    ) = TouchSimulator(
      type = GestureType.MULTI_TOUCH,
      points = points,
      loop = loop,
    )
  }
}

/**
 * 操作模拟 Composable (Touch simulator Composable).
 *
 * 渲染:
 *  - 手指圆点 (在当前时间点的位置)
 *  - 拖拽轨迹 (从 startPoint 到 currentPoint)
 *  - 点击波纹 (TAP 时)
 *  - 长按波纹 (LONG_PRESS 时持续扩散)
 *
 * 动画:
 *  - 用 Coroutine delay 推进时间
 *  - 手指位置通过线性插值在路径点之间平滑移动
 *  - 循环: 播放完后等 loopDelayMs 毫秒再重播
 */
@Composable
fun TouchSimulatorOverlay(
  simulator: TouchSimulator,
  modifier: Modifier = Modifier,
  onComplete: (() -> Unit)? = null,
) {
  // 推进动画时间 (0..durationMs)
  var currentTime by remember { mutableStateOf(0) }
  val isRunning = simulator.loop || currentTime < simulator.durationMs

  LaunchedEffect(simulator, isRunning) {
    if (!isRunning) return@LaunchedEffect
    val startTime = System.currentTimeMillis() - currentTime
    while (true) {
      val now = System.currentTimeMillis() - startTime
      currentTime = if (simulator.loop) {
        if (now >= simulator.durationMs + simulator.loopDelayMs) {
          onComplete?.invoke()
          // 重置
          delay(16)
          0
        } else {
          now.toInt().coerceAtMost(simulator.durationMs)
        }
      } else {
        if (now >= simulator.durationMs) {
          currentTime = simulator.durationMs
          onComplete?.invoke()
          break
        } else {
          now.toInt()
        }
      }
      delay(16)
    }
  }

  // 当前手指位置
  val currentPoint = remember(currentTime, simulator) {
    interpolatePath(simulator.points, currentTime.toFloat())
  }

  // 手指推进进度 (0..1)
  val progress = if (simulator.durationMs > 0) {
    (currentTime.toFloat() / simulator.durationMs).coerceIn(0f, 1f)
  } else 0f

  // 涟漪扩散 (TAP / LONG_PRESS)
  val rippleExpand = if (simulator.type == GestureType.TAP) {
    progress
  } else if (simulator.type == GestureType.LONG_PRESS) {
    // 长按涟漪: 持续 0..1 循环
    ((currentTime % 800L) / 800f).coerceIn(0f, 1f)
  } else null

  // 内部高光闪烁 (LONG_PRESS 时)
  val pulseAlpha = if (simulator.type == GestureType.LONG_PRESS) {
    val transition = rememberInfiniteTransition(label = "longpress-pulse")
    val a by transition.animateFloat(
      initialValue = 0.5f,
      targetValue = 0.9f,
      animationSpec = infiniteRepeatable(
        animation = tween(600, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "longpress-alpha",
    )
    a
  } else 0.8f

  Canvas(modifier = modifier.fillMaxSize()) {
    val fingerRadius = simulator.fingerSize.toPx() / 2f
    val pointer = Offset(currentPoint.x, currentPoint.y)

    // 1. 拖拽轨迹 (虚线)
    if (simulator.showTrail && simulator.points.size > 1) {
      val trailPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(simulator.startPoint.x, simulator.startPoint.y)
        for (i in 1 until simulator.points.size) {
          val p = simulator.points[i]
          val t = p.timeMs.toFloat()
          if (t <= currentTime) {
            lineTo(p.x, p.y)
          } else {
            // 在当前未到的点之间插值
            val prev = simulator.points[i - 1]
            val segmentT = (currentTime - prev.timeMs).toFloat() /
                (t - prev.timeMs).coerceAtLeast(1f)
            val ix = prev.x + (p.x - prev.x) * segmentT.coerceIn(0f, 1f)
            val iy = prev.y + (p.y - prev.y) * segmentT.coerceIn(0f, 1f)
            lineTo(ix, iy)
            break
          }
        }
      }
      drawPath(
        path = trailPath,
        color = simulator.trailColor,
        style = Stroke(
          width = 6f,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f),
        ),
      )
    }

    // 2. 点击 / 长按波纹
    if (simulator.showRipple && rippleExpand != null) {
      val maxRadius = fingerRadius * 3f
      val radius = fingerRadius + (maxRadius - fingerRadius) * rippleExpand
      val alpha = (1f - rippleExpand) * 0.7f
      drawCircle(
        color = simulator.rippleColor.copy(alpha = alpha),
        radius = radius,
        center = pointer,
        style = Stroke(width = 3f),
      )
      // 第二层波纹 (TAP 时)
      if (simulator.type == GestureType.TAP) {
        val r2 = fingerRadius + (maxRadius * 0.7f - fingerRadius) * rippleExpand
        drawCircle(
          color = simulator.rippleColor.copy(alpha = alpha * 0.5f),
          radius = r2,
          center = pointer,
          style = Stroke(width = 2f),
        )
      }
    }

    // 3. 手指圆点
    if (simulator.showFinger) {
      // 外圈光晕
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            simulator.fingerColor.copy(alpha = pulseAlpha * 0.4f),
            simulator.fingerColor.copy(alpha = 0f),
          ),
          center = pointer,
          radius = fingerRadius * 1.5f,
        ),
        radius = fingerRadius * 1.5f,
        center = pointer,
      )
      // 内圈白色实心
      drawCircle(
        color = simulator.fingerColor,
        radius = fingerRadius * 0.6f,
        center = pointer,
      )
      // 高光
      drawCircle(
        color = simulator.fingerColor.copy(alpha = 0.8f),
        radius = fingerRadius * 0.3f,
        center = Offset(pointer.x - fingerRadius * 0.15f, pointer.y - fingerRadius * 0.15f),
      )
    }
  }
}

/**
 * 在给定时间点对路径进行线性插值.
 */
private fun interpolatePath(points: List<PathPoint>, t: Float): PathPoint {
  if (t <= points.first().timeMs) return points.first()
  if (t >= points.last().timeMs) return points.last()
  for (i in 1 until points.size) {
    val cur = points[i]
    val prev = points[i - 1]
    if (t in prev.timeMs.toFloat()..cur.timeMs.toFloat()) {
      val span = (cur.timeMs - prev.timeMs).coerceAtLeast(1).toFloat()
      val segmentT = (t - prev.timeMs) / span
      return PathPoint(
        x = prev.x + (cur.x - prev.x) * segmentT,
        y = prev.y + (cur.y - prev.y) * segmentT,
        timeMs = t.toInt(),
        pressure = prev.pressure + (cur.pressure - prev.pressure) * segmentT,
      )
    }
  }
  return points.last()
}

/**
 * 计算两个 PathPoint 之间的欧几里得距离.
 */
fun pathLength(points: List<PathPoint>): Float {
  if (points.size < 2) return 0f
  var total = 0f
  for (i in 1 until points.size) {
    total += hypot(
      (points[i].x - points[i - 1].x).toDouble(),
      (points[i].y - points[i - 1].y).toDouble(),
    ).toFloat()
  }
  return total
}
