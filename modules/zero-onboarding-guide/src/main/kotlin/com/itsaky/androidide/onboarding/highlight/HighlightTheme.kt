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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 高亮视觉主题 (Highlight visual theme).
 *
 * 设计目标: 提供不同视觉风格, 让"被框选"这件事在不同场景下都有合适的表达.
 * 不同的 UI 控件用不同颜色 / 形状 / 动画 / 主题, 避免单调.
 *
 * 8 种内置主题:
 *  - [Solid] 实色 (默认, 偏白半透明边框)
 *  - [Dashed] 虚线
 *  - [Dotted] 点线
 *  - [Neon] 霓虹 (发光)
 *  - [Tape] 胶带 (四角的 L 型括号, 不闭合)
 *  - [Corners] 仅四角 (L 形装饰)
 *  - [Spotlight] 聚光 (圆形高光 + 周围暗)
 *  - [Frosted] 磨砂 (边框自身也磨砂)
 *  - [Custom] 自定义
 */
sealed class HighlightTheme {

  /** 实色边框 (默认). */
  data class Solid(
    val borderColor: Color = Color(0xFFFFFFFF),
    val borderWidth: Dp = 2.dp,
    val borderAlpha: Float = 1f,
    val scrimColor: Color = Color(0x99000000),
  ) : HighlightTheme()

  /** 虚线边框. */
  data class Dashed(
    val borderColor: Color = Color(0xFFFFFFFF),
    val borderWidth: Dp = 2.dp,
    val dashLength: Dp = 8.dp,
    val gapLength: Dp = 4.dp,
    val scrimColor: Color = Color(0x99000000),
  ) : HighlightTheme()

  /** 点线边框. */
  data class Dotted(
    val borderColor: Color = Color(0xFFFFFFFF),
    val borderWidth: Dp = 2.dp,
    val dotSpacing: Dp = 6.dp,
    val scrimColor: Color = Color(0x99000000),
  ) : HighlightTheme()

  /** 霓虹发光边框. */
  data class Neon(
    val color: Color = Color(0xFF00E5FF),
    val borderWidth: Dp = 2.dp,
    val glowRadius: Dp = 16.dp,
    val glowColor: Color = Color(0xFF00E5FF),
    val scrimColor: Color = Color(0xAA000000),
  ) : HighlightTheme()

  /** 胶带 (四角的 L 形括号, 不闭合). */
  data class Tape(
    val color: Color = Color(0xFFFFFFFF),
    val length: Dp = 24.dp,
    val thickness: Dp = 3.dp,
    val scrimColor: Color = Color(0x99000000),
  ) : HighlightTheme()

  /** 仅四角 (装饰). */
  data class Corners(
    val color: Color = Color(0xFFFFFFFF),
    val length: Dp = 16.dp,
    val thickness: Dp = 2.dp,
    val cornerRadius: Dp = 0.dp,
    val scrimColor: Color = Color(0x99000000),
  ) : HighlightTheme()

  /** 聚光 (圆形高光 + 周围暗). */
  data class Spotlight(
    val scrimColor: Color = Color(0xDD000000),
    val ringColor: Color = Color(0xFFFFFFFF),
    val ringWidth: Dp = 2.dp,
    val softEdge: Boolean = true, // 边缘是否柔化
  ) : HighlightTheme()

  /** 磨砂 (边框自身也磨砂). */
  data class Frosted(
    val borderColor: Color = Color(0xFFFFFFFF),
    val borderWidth: Dp = 1.dp,
    val scrimColor: Color = Color(0x66000000),
    val blurAlpha: Float = 0.3f,
  ) : HighlightTheme()

  /** 自定义 (高级用户). */
  data class Custom(
    val theme: HighlightTheme,
  ) : HighlightTheme()

  companion object {
    val Default: HighlightTheme = Solid()
  }
}

/**
 * 提取主题的 scrim 颜色 (背景蒙层).
 */
val HighlightTheme.scrimColorOrNull: Color?
  get() = when (this) {
    is HighlightTheme.Solid -> this.scrimColor
    is HighlightTheme.Dashed -> this.scrimColor
    is HighlightTheme.Dotted -> this.scrimColor
    is HighlightTheme.Neon -> this.scrimColor
    is HighlightTheme.Tape -> this.scrimColor
    is HighlightTheme.Corners -> this.scrimColor
    is HighlightTheme.Spotlight -> this.scrimColor
    is HighlightTheme.Frosted -> this.scrimColor
    is HighlightTheme.Custom -> this.theme.scrimColorOrNull
  }
