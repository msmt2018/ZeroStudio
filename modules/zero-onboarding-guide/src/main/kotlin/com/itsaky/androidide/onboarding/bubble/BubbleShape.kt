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

package com.itsaky.androidide.onboarding.bubble

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection as UnitLayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 内置气泡形状库 (Built-in bubble shape library).
 *
 * 设计与排版:
 *  - 包含 10 种常见 / 常用 / 高级气泡遮罩形状
 *  - 每个 shape 是一个 Compose [Shape] 实现, 可直接传给 [androidx.compose.foundation.background]
 *  - 也支持自定义 shape: 调用 [BubbleShape.Custom] 传入任意 [Shape]
 *
 * 设计原则 (参考 dribbble 顶级设计师的 UX 设计风格):
 *  - 大圆角柔和 (RoundedRectangle, WideCard) — 主流磨砂玻璃气泡
 *  - 极端形状 (Hexagon, Diamond) — 装饰性高, 适合引导类强调
 *  - 长条 (StretchedBar) — 顶部 / 底部通用提示
 *  - 圆头胶囊 (Pill) — 一行紧凑文本标签
 *  - 圆 (Circle) — 小气泡 / 头像伴随
 *  - 圆角正方形 (Square) — 网格布局中平衡布局
 *  - 带尖角气泡 (SpeechBubble) — 经典聊天气泡
 *  - Tabbed — 底部带突出指示的卡片
 */
sealed class BubbleShape {

  /** 气泡的最终 Compose [Shape]. */
  abstract val shape: Shape

  // ============================================================
  // 1. 圆角矩形 (最常见, 默认)
  // ============================================================
  data class RoundedRectangle(
    val cornerRadius: Dp = 20.dp,
  ) : BubbleShape() {
    override val shape: Shape = RoundedCornerShape(cornerRadius)
  }

  // ============================================================
  // 2. 圆角正方形 (Square - 圆角处理)
  // ============================================================
  data class Square(
    val cornerRadius: Dp = 24.dp,
  ) : BubbleShape() {
    override val shape: Shape = RoundedCornerShape(cornerRadius)
  }

  // ============================================================
  // 3. 胶囊 (Pill) — 长条圆角
  // ============================================================
  data class Pill(
    val cornerRadius: Dp = 28.dp,
  ) : BubbleShape() {
    override val shape: Shape = RoundedCornerShape(cornerRadius)
  }

  // ============================================================
  // 4. 圆形 (Circle)
  // ============================================================
  data object Circle : BubbleShape() {
    override val shape: Shape = CircleShape
  }

  // ============================================================
  // 5. 大圆角宽卡片 (WideCard) — 适合双行布局, 顶图 + 文案
  // ============================================================
  data class WideCard(
    val cornerRadius: Dp = 32.dp,
  ) : BubbleShape() {
    override val shape: Shape = RoundedCornerShape(cornerRadius)
  }

  // ============================================================
  // 6. 长条 (StretchedBar) — 顶部 / 底部通用提示条
  // ============================================================
  data class StretchedBar(
    val cornerRadius: Dp = 12.dp,
  ) : BubbleShape() {
    override val shape: Shape = RoundedCornerShape(cornerRadius)
  }

  // ============================================================
  // 7. 六边形 (Hexagon) — 高级装饰性气泡
  // ============================================================
  data class Hexagon(
    val inset: Dp = 8.dp,
  ) : BubbleShape() {
    override val shape: Shape = HexagonShape(inset)
  }

  // ============================================================
  // 8. 菱形 (Diamond) — 高级装饰性气泡
  // ============================================================
  data class Diamond(
    val inset: Dp = 4.dp,
  ) : BubbleShape() {
    override val shape: Shape = DiamondShape(inset)
  }

  // ============================================================
  // 9. 聊天气泡 (SpeechBubble) — 经典气泡带尖角
  // ============================================================
  data class SpeechBubble(
    val cornerRadius: Dp = 20.dp,
    val tailSize: Dp = 12.dp,
    /** 尖角位置, 默认在底部居中向下 */
    val tailPosition: TailPosition = TailPosition.BottomCenter,
  ) : BubbleShape() {
    override val shape: Shape = SpeechBubbleShape(cornerRadius, tailSize, tailPosition)
  }

  /** 聊天气泡尖角方向 */
  enum class TailPosition { BottomCenter, TopCenter, LeftCenter, RightCenter }

  // ============================================================
  // 10. Tabbed (带底部凸出指示器) — 适合菜单引导
  // ============================================================
  data class Tabbed(
    val cornerRadius: Dp = 16.dp,
    val indicatorWidth: Dp = 36.dp,
    val indicatorHeight: Dp = 4.dp,
  ) : BubbleShape() {
    override val shape: Shape = TabbedShape(cornerRadius, indicatorWidth, indicatorHeight)
  }

