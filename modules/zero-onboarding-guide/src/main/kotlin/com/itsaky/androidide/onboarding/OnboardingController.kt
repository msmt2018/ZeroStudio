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
import androidx.compose.ui.geometry.Size
import com.itsaky.androidide.onboarding.bubble.BubbleContent
import com.itsaky.androidide.onboarding.bubble.BubbleShape
import com.itsaky.androidide.onboarding.bubble.BubbleStyle
import com.itsaky.androidide.onboarding.highlight.HighlightShape
import com.itsaky.androidide.onboarding.highlight.HighlightStyle
import com.itsaky.androidide.onboarding.prefs.OnboardingPreferences
import com.itsaky.androidide.onboarding.simulation.TouchSimulator
import kotlinx.coroutines.delay

/**
 * 气泡位置 (Bubble placement).
 *
 * 控制气泡相对于 target 的位置:
 *  - Auto: 自动选择 (默认) — 优先选择空间最大的方向 (上 / 下 / 左 / 右)
 *  - Above / Below / Left / Right: 固定位置
 *  - TopCenter / BottomCenter: 屏幕顶部 / 底部居中
 *  - Custom: 自定义 x, y
 *
 * ## 自动选择 (Auto) 算法
 * 调用 [BubblePlacement.computeBest] 根据 target + 容器尺寸 + 气泡预估尺寸
 * 自动选择空间最大的方向. 这个算法确保气泡不会被屏幕边界或被遮罩的目标区域遮挡.
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

  companion object {
    /**
     * 自动计算最佳气泡位置.
     *
     * 算法:
     *  1. 测量 target 四周可用空间 (上, 下, 左, 右)
     *  2. 选择空间最大的方向
     *  3. 如果 4 个方向都不够, 选择距离屏幕中心最近的方向
     *
     * @param target 目标区域
     * @param container 容器尺寸 (整个引导 Overlay 的尺寸)
     * @param bubbleSize 气泡预估尺寸 (宽, 高)
     * @param margin 期望间距
     * @return 最佳位置 (Above / Below / Left / Right)
     */
    fun computeBest(
      target: Rect,
      container: Size,
      bubbleSize: Size,
      margin: Float = 16f,
    ): BubblePlacement {
      val topSpace = target.top
      val bottomSpace = container.height - target.bottom
      val leftSpace = target.left
      val rightSpace = container.width - target.right

      // 检查每个方向是否能容纳气泡
      val canTop = topSpace >= bubbleSize.height + margin
      val canBottom = bottomSpace >= bubbleSize.height + margin
      val canLeft = leftSpace >= bubbleSize.width + margin
      val canRight = rightSpace >= bubbleSize.width + margin

      // 优先选择空间最大的方向
      val spaces = listOf(
        canTop to (Above to topSpace),
        canBottom to (Below to bottomSpace),
        canLeft to (Left to leftSpace),
        canRight to (Right to rightSpace),
      ).filter { it.first }
        .map { it.second }

      if (spaces.isEmpty()) {
        // 都没有完美空间: 选最大的 (即使放不下)
        val all = listOf(
          Above to topSpace,
          Below to bottomSpace,
          Left to leftSpace,
          Right to rightSpace,
        )
        return all.maxBy { it.second }.first
      }
      return spaces.maxBy { it.second }.first
    }
  }
}

