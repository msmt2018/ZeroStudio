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

package com.itsaky.androidide.ui

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.BaseEditorActivity
import com.itsaky.androidide.onboarding.BubblePlacement
import com.itsaky.androidide.onboarding.OnboardingConfig
import com.itsaky.androidide.onboarding.OnboardingController
import com.itsaky.androidide.onboarding.OnboardingOverlay
import com.itsaky.androidide.onboarding.OnboardingPreferences
import com.itsaky.androidide.onboarding.OnboardingStep
import com.itsaky.androidide.onboarding.OnboardingTarget
import com.itsaky.androidide.onboarding.LaunchOnboarding
import com.itsaky.androidide.onboarding.bindOnboardingTarget
import com.itsaky.androidide.onboarding.bindOnboardingTargetAsHandle
import com.itsaky.androidide.onboarding.bubble.BubbleContent
import com.itsaky.androidide.onboarding.bubble.BubbleShape
import com.itsaky.androidide.onboarding.bubble.BubbleStyle
import com.itsaky.androidide.onboarding.highlight.HighlightAnimation
import com.itsaky.androidide.onboarding.highlight.HighlightShape
import com.itsaky.androidide.onboarding.highlight.HighlightTheme
import com.itsaky.androidide.onboarding.prefs.SharedPreferencesOnboardingPreferences
import com.itsaky.androidide.onboarding.simulation.GestureType
import com.itsaky.androidide.onboarding.simulation.PathPoint
import com.itsaky.androidide.onboarding.simulation.TouchSimulator
import kotlin.math.max

/**
 * 编辑器底部抽屉的操作引导控制器 (EditorBottomSheet onboarding controller).
 *
 * 职责:
 *  1. 把底部抽屉中的 3 个核心控件 (page_switch_gesture_bubble / header_container /
 *     external_symbol_input_view) 绑定到 [OnboardingTarget] (id-based)
 *  2. 启动一次 3 步操作引导, 引导用户学习如何使用抽屉
 *  3. 在引导过程中:
 *     - 框选对应控件 (高亮形状 + 主题 + 动画)
 *     - 弹出气泡 (位置智能选优: 全在屏幕底部 → 气泡显示在上方)
 *     - **真实** 模拟操作: 真正驱动 drawer / header / symbol-input 状态变化
 *     - 显示视觉手指 + 箭头 + 轨迹 (TouchSimulator 渲染)
 *     - 可点击副标题触发特定动作 (如: 点击 → 模拟点击中间黑色气泡)
 *     - 任何区域轻点推进到下一步 (符合 dribbble 高级 UX)
 *  4. 通过 SharedPreferences 持久化, 完成一次后永久不再提示
 *  5. 提供 [restart] 入口供开发/QA 反复调试
 *
 * 用法 (在 BaseEditorActivity 中):
 * ```
 * private lateinit var onboardingController: EditorBottomSheetOnboardingController
 *
 * override fun onPostCreate(savedInstanceState: Bundle?) {
 *   super.onPostCreate(savedInstanceState)
 *   setupBottomSheet()
 *   onboardingController = EditorBottomSheetOnboardingController(
 *     activity = this,
 *     bottomSheet = bottomSheet,
 *     overlayHost = binding.editor.onboardingOverlayHost,
 *   )
 *   onboardingController.attach()
 *   onboardingController.startIfNeeded()
 * }
 * ```
 */