  // ============================================================
  // 11. 自定义形状 (Custom)
  // ============================================================
  data class Custom(
    override val shape: Shape,
  ) : BubbleShape()

  companion object {
    /** 默认气泡形状: 圆角矩形 */
    val Default: BubbleShape = RoundedRectangle()
  }
}

// =============================================================================
// 私有 Shape 实现 — 直接实现 Shape 接口, 通过 createOutline(size, ld, density)
// 拿到 density, 这样可以调用 Dp.toPx() 把 dp 转换成 px.
// =============================================================================

private class HexagonShape(private val inset: Dp) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: Density,
  ): Outline = Outline.Generic(Path().apply {
    with(density) {
      val ix = inset.toPx().coerceAtMost(min(size.width, size.height) / 4f)
      val iy = inset.toPx().coerceAtMost(min(size.width, size.height) / 4f)
      // 六边形: 左右两侧扁平, 上下平直
      moveTo(ix, 0f)
      lineTo(size.width - ix, 0f)
      lineTo(size.width, size.height / 2f)
      lineTo(size.width - ix, size.height)
      lineTo(ix, size.height)
      lineTo(0f, size.height / 2f)
      close()
    }
  })
}

private class DiamondShape(private val inset: Dp) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: Density,
  ): Outline = Outline.Generic(Path().apply {
    with(density) {
      val ix = inset.toPx().coerceAtMost(size.width / 4f)
      val iy = inset.toPx().coerceAtMost(size.height / 4f)
      moveTo(size.width / 2f, iy)
      lineTo(size.width - ix, size.height / 2f)
      lineTo(size.width / 2f, size.height - iy)
      lineTo(ix, size.height / 2f)
      close()
    }
  })
}

private class SpeechBubbleShape(
  private val cornerRadius: Dp,
  private val tailSize: Dp,
  private val tailPosition: BubbleShape.TailPosition,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: Density,
  ): Outline = Outline.Generic(Path().apply {
    with(density) {
      val w = size.width
      val h = size.height
      val r = cornerRadius.toPx().coerceAtMost(min(w, h) / 2f)
      val t = tailSize.toPx()

      when (tailPosition) {
        BubbleShape.TailPosition.BottomCenter -> {
          // 圆角矩形 + 底部居中向下尖角
          addRoundRect(
            RoundRect(
              left = 0f, top = 0f, right = w, bottom = h - t,
              cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
            )
          )
          moveTo(w / 2f - t, h - t)
          lineTo(w / 2f, h)
          lineTo(w / 2f + t, h - t)
          close()
        }
        BubbleShape.TailPosition.TopCenter -> {
          addRoundRect(
            RoundRect(
              left = 0f, top = t, right = w, bottom = h,
              cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
            )
          )
          moveTo(w / 2f - t, t)
          lineTo(w / 2f, 0f)
          lineTo(w / 2f + t, t)
          close()
        }
        BubbleShape.TailPosition.LeftCenter -> {
          addRoundRect(
            RoundRect(
              left = t, top = 0f, right = w, bottom = h,
              cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
            )
          )
          moveTo(t, h / 2f - t)
          lineTo(0f, h / 2f)
          lineTo(t, h / 2f + t)
          close()
        }
        BubbleShape.TailPosition.RightCenter -> {
          addRoundRect(
            RoundRect(
              left = 0f, top = 0f, right = w - t, bottom = h,
              cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
            )
          )
          moveTo(w - t, h / 2f - t)
          lineTo(w, h / 2f)
          lineTo(w - t, h / 2f + t)
          close()
        }
      }
    }
  })
}

private class TabbedShape(
  private val cornerRadius: Dp,
  private val indicatorWidth: Dp,
  private val indicatorHeight: Dp,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    density: Density,
  ): Outline = Outline.Generic(Path().apply {
    with(density) {
      val w = size.width
      val h = size.height
      val r = cornerRadius.toPx().coerceAtMost(min(w, h) / 2f)
      val iw = indicatorWidth.toPx().coerceAtMost(w / 2f)
      val ih = indicatorHeight.toPx()

      // 上方圆角矩形 + 底部居中凸出
      addRoundRect(
        RoundRect(
          left = 0f, top = 0f, right = w, bottom = h,
          cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
        )
      )
      // 底部凸出指示器 (半圆)
      moveTo(w / 2f - iw, h)
      cubicTo(
        w / 2f - iw, h + ih,
        w / 2f + iw, h + ih,
        w / 2f + iw, h
      )
      close()
    }
  })
}
