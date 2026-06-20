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
 * 高亮动画 (Highlight animation).
 *
 * 设计目标: 让高亮有"呼吸" / "扫描" / "旋转" / "波动" 等动态, 提升高级感与艺术感.
 *
 * 6 种内置动画:
 *  - [None] 无动画
 *  - [Pulse] 脉冲 (默认)
 *  - [Rotate] 旋转 (虚线旋转)
 *  - [Breathe] 呼吸 (缓慢淡入淡出)
 *  - [Scan] 扫描 (从左到右的光线)
 *  - [Wave] 波动 (径向波纹)
 *  - [Shimmer] 微光 (边框闪烁)
 */
sealed class HighlightAnimation {

  data object None : HighlightAnimation()

  data class Pulse(
    val durationMs: Int = 1400,
    val minAlpha: Float = 0.3f,
    val maxAlpha: Float = 0.9f,
    val minScale: Float = 0.95f,
    val maxScale: Float = 1.05f,
  ) : HighlightAnimation()

  /** 虚线沿边框旋转 (适合虚线 / 点线主题). */
  data class Rotate(
    val durationMs: Int = 4000,
    val direction: Direction = Direction.Clockwise,
  ) : HighlightAnimation() {
    enum class Direction { Clockwise, CounterClockwise }
  }

  data class Breathe(
    val durationMs: Int = 2000,
    val minAlpha: Float = 0.4f,
    val maxAlpha: Float = 1.0f,
  ) : HighlightAnimation()

  /** 扫描光线: 一道亮光从左到右扫过 target 区域. */
  data class Scan(
    val durationMs: Int = 1800,
    val lineWidth: Dp = 2.dp,
    val lineColor: Color = Color(0xFFFFFFFF),
    val lineAlpha: Float = 0.7f,
  ) : HighlightAnimation()

  /** 波动: 从 target 中心向外扩散的波纹. */
  data class Wave(
    val durationMs: Int = 2200,
    val waveCount: Int = 2,                    // 同时几个波纹
    val maxRadiusMultiplier: Float = 1.6f,     // 最大半径倍数
    val waveColor: Color = Color(0xFFFFFFFF),
    val waveAlpha: Float = 0.5f,
  ) : HighlightAnimation()

  /** 微光: 边框颜色循环变化 (像金属反光). */
  data class Shimmer(
    val durationMs: Int = 1600,
    val baseColor: Color = Color(0xFFFFFFFF),
    val highlightColor: Color = Color(0xFFFFD54F),
  ) : HighlightAnimation()

  companion object {
    val Default: HighlightAnimation = Pulse()
  }
}
