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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import com.itsaky.androidide.onboarding.bubble.BubbleContent
import com.itsaky.androidide.onboarding.bubble.BubbleShape
import com.itsaky.androidide.onboarding.bubble.BubbleStyle
import com.itsaky.androidide.onboarding.highlight.HighlightStyle
import com.itsaky.androidide.onboarding.simulation.TouchSimulator
import kotlinx.coroutines.delay

/**
 * 气泡位置 (Bubble placement).
 *
 * 控制气泡相对于 target 的位置:
 *  - Auto: 自动选择 (默认) — 优先上方, 不够空间则下方
 *  - Above / Below / Left / Right: 固定位置
 *  - TopCenter / BottomCenter: 屏幕顶部 / 底部居中
 *  - Custom: 自定义 x, y
 */
@Immutable
sealed class BubblePlacement {
  data object Auto : BubblePlacement()
  data object Above : BubblePlacement()
  data object Below : BubblePlacement()
  data object Left : BubblePlacement()
  data object Right : BubblePlacement()
  data object TopCenter : BubblePlacement()
  data object BottomCenter : BubblePlacement()
  data class Custom(val x: Float, val y: Float) : BubblePlacement()
}

/**
 * 引导步骤 (Onboarding step).
 *
 * 描述一次引导中的一个步骤:
 *  - 目标区域 (可空, 表示无目标)
 *  - 气泡内容 + 样式 + 形状 + 位置
 *  - 可选的高亮样式 (默认继承 OnboardingGuide 的全局样式)
 *  - 可选的操作模拟 (手指动画)
 *  - 触发推进的事件 (默认点击气泡 = 下一步)
 *  - 持续时间 (null = 永久等待用户操作)
 *  - 唯一 id (用于状态管理)
 *
 * @param id 步骤唯一标识
 * @param content 气泡内容 (主标题 + 副标题 + 可选图标)
 * @param targetRect 目标区域 (可空)
 * @param bubbleShape 气泡形状
 * @param bubbleStyle 气泡样式
 * @param bubblePlacement 气泡位置
 * @param highlightStyle 高亮样式 (null = 继承全局)
 * @param touchSimulator 操作模拟 (null = 不模拟)
 * @param autoAdvanceMs 自动推进延迟 (ms, null = 等待用户操作)
 * @param onStepShown 步骤展示回调
 * @param onStepDismissed 步骤消失回调
 */
@Immutable
data class OnboardingStep(
  val id: String,
  val content: BubbleContent,
  val targetRect: Rect? = null,
  val bubbleShape: BubbleShape = BubbleShape.Default,
  val bubbleStyle: BubbleStyle = BubbleStyle.Default,
  val bubblePlacement: BubblePlacement = BubblePlacement.Auto,
  val highlightStyle: HighlightStyle? = null,
  val touchSimulator: TouchSimulator? = null,
  val autoAdvanceMs: Long? = null,
  val onStepShown: (() -> Unit)? = null,
  val onStepDismissed: (() -> Unit)? = null,
) {
  companion object {
    /**
     * 简单创建步骤 (最少参数).
     */
    fun simple(
      id: String,
      title: String,
      subtitle: String = "",
      targetRect: Rect? = null,
    ) = OnboardingStep(
      id = id,
      content = BubbleContent(title = title, subtitle = subtitle),
      targetRect = targetRect,
    )

    /**
     * 创建一个引导操作手势的步骤 (例如 "上滑打开底部抽屉").
     */
    fun withGesture(
      id: String,
      title: String,
      subtitle: String = "",
      targetRect: Rect? = null,
      touchSimulator: TouchSimulator,
    ) = OnboardingStep(
      id = id,
      content = BubbleContent(title = title, subtitle = subtitle),
      targetRect = targetRect,
      touchSimulator = touchSimulator,
    )
  }
}

/**
 * 引导控制器 (Onboarding controller).
 *
 * 状态机:
 *  - Idle (未启动)
 *  - Playing (正在播放某一步)
 *  - Finished (全部完成)
 *  - Paused (暂停)
 *
 * 支持:
 *  - next(): 下一步
 *  - previous(): 上一步
 *  - skip(): 跳过全部
 *  - finish(): 立即完成
 *  - goto(id): 跳到指定 id
 *  - restart(): 重新开始
 */
