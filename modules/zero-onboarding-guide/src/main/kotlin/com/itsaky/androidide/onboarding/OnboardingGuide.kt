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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import com.itsaky.androidide.onboarding.bubble.BubbleContent
import com.itsaky.androidide.onboarding.BubblePlacement
import com.itsaky.androidide.onboarding.bubble.BubbleShape
import com.itsaky.androidide.onboarding.bubble.BubbleStyle
import com.itsaky.androidide.onboarding.highlight.HighlightAnimation
import com.itsaky.androidide.onboarding.highlight.HighlightShape
import com.itsaky.androidide.onboarding.highlight.HighlightStyle
import com.itsaky.androidide.onboarding.highlight.HighlightTheme
import com.itsaky.androidide.onboarding.simulation.TouchSimulator

/**
 * OnboardingGuide — 引导 API 门面.
 *
 * 提供简洁 DSL, 快速构建引导步骤:
 *
 * ## 基本用法
 * ```
 * OnboardingGuide(
 *   steps = listOf(
 *     OnboardingGuide.step(
 *       id = "welcome",
 *       title = "欢迎使用 ZeroStudio!",
 *       subtitle = "这是一个引导示例",
 *     ),
 *   ),
 *   onComplete = { /* ... */ },
 * )
 * ```
 *
 * ## 完整 DSL
 * ```
 * val steps = listOf(
 *   OnboardingGuide.step("intro", "欢迎") { /* 高级配置 */ },
 *   OnboardingGuide.step("feature", "试试这个功能") {
 *     targetRect = someRect
 *     bubbleShape = BubbleShape.WideCard()
 *     touchSimulator = TouchSimulator.tap(...)
 *   },
 * )
 * ```
 */
object OnboardingGuide {

  /**
   * 创建一个引导步骤 (最简单形式).
   */
  fun step(
    id: String,
    title: String,
    subtitle: String = "",
    icon: ImageVector? = null,
    targetRect: Rect? = null,
  ): OnboardingStep = OnboardingStep(
    id = id,
    content = BubbleContent(
      title = title,
      subtitle = subtitle,
      icon = icon,
    ),
    targetRect = targetRect,
  )

  /**
   * 创建一个带触摸模拟的引导步骤.
   */
  fun stepWithGesture(
    id: String,
    title: String,
    subtitle: String = "",
    targetRect: Rect? = null,
    touchSimulator: TouchSimulator,
  ): OnboardingStep = OnboardingStep(
    id = id,
    content = BubbleContent(title = title, subtitle = subtitle),
    targetRect = targetRect,
    touchSimulator = touchSimulator,
  )

  /**
   * 完整 DSL: 创建一个可深度自定义的引导步骤.
   *
   * ```
   * OnboardingGuide.customStep("step1") {
   *   content = BubbleContent.full("标题", "副标题", icon)
   *   targetRect = myRect
   *   bubbleShape = BubbleShape.Hexagon()
   *   bubbleStyle = BubbleStyle.WideCard
   *   bubblePlacement = BubblePlacement.Above
   *   highlightStyle = HighlightStyle.Strong
   *   touchSimulator = TouchSimulator.tap(x, y)
   * }
   * ```
   */
  @Composable
  fun customStep(
    id: String,
    configure: StepBuilder.() -> Unit = {},
  ): OnboardingStep {
    val builder = remember(id) { StepBuilder(id) }
    builder.configure()
    return builder.build()
  }

  /**
   * 引导步骤构建器.
   */
  class StepBuilder internal constructor(val id: String) {
    var content: BubbleContent = BubbleContent(title = "")
    var targetRect: Rect? = null
    var target: OnboardingTarget? = null
    var bubbleShape: BubbleShape = BubbleShape.Default
    var bubbleStyle: BubbleStyle = BubbleStyle.Default
    var bubblePlacement: BubblePlacement = BubblePlacement.Auto
    var highlightShape: HighlightShape = HighlightShape.Auto
    var highlightTheme: HighlightTheme = HighlightTheme.Default
    var highlightAnimation: HighlightAnimation = HighlightAnimation.Default
    var highlightStyle: HighlightStyle? = null
    var touchSimulator: TouchSimulator? = null
    var autoAdvanceMs: Long? = null
    var onStepShown: (() -> Unit)? = null
    var onStepDismissed: (() -> Unit)? = null

    internal fun build(): OnboardingStep = OnboardingStep(
      id = id,
      content = content,
      targetRect = targetRect,
      target = target,
      bubbleShape = bubbleShape,
      bubbleStyle = bubbleStyle,
      bubblePlacement = bubblePlacement,
      highlightShape = highlightShape,
      highlightTheme = highlightTheme,
      highlightAnimation = highlightAnimation,
      highlightStyle = highlightStyle,
      touchSimulator = touchSimulator,
      autoAdvanceMs = autoAdvanceMs,
      onStepShown = onStepShown,
      onStepDismissed = onStepDismissed,
    )
  }
}

/**
 * 启动引导的便捷 Composable.
 *
 * ```
 * LaunchOnboarding(steps = listOf(...))
 * ```
 */
@Composable
fun LaunchOnboarding(
  steps: List<OnboardingStep>,
  config: OnboardingConfig = OnboardingConfig(),
  autoStart: Boolean = true,
  onControllerReady: ((OnboardingController) -> Unit)? = null,
): OnboardingController {
  val controller = rememberOnboardingController(steps, config)
  LaunchedEffect(controller) {
    onControllerReady?.invoke(controller)
    if (autoStart && !controller.isPlaying && !controller.isFinished) {
      controller.start()
    }
  }
  return controller
}
