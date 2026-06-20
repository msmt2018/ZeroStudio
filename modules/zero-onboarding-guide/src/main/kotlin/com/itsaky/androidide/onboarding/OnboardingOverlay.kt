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

package com.itsaky.androidide.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.onboarding.bubble.GuideBubble
import com.itsaky.androidide.onboarding.highlight.HighlightFrame
import com.itsaky.androidide.onboarding.highlight.HighlightStyle
import com.itsaky.androidide.onboarding.simulation.TouchSimulatorOverlay
import kotlin.math.roundToInt

/**
 * 引导根容器 (Onboarding overlay root).
 *
 * 这是整个引导系统的根 Composable. 负责:
 *  - 渲染全屏遮罩 + 高亮框
 *  - 渲染气泡 (根据 currentStep)
 *  - 渲染操作模拟 (如果 step 指定)
 *  - 处理点击事件 (下一步 / 跳过)
 *
 * 用法:
 * ```
 * val controller = rememberOnboardingController(steps)
 * Box(modifier = Modifier.fillMaxSize()) {
 *   YourContent()
 *   OnboardingOverlay(controller = controller)
 * }
 * ```
 *
 * @param controller 引导控制器
 * @param modifier 外部 modifier
 */
@Composable
fun OnboardingOverlay(
  controller: OnboardingController,
  modifier: Modifier = Modifier,
) {
  val currentStep = controller.currentStep
  if (currentStep == null) return

  val config = controller.config
  val highlightStyle = currentStep.highlightStyle ?: config.highlightStyle

  val interactionSource = remember { MutableInteractionSource() }

  Box(modifier = modifier.fillMaxSize()) {
    // 1. 高亮框 (始终在最底层)
    HighlightFrame(
      targetRect = currentStep.targetRect,
      style = highlightStyle,
      shape = currentStep.bubbleShape,
    )

    // 2. 点击外部区域 (可取消)
    if (config.cancellable) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(controller.currentStep.id) {
            // 整个 Overlay 都是点击区域
          }
          .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { controller.skip() },
          ),
      )
    }

    // 3. 操作模拟
    if (currentStep.touchSimulator != null) {
      TouchSimulatorOverlay(
        simulator = currentStep.touchSimulator,
        modifier = Modifier.fillMaxSize(),
      )
    }

    // 4. 气泡
    if (currentStep.targetRect != null) {
      // 有目标: 气泡围绕 target 定位
      PositionedBubble(
        controller = controller,
        step = currentStep,
        targetRect = currentStep.targetRect,
        onNext = { controller.next() },
        onPrevious = { controller.previous() },
      )
    } else {
      // 无目标: 气泡放在屏幕中央
      CenteredBubble(
        controller = controller,
        step = currentStep,
        onNext = { controller.next() },
        onPrevious = { controller.previous() },
      )
    }

    // 5. 进度指示器 (右下角)
    if (config.showProgressIndicator) {
      ProgressIndicator(
        current = controller.currentIndex + 1,
        total = controller.steps.size,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(24.dp),
      )
    }
  }
}

/**
 * 居中气泡 (无目标时).
 */
@Composable
private fun CenteredBubble(
  controller: OnboardingController,
  step: OnboardingStep,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    GuideBubbleWithActions(
      step = step,
      controller = controller,
      onNext = onNext,
      onPrevious = onPrevious,
    )
  }
}

/**
 * 定位气泡 (有目标时).
 *
 * 根据 [BubblePlacement] 计算气泡位置:
 *  - Auto / Above: target 上方居中
 *  - Below: target 下方居中
 *  - Left: target 左侧
 *  - Right: target 右侧
 *  - TopCenter: 屏幕顶部居中
 *  - BottomCenter: 屏幕底部居中
 *  - Custom: 自定义 x, y
 */