class OnboardingController internal constructor(
  val steps: List<OnboardingStep>,
  val config: OnboardingConfig,
) {
  private val _currentIndex = mutableIntStateOf(-1)
  val currentIndex: Int get() = _currentIndex.intValue

  private val _isPlaying = mutableStateOf(false)
  val isPlaying: Boolean get() = _isPlaying.value

  val currentStep: OnboardingStep?
    get() = steps.getOrNull(_currentIndex.intValue)

  val isFirstStep: Boolean get() = _currentIndex.intValue == 0
  val isLastStep: Boolean get() = _currentIndex.intValue == steps.size - 1
  val isFinished: Boolean get() = _currentIndex.intValue >= steps.size

  /** 当前进度 (0..1) */
  val progress: Float
    get() = if (steps.isEmpty()) 1f
    else ((_currentIndex.intValue + 1).toFloat() / steps.size).coerceIn(0f, 1f)

  /** 启动引导 */
  fun start() {
    if (steps.isEmpty()) return
    _currentIndex.intValue = 0
    _isPlaying.value = true
    currentStep?.onStepShown?.invoke()
  }

  /** 下一步 */
  fun next() {
    if (isFinished) return
    currentStep?.onStepDismissed?.invoke()
    val newIndex = _currentIndex.intValue + 1
    if (newIndex >= steps.size) {
      _currentIndex.intValue = steps.size
      _isPlaying.value = false
      config.onComplete?.invoke()
    } else {
      _currentIndex.intValue = newIndex
      steps[newIndex].onStepShown?.invoke()
    }
  }

  /** 上一步 */
  fun previous() {
    if (_currentIndex.intValue <= 0) return
    currentStep?.onStepDismissed?.invoke()
    val newIndex = _currentIndex.intValue - 1
    _currentIndex.intValue = newIndex
    steps[newIndex].onStepShown?.invoke()
  }

  /** 跳过 (立即结束) */
  fun skip() {
    if (!_isPlaying.value) return
    _currentIndex.intValue = steps.size
    _isPlaying.value = false
    config.onSkipped?.invoke()
  }

  /** 跳到指定 id 的步骤 */
  fun goto(id: String) {
    val newIndex = steps.indexOfFirst { it.id == id }
    if (newIndex < 0) return
    currentStep?.onStepDismissed?.invoke()
    _currentIndex.intValue = newIndex
    steps[newIndex].onStepShown?.invoke()
  }

  /** 重新开始 */
  fun restart() {
    _currentIndex.intValue = 0
    _isPlaying.value = true
    currentStep?.onStepShown?.invoke()
  }

  /** 暂停 */
  fun pause() {
    _isPlaying.value = false
  }

  /** 恢复 */
  fun resume() {
    if (_currentIndex.intValue in 0 until steps.size) {
      _isPlaying.value = true
    }
  }
}

/**
 * 引导全局配置 (Onboarding config).
 */
data class OnboardingConfig(
  val highlightStyle: HighlightStyle = HighlightStyle.Default,
  val onComplete: (() -> Unit)? = null,
  val onSkipped: (() -> Unit)? = null,
  val cancellable: Boolean = true,                  // 点击空白是否跳过
  val showProgressIndicator: Boolean = true,        // 是否显示进度
  val pauseBetweenStepsMs: Long = 320,              // 步骤间暂停
)

/**
 * 记住一个引导控制器 (Compose helper).
 */
@Composable
fun rememberOnboardingController(
  steps: List<OnboardingStep>,
  config: OnboardingConfig = OnboardingConfig(),
): OnboardingController {
  return remember(steps, config) { OnboardingController(steps, config) }
}

/**
 * 在 Composable 中启动引导 (当 controller 第一次被 remember 时).
 */
@Composable
fun AutoStartOnboarding(controller: OnboardingController) {
  LaunchedEffect(controller) {
    if (!controller.isPlaying && !controller.isFinished) {
      controller.start()
    }
  }
}

/**
 * 监听当前步骤的 autoAdvanceMs, 自动推进.
 */
@Composable
fun AutoAdvanceHandler(
  controller: OnboardingController,
  onAdvance: () -> Unit,
) {
  val currentStep = controller.currentStep
  LaunchedEffect(currentStep?.id) {
    val autoMs = currentStep?.autoAdvanceMs ?: return@LaunchedEffect
    delay(autoMs)
    if (controller.currentStep?.id == currentStep.id) {
      onAdvance()
    }
  }
}