/**
 * 引导步骤 (Onboarding step).
 *
 * 描述一次引导中的一个步骤:
 *  - 目标区域 (可空, 表示无目标) — 来自 [targetRect] 或 [OnboardingTarget] 绑定
 *  - 气泡内容 + 样式 + 形状 + 位置
 *  - 可选的高亮形状 / 样式 / 主题 / 动画 (默认继承 OnboardingGuide 的全局样式)
 *  - 可选的操作模拟 (手指动画)
 *  - 触发推进的事件 (默认点击气泡 = 下一步)
 *  - 持续时间 (null = 永久等待用户操作)
 *  - 唯一 id (用于状态管理)
 *
 * @param id 步骤唯一标识
 * @param content 气泡内容 (主标题 + 副标题 + 可选图标)
 * @param targetRect 目标区域 (可空, 兼容旧 API)
 * @param target 通过 id 绑定的目标 (新 API, 与 targetRect 二选一, 优先 target)
 * @param bubbleShape 气泡形状
 * @param bubbleStyle 气泡样式
 * @param bubblePlacement 气泡位置 (Auto = 自动选择)
 * @param highlightShape 高亮形状 (默认 [HighlightShape.Auto] 自适应)
 * @param highlightTheme 高亮主题 (默认 [com.itsaky.androidide.onboarding.highlight.HighlightTheme.Default])
 * @param highlightAnimation 高亮动画
 * @param highlightStyle 旧版高亮样式 (向后兼容, 优先使用 highlightShape/Theme/Animation)
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
  val target: OnboardingTarget? = null,
  val bubbleShape: BubbleShape = BubbleShape.Default,
  val bubbleStyle: BubbleStyle = BubbleStyle.Default,
  val bubblePlacement: BubblePlacement = BubblePlacement.Auto,
  val highlightShape: HighlightShape = HighlightShape.Auto,
  val highlightTheme: com.itsaky.androidide.onboarding.highlight.HighlightTheme =
    com.itsaky.androidide.onboarding.highlight.HighlightTheme.Default,
  val highlightAnimation: com.itsaky.androidide.onboarding.highlight.HighlightAnimation =
    com.itsaky.androidide.onboarding.highlight.HighlightAnimation.Default,
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

  /** 启动引导. 如果 [OnboardingConfig.skipIfCompleted] 且持久化记录已完成, 则不会启动. */
  fun start() {
    if (steps.isEmpty()) return
    // 持久化: 已完成则不启动
    if (config.skipIfCompleted && config.guideId != null && config.preferences?.isCompleted(config.guideId) == true) {
      _isPlaying.value = false
      _currentIndex.intValue = steps.size  // 标记为 finished
      return
    }
    _currentIndex.intValue = 0
    _isPlaying.value = true
    currentStep?.onStepShown?.invoke()
  }

  /** 下一步. 最后一步会触发 onComplete 并标记持久化完成. */
  fun next() {
    if (isFinished) return
    currentStep?.onStepDismissed?.invoke()
    val newIndex = _currentIndex.intValue + 1
    if (newIndex >= steps.size) {
      _currentIndex.intValue = steps.size
      _isPlaying.value = false
      // 持久化: 标记为已完成
      markCompletedIfNeeded()
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

  /** 跳过 (立即结束). 默认不会标记为已完成. */
  fun skip() {
    if (!_isPlaying.value) return
    _currentIndex.intValue = steps.size
    _isPlaying.value = false
    config.onSkipped?.invoke()
  }

  /** 立即完成 (类似 next() 到末尾, 但不经过中间步骤). */
  fun finish() {
    if (isFinished) return
    currentStep?.onStepDismissed?.invoke()
    _currentIndex.intValue = steps.size
    _isPlaying.value = false
    markCompletedIfNeeded()
    config.onComplete?.invoke()
  }

  /** 跳到指定 id 的步骤 */
  fun goto(id: String) {
    val newIndex = steps.indexOfFirst { it.id == id }
    if (newIndex < 0) return
    currentStep?.onStepDismissed?.invoke()
    _currentIndex.intValue = newIndex
    steps[newIndex].onStepShown?.invoke()
  }

  /** 重新开始 (重置状态但不影响持久化). */
  fun restart() {
    _currentIndex.intValue = 0
    _isPlaying.value = true
    currentStep?.onStepShown?.invoke()
  }

  /** 重新开始 + 清除持久化. */
  fun restartFromScratch() {
    config.preferences?.let { prefs ->
      config.guideId?.let { id -> prefs.reset(id) }
    }
    restart()
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

  /** 手动标记为已完成 (持久化). */
  fun markCompleted() {
    markCompletedIfNeeded()
  }

  /** 清除该引导的持久化完成标记. */
  fun clearCompleted() {
    config.preferences?.let { prefs ->
      config.guideId?.let { id -> prefs.reset(id) }
    }
  }

  private fun markCompletedIfNeeded() {
    if (config.guideId != null && config.preferences != null) {
      config.preferences?.markCompleted(config.guideId!!)
    }
  }
}

/**
 * 引导全局配置 (Onboarding config).
 *
 * 新增持久化字段:
 *  - [guideId] 引导唯一标识, 用于持久化记录 "该引导是否完成"
 *  - [preferences] 持久化实现 (推荐 [com.itsaky.androidide.onboarding.prefs.SharedPreferencesOnboardingPreferences])
 *  - [skipIfCompleted] 若 [guideId] 在持久化中已标记为完成, 启动时自动跳过
 *
 * 用法:
 * ```
 * val prefs = SharedPreferencesOnboardingPreferences(context)
 * LaunchOnboarding(
 *   steps = steps,
 *   config = OnboardingConfig(
 *     guideId = "first_time_user",
 *     preferences = prefs,
 *     skipIfCompleted = true,
 *   ),
 * )
 * ```
 */
data class OnboardingConfig(
  val highlightStyle: HighlightStyle = HighlightStyle.Default,
  val onComplete: (() -> Unit)? = null,
  val onSkipped: (() -> Unit)? = null,
  val cancellable: Boolean = true,                  // 点击空白是否跳过
  val showProgressIndicator: Boolean = true,        // 是否显示进度
  val pauseBetweenStepsMs: Long = 320,              // 步骤间暂停

  // === 持久化 (新增) ===
  val guideId: String? = null,                      // 引导唯一标识
  val preferences: OnboardingPreferences? = null,   // 持久化实现
  val skipIfCompleted: Boolean = true,              // 已完成则跳过
) {
  /** 便利构造: 使用 [OnboardingPreferences.getDefault] 作为默认 preferences. */
  constructor(
    guideId: String,
    skipIfCompleted: Boolean = true,
    highlightStyle: HighlightStyle = HighlightStyle.Default,
    onComplete: (() -> Unit)? = null,
    onSkipped: (() -> Unit)? = null,
    cancellable: Boolean = true,
    showProgressIndicator: Boolean = true,
    pauseBetweenStepsMs: Long = 320,
  ) : this(
    highlightStyle = highlightStyle,
    onComplete = onComplete,
    onSkipped = onSkipped,
    cancellable = cancellable,
    showProgressIndicator = showProgressIndicator,
    pauseBetweenStepsMs = pauseBetweenStepsMs,
    guideId = guideId,
    preferences = OnboardingPreferences.getDefaultOrNull(),
    skipIfCompleted = skipIfCompleted,
  )
}

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