@Composable
private fun PositionedBubble(
  controller: OnboardingController,
  step: OnboardingStep,
  targetRect: Rect,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
) {
  val density = LocalDensity.current
  val margin = with(density) { 16.dp.toPx() }

  val offsetModifier = when (val placement = step.bubblePlacement) {
    BubblePlacement.Auto,
    BubblePlacement.Above -> Modifier.absoluteOffsetByRect(
      targetRect = targetRect,
      alignX = AlignH.Center,
      alignY = AlignV.Below,
      margin = margin,
    )
    BubblePlacement.Below -> Modifier.absoluteOffsetByRect(
      targetRect = targetRect,
      alignX = AlignH.Center,
      alignY = AlignV.Above,
      margin = margin,
    )
    BubblePlacement.Left -> Modifier.absoluteOffsetByRect(
      targetRect = targetRect,
      alignX = AlignH.RightOf,
      alignY = AlignV.Center,
      margin = margin,
    )
    BubblePlacement.Right -> Modifier.absoluteOffsetByRect(
      targetRect = targetRect,
      alignX = AlignH.LeftOf,
      alignY = AlignV.Center,
      margin = margin,
    )
    BubblePlacement.TopCenter -> Modifier
      .align(Alignment.TopCenter)
      .padding(top = 32.dp)
    BubblePlacement.BottomCenter -> Modifier
      .align(Alignment.BottomCenter)
      .padding(bottom = 32.dp)
    is BubblePlacement.Custom -> Modifier
      .offset(x = placement.x.dp, y = placement.y.dp)
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Box(modifier = offsetModifier) {
      GuideBubbleWithActions(
        step = step,
        controller = controller,
        onNext = onNext,
        onPrevious = onPrevious,
      )
    }
  }
}

/**
 * 气泡 + 操作按钮 (下一步 / 上一步).
 */
@Composable
private fun GuideBubbleWithActions(
  step: OnboardingStep,
  controller: OnboardingController,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
) {
  GuideBubble(
    content = step.content,
    shape = step.bubbleShape,
    style = step.bubbleStyle,
    actions = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!controller.isFirstStep) {
          TextButton(onClick = onPrevious) {
            Text(
              text = "← 上一步",
              color = step.bubbleStyle.subtitleColor,
              style = MaterialTheme.typography.labelMedium,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
        }

        if (controller.isLastStep) {
          TextButton(onClick = onNext) {
            Text(
              text = "完成 ✓",
              color = step.bubbleStyle.titleColor,
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
              ),
            )
          }
        } else {
          TextButton(onClick = onNext) {
            Text(
              text = "下一步 →",
              color = step.bubbleStyle.titleColor,
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
              ),
            )
          }
        }
      }
    },
  )
}

/**
 * 进度指示器 (右下角).
 */
@Composable
private fun ProgressIndicator(
  current: Int,
  total: Int,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .clip(CircleShape)
      .background(Color(0xCC000000))
      .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = "$current / $total",
      color = Color.White,
      style = MaterialTheme.typography.labelSmall,
    )
  }
}

// =============================================================================
// 位置计算辅助 (Position calculation helpers)
// =============================================================================

/** 水平对齐方式. */
private enum class AlignH { LeftOf, RightOf, Center }

/** 垂直对齐方式. */
private enum class AlignV { Above, Below, Center }

/**
 * 根据 targetRect 和对齐方式计算 Modifier (绝对像素偏移).
 *
 * 区别于标准 [Modifier.offset]: 这个 offset 用绝对像素,
 * 因为 targetRect 是绝对坐标 (来自 positionInWindow).
 */
private fun Modifier.absoluteOffsetByRect(
  targetRect: Rect,
  alignX: AlignH,
  alignY: AlignV,
  margin: Float = 0f,
): Modifier = this.offset { intSize ->
  val width = intSize.width.toFloat()
  val height = intSize.height.toFloat()
  val targetCx = targetRect.left + targetRect.width / 2f
  val targetCy = targetRect.top + targetRect.height / 2f

  val x = when (alignX) {
    AlignH.LeftOf -> targetRect.left - width - margin
    AlignH.RightOf -> targetRect.right + margin
    AlignH.Center -> targetCx - width / 2f
  }
  val y = when (alignY) {
    AlignV.Above -> targetRect.top - height - margin
    AlignV.Below -> targetRect.bottom + margin
    AlignV.Center -> targetCy - height / 2f
  }
  IntOffset(x.roundToInt(), y.roundToInt())
}
