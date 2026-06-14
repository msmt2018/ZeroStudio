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
package com.itsaky.androidide.perf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 折线图 (sparkline) Composable (PR #5/5).
 *
 * 简单 Canvas 自绘, 不引外部 chart 库. 单条线 + 可选第二条 (e.g. PSS total + native).
 *
 * 用法:
 * ```kotlin
 * Sparkline(
 *   values = listOf(60, 58, 55, ...),
 *   height = 80.dp,
 *   color = MaterialTheme.colorScheme.primary,
 * )
 * ```
 *
 * @author android_zero
 */
@Composable
fun Sparkline(
    values: List<Number>,
    height: Dp = 80.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    fillAlpha: Float = 0.18f,
) {
  Box(modifier = Modifier.fillMaxSize().height(height).padding(8.dp)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      if (values.size < 2) return@Canvas
      val w = size.width
      val h = size.height
      val max = values.maxOf { it.toDouble() }.coerceAtLeast(1.0)
      val min = values.minOf { it.toDouble() }.coerceAtMost(0.0)
      val range = (max - min).coerceAtLeast(1.0)
      val stepX = w / (values.size - 1)

      // 折线
      val path = Path()
      values.forEachIndexed { i, v ->
        val x = i * stepX
        val y = h - ((v.toDouble() - min) / range * h).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      drawPath(path = path, color = color, style = Stroke(width = 3f))

      // 填充
      val fillPath = Path().apply {
        addPath(path)
        lineTo(w, h)
        lineTo(0f, h)
        close()
      }
      drawPath(path = fillPath, color = color.copy(alpha = fillAlpha))
    }
  }
}

/**
 * 双线 sparkline (e.g. total PSS + native PSS).
 */
@Composable
fun DualSparkline(
    primary: List<Number>,
    secondary: List<Number>,
    height: Dp = 80.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
) {
  Box(modifier = Modifier.fillMaxSize().height(height).padding(8.dp)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      if (primary.size < 2 && secondary.size < 2) return@Canvas
      val w = size.width
      val h = size.height
      val all = primary + secondary
      val max = all.maxOf { it.toDouble() }.coerceAtLeast(1.0)
      val min = all.minOf { it.toDouble() }.coerceAtMost(0.0)
      val range = (max - min).coerceAtLeast(1.0)

      fun drawLine(values: List<Number>, color: Color, width: Float) {
        if (values.size < 2) return
        val stepX = w / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
          val x = i * stepX
          val y = h - ((v.toDouble() - min) / range * h).toFloat()
          if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = width))
      }

      drawLine(secondary, secondaryColor, 2f)
      drawLine(primary, primaryColor, 3f)
    }
  }
}
