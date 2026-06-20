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

package com.itsaky.androidide.onboarding.animation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * 内置动画规格 (Animation specs).
 *
 * 设计参考 dribbble 顶级设计师的 UX 动画:
 *  - 弹性进入 (Spring) — 高阻尼, 中低刚度, 让气泡"砰"地落位
 *  - 优雅淡出 (Tween) — 快速淡出, 不喧宾夺主
 *  - 弹性脉冲 (Spring) — 中阻尼, 高刚度, 让高亮框有节奏地呼吸
 */
object AnimationDefaults {

  /** 默认气泡进入: 弹性 (Spring) + 缩放 + 透明 + 微微下沉 */
  val BubbleEnter: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
  )

  /** 默认气泡退出: 快速淡出 + 缩小 */
  val BubbleExit: AnimationSpec<Float> = tween(
    durationMillis = 180,
  )

  /** 高亮框脉冲: 弹性呼吸, 中阻尼高刚度 */
  val HighlightPulse: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
  )

  /** 高亮框边框光晕 (缓慢) */
  val HighlightGlow: AnimationSpec<Float> = tween(
    durationMillis = 1200,
  )

  /** 位置过渡 (气泡跟随目标移动) */
  val PositionSpring: AnimationSpec<IntOffset> = spring(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
  )

  /** 默认气泡淡入 (透明度) */
  val AlphaTween: AnimationSpec<Float> = tween(
    durationMillis = 280,
  )

  /** 操作模拟手指: 移动弹性 */
  val TouchMove: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
  )
}