class EditorBottomSheetOnboardingController(
  private val activity: BaseEditorActivity,
  private val bottomSheet: EditorBottomSheet,
  private val overlayHost: ComposeView,
) {
  // === id-based OnboardingTarget (持久化在全局 map 里) ===
  private val drawerBubbleTarget = OnboardingTarget.of("page_switch_gesture_bubble")
  private val headerContainerTarget = OnboardingTarget.of("header_container")
  private val symbolInputTarget = OnboardingTarget.of("external_symbol_input_view")

  // === 状态 ===
  private var controller: OnboardingController? = null
  private var preferences: OnboardingPreferences? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private var attached = false

  /**
   * 绑定控件到 OnboardingTarget (注册 OnLayoutChangeListener 监听位置变化).
   *
   * 该方法是幂等的, 可以多次调用.
   */
  fun attach() {
    if (attached) return
    attached = true
    preferences = SharedPreferencesOnboardingPreferences(activity)
    val bsBinding = bottomSheet.binding
    // page_switch_gesture_bubble 是 24dp 高全宽的横条, 视觉上只有中心的小手柄需要框选,
    // 用 bindOnboardingTargetAsHandle 限定 60dp 宽, 居中 (与 EdgeSnapBubbleView 手柄位置匹配)
    bsBinding.pageSwitchGestureBubble.bindOnboardingTargetAsHandle(
      target = drawerBubbleTarget,
      widthDp = 60f,
      heightDp = 24f,
      gravity = android.view.Gravity.CENTER,
    )
    // header_container 是 ViewFlipper, layout_toStartOf=tv_cursor_position,
    // 用 START 重力让框选区域紧贴左边缘, 避开右侧的 cursor 位置文本
    bsBinding.headerContainer.bindOnboardingTargetAsHandle(
      target = headerContainerTarget,
      widthDp = 200f,
      gravity = android.view.Gravity.START,
    )
    bsBinding.externalSymbolInputView.bindOnboardingTarget(symbolInputTarget)
  }

  /**
   * 如果引导未完成, 启动一次.
   *
   * 由于 [OnboardingConfig.skipIfCompleted] 默认 true, 已完成的引导不会显示.
   */
  fun startIfNeeded() {
    if (controller?.isPlaying == true) return
    val steps = buildSteps()
    controller = LaunchOnboarding(
      steps = steps,
      config = OnboardingConfig(
        guideId = GUIDE_ID,
        preferences = preferences,
        skipIfCompleted = true,
      ),
      autoStart = true,
    )
    overlayHost.setContent {
      ComposeOverlay(controller = controller)
    }
  }

  /**
   * 强制从头开始 (清除持久化 + 重新启动). 主要用于开发/QA 调试.
   */
  fun restart() {
    preferences?.reset(GUIDE_ID)
    controller = null
    startIfNeeded()
  }

  /**
   * Compose 层 (在 overlayHost ComposeView 中渲染引导浮层).
   */
  @Composable
  private fun ComposeOverlay(controller: OnboardingController?) {
    controller?.let { OnboardingOverlay(controller = it) }
  }

  // =============================================================================
  // 步骤构建
  // =============================================================================

  private fun buildSteps(): List<OnboardingStep> = listOf(
    buildStep1DrawerBubble(),
    buildStep2HeaderContainer(),
    buildStep3SymbolInput(),
  )

  /**
   * 步骤 1: 框选 page_switch_gesture_bubble.
   *
   * 用户学到:
   *  - 这里可以上滑打开 IDE 抽屉, 下滑关闭
   *  - 通过子标题中的可点击链接, 触发"模拟点击中间黑色气泡"演示
   *
   * UX 设计:
   *  - 气泡显示在 page_switch_gesture_bubble **上方** (因为控件在屏幕底部)
   *  - 高亮形状: Stadium (胶囊, 与控件形状匹配)
   *  - 高亮主题: Neon (霓虹蓝发光) + Pulse (脉冲动画)
   *  - 真实操作: step 进入 500ms 后开始视觉触摸模拟; onStepShown 1.5s 后
   *    调用 drawer.state = HALF_EXPANDED 真正打开抽屉
   *  - 子标题点击: 调用 performClick() 真正触发点击 → toggle header
   */
  private fun buildStep1DrawerBubble(): OnboardingStep {
    val bubble = bottomSheet.binding.pageSwitchGestureBubble
    val tap = computeTapPoint(bubble)
    return OnboardingStep(
      id = "step_1_drawer_bubble",
      content = BubbleContent.withClickableSubtitle(
        title = activity.getString(R.string.onboarding_step1_title),
        subtitle = activity.getString(R.string.onboarding_step1_subtitle),
        onSubtitleClick = {
          // 真实操作: 模拟点击中间黑色气泡 (toggle header)
          bubble.performClick()
        },
      ),
      target = drawerBubbleTarget,
      // 方形/圆角矩形 (无尾巴) + 磨砂玻璃 (BubbleStyle.WideCard 提供 32dp 模糊)
      bubbleShape = BubbleShape.RoundedRectangle(cornerRadius = 24.dp),
      bubbleStyle = BubbleStyle.WideCard,
      bubblePlacement = BubblePlacement.Above,  // 控件在底部, 气泡在上方
      // 边框形状: 圆形胶囊 (与小手柄形状匹配, 24dp 高的横条, 中心 60dp 宽)
      highlightShape = HighlightShape.Stadium,
      // 边框主题: 鲜艳的青色, 适度发光 (减小 glowRadius 避免边框变粗)
      highlightTheme = HighlightTheme.Neon(
        color = Color(0xFF00E5FF),
        glowColor = Color(0x8800E5FF),
        glowRadius = 8.dp,                           // 8dp 而非 18dp: 边框更精细
        scrimColor = Color(0x99000000),              // 60% 黑 (原 80% 太暗)
      ),
      highlightAnimation = HighlightAnimation.Pulse(
        durationMs = 1400,
        minAlpha = 0.6f,                              // 提高最小 alpha, 始终清晰可见
        maxAlpha = 1.0f,
      ),
      // 视觉触摸模拟: 在手柄中心反复点击 (TAP, loop=true),
      // 演示"点按这里可以切换抽屉"
      // (TouchSimulator.tap 工厂保证 1 point + TAP 类型, 满足 init 校验; 再 copy 自定义颜色)
      touchSimulator = TouchSimulator.tap(
        x = tap.x,
        y = tap.y,
        durationMs = 380,
        loop = true,
      ).copy(
        showFinger = true,
        showTrail = false,
        showRipple = true,
        fingerSize = 36.dp,
        fingerColor = Color(0xFFFF6B9D),
        trailColor = Color(0xFFFF6B9D),
        rippleColor = Color(0xFFFF6B9D),
        loopDelayMs = 1200,                                   // 两次点击间隔 1.2s
      ),
      // 真实操作: 进入步骤后 1s, 真的把抽屉打开一半 (HALF_EXPANDED)
      onStepShown = {
        mainHandler.postDelayed({
          openDrawerHalf()
        }, 1000)
        // 3.2s 后复位到 COLLAPSED
        mainHandler.postDelayed({
          collapseDrawer()
        }, 3200)
      },
    )
  }

  /**
   * 步骤 2: 框选 header_container.
   *
   * 用户学到: 这里显示构建状态 + 当前光标位置 (行:列).
   *
   * UX 设计:
   *  - 气泡显示在 header_container **上方**
   *  - 高亮形状: RoundedRect (圆角矩形, 跟 ViewFlipper 形状匹配)
   *  - 高亮主题: Solid (实色) + Breathe (呼吸动画)
   *  - 不需要操作模拟
   */
  private fun buildStep2HeaderContainer(): OnboardingStep {
    return OnboardingStep(
      id = "step_2_header_container",
      content = BubbleContent.withSubtitle(
        title = activity.getString(R.string.onboarding_step2_title),
        subtitle = activity.getString(R.string.onboarding_step2_subtitle),
      ),
      target = headerContainerTarget,
      // 圆角矩形气泡 (无尾巴, 干净)
      bubbleShape = BubbleShape.RoundedRectangle(cornerRadius = 24.dp),
      bubbleStyle = BubbleStyle.WideCard,
      bubblePlacement = BubblePlacement.Above,
      // 圆角矩形高亮 (与 ViewFlipper 形状匹配)
      highlightShape = HighlightShape.RoundedRect(cornerRadius = 12.dp),
      // 鲜明的琥珀色边框, 3dp 宽, 90% 透明度
      highlightTheme = HighlightTheme.Solid(
        borderColor = Color(0xFFFFC107),
        borderWidth = 3.dp,                            // 加粗 (原 2dp 太细)
        borderAlpha = 0.9f,
        scrimColor = Color(0x99000000),                // 60% 黑 (原 80% 太暗)
      ),
      highlightAnimation = HighlightAnimation.Breathe(
        durationMs = 1800,
        minAlpha = 0.7f,                                // 提高最小 alpha, 始终清晰
        maxAlpha = 1.0f,
      ),
    )
  }

  /**
   * 步骤 3: 框选 external_symbol_input_view.
   *
   * 用户学到: 这里可以**上滑/下滑**打开/关闭符号输入工具栏的抽屉.
   *
   * UX 设计:
   *  - 气泡显示在 external_symbol_input_view **上方** (控件在底部)
   *  - 高亮形状: RoundedRect (圆角矩形)
   *  - 高亮主题: Dashed (虚线) + Scan (扫描动画)
   *  - 真实操作: 视觉触摸模拟上滑 → 停顿 → 下滑 (完整上下滑演示)
   */
  private fun buildStep3SymbolInput(): OnboardingStep {
    val symbolView = bottomSheet.binding.externalSymbolInputView
    val path = computeSwipeUpPath(symbolView)
    return OnboardingStep(
      id = "step_3_symbol_input",
      content = BubbleContent(
        title = activity.getString(R.string.onboarding_step3_title),
        subtitle = activity.getString(R.string.onboarding_step3_subtitle),
      ),
      target = symbolInputTarget,
      // 圆角矩形气泡 (无尾巴, 干净)
      bubbleShape = BubbleShape.RoundedRectangle(cornerRadius = 24.dp),
      bubbleStyle = BubbleStyle.WideCard,
      bubblePlacement = BubblePlacement.Above,
      // 圆角矩形高亮 (与 AdvancedSymbolInputView 形状匹配)
      highlightShape = HighlightShape.RoundedRect(cornerRadius = 16.dp),
      // 鲜明的紫色虚线边框, 3dp 宽 (加粗)
      highlightTheme = HighlightTheme.Dashed(
        borderColor = Color(0xFFB388FF),
        borderWidth = 3.dp,                            // 加粗 (原 2dp 太细)
        dashLength = 12.dp,
        gapLength = 6.dp,
        scrimColor = Color(0x99000000),                // 60% 黑 (原 80% 太暗)
      ),
      highlightAnimation = HighlightAnimation.Scan(
        durationMs = 1800,
        lineWidth = 2.dp,
        lineColor = Color(0xCCB388FF),
        lineAlpha = 0.7f,
      ),
      // 视觉触摸模拟: 上滑 → 停顿 → 下滑复位 (完整上下滑演示)
      // loop=true 持续演示, 让用户清楚看到完整动作
      touchSimulator = TouchSimulator(
        type = GestureType.SWIPE,
        points = listOf(
          PathPoint(x = path.fromX, y = path.fromY, timeMs = 0),       // 起点 (中心)
          PathPoint(x = path.fromX, y = path.toY, timeMs = 600),       // 上滑 60%
          PathPoint(x = path.fromX, y = path.toY, timeMs = 1200),      // 停顿 (顶部)
          PathPoint(x = path.fromX, y = path.fromY, timeMs = 1800),     // 下滑复位
          PathPoint(x = path.fromX, y = path.fromY, timeMs = 2200),     // 停顿 (底部)
        ),
        // durationMs 由 points 的 max(timeMs) 自动计算 (此处 = 2200)
        loop = true,
        loopDelayMs = 1000,                                           // 两次完整上下滑间隔 1s
        showFinger = true,
        showTrail = true,
        showRipple = true,
        fingerSize = 36.dp,
        fingerColor = Color(0xFFB388FF),
        trailColor = Color(0xFFB388FF),
        rippleColor = Color(0xFFB388FF),
      ),
    )
  }

  // =============================================================================
  // 内部工具
  // =============================================================================

  /**
   * 计算 "上滑 20%~30%" 的路径 (从 view 中心向上 60% view 高度).
   */
  private fun computeSwipeUpPath(view: View): SwipePath {
    val loc = IntArray(2)
    view.getLocationInWindow(loc)
    val centerX = loc[0] + view.width / 2f
    val centerY = loc[1] + view.height / 2f
    // 起点: view 中心
    // 终点: 中心向上 60% view 高度
    val dy = view.height * 0.6f
    val toY = max(0f, centerY - dy)
    return SwipePath(
      fromX = centerX,
      fromY = centerY,
      toX = centerX,
      toY = toY,
    )
  }

  /**
   * 计算 view 中心点的屏幕坐标 (用于 TAP 触摸模拟).
   *
   * 注意: view 在 `getLocationInWindow` 之后才有效, 如果 view 还没 measure
   * 返回 (0, 0). 调用前请确保 view 已经布局完成.
   */
  private fun computeTapPoint(view: View): SwipePath {
    val loc = IntArray(2)
    view.getLocationInWindow(loc)
    val centerX = loc[0] + view.width / 2f
    val centerY = loc[1] + view.height / 2f
    return SwipePath(
      fromX = centerX,
      fromY = centerY,
      toX = centerX,
      toY = centerY,
    )
  }

  /**
   * 真实操作: 打开 drawer 到 HALF_EXPANDED.
   */
  private fun openDrawerHalf() {
    if (bottomSheet.getCurrentBehaviorState() == BottomSheetBehavior.STATE_EXPANDED) return
    bottomSheet.expandToHalf()
  }

  private fun collapseDrawer() {
    bottomSheet.forceCollapse()
  }

  /** 临时数据类: 路径四元组. */
  private data class SwipePath(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
  )

  companion object {
    /** 引导唯一 ID (用于 SharedPreferences 持久化). */
    const val GUIDE_ID = "editor_bottom_sheet_onboarding_v1"
  }
}
