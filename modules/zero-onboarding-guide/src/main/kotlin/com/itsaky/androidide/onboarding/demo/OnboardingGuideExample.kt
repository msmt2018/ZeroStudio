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

package com.itsaky.androidide.onboarding.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.onboarding.BubblePlacement
import com.itsaky.androidide.onboarding.LaunchOnboarding
import com.itsaky.androidide.onboarding.OnboardingConfig
import com.itsaky.androidide.onboarding.OnboardingController
import com.itsaky.androidide.onboarding.OnboardingOverlay
import com.itsaky.androidide.onboarding.OnboardingPreferences
import com.itsaky.androidide.onboarding.OnboardingStep
import com.itsaky.androidide.onboarding.OnboardingTarget
import com.itsaky.androidide.onboarding.bubble.BubbleContent
import com.itsaky.androidide.onboarding.bubble.BubbleShape
import com.itsaky.androidide.onboarding.bubble.BubbleStyle
import com.itsaky.androidide.onboarding.highlight.HighlightAnimation
import com.itsaky.androidide.onboarding.highlight.HighlightShape
import com.itsaky.androidide.onboarding.highlight.HighlightTheme
import com.itsaky.androidide.onboarding.onboardingBind
import com.itsaky.androidide.onboarding.prefs.SharedPreferencesOnboardingPreferences
import com.itsaky.androidide.onboarding.simulation.GestureType
import com.itsaky.androidide.onboarding.simulation.PathPoint
import com.itsaky.androidide.onboarding.simulation.TouchSimulator

/**
 * 完整示例: 演示 zero-onboarding-guide 库的所有高级特性.
 *
 * 演示内容:
 *  1. **持久化** (guideId) — 完成一次后永久不再提示
 *  2. **id 绑定目标** (Modifier.onboardingBind) — 滚动/折叠后位置自动跟踪
 *  3. **多种高亮形状** — Circle / RoundedRect / Stadium / Polygon / Spotlight
 *  4. **多种主题** — Solid / Dashed / Neon / Tape / Frosted
 *  5. **多种动画** — Pulse / Scan / Wave / Breathe
 *  6. **操作模拟** — Tap / Swipe (上滑打开底部抽屉)
 *  7. **气泡智能定位** — BubblePlacement.Auto 自动选空间最大方向
 */
@Composable
fun OnboardingGuideExample() {
  // === 1. 持久化: 一次性引导 ===
  val context = LocalContext.current
  val prefs = remember { SharedPreferencesOnboardingPreferences(context) }
  // 首次启动时注册全局默认实例
  LaunchedEffect(Unit) {
    OnboardingPreferences.setDefault(prefs)
  }

  // === 2. id 绑定的目标 (id-based binding) ===
  // 用 OnboardingTarget.of 创建一个 id 目标, 可在 UI 中通过 Modifier.onboardingBind 标记
  val searchTarget = remember { OnboardingTarget.of("search_button") }
  val settingsTarget = remember { OnboardingTarget.of("settings_button") }
  val drawerTarget = remember { OnboardingTarget.of("drawer_handle") }

  // === 旧 API 兼容: 也可以直接传 Rect (但不如 id 绑定灵活) ===
  var drawerHandleRect by remember { mutableStateOf<Rect?>(null) }

  // 引导控制器
  var controller by remember { mutableStateOf<OnboardingController?>(null) }
  val density = LocalDensity.current

  // === 启动引导 (带持久化) ===
  LaunchedEffect(Unit) {
    val steps = listOf(
      // ===== 步骤 1: 欢迎 (无目标, 居中气泡) =====
      OnboardingStep(
        id = "welcome",
        content = BubbleContent(
          title = "欢迎使用 ZeroStudio!",
          subtitle = "3 步快速引导, 帮你熟悉主要功能",
        ),
        bubbleShape = BubbleShape.WideCard(),
        bubbleStyle = BubbleStyle.WideCard,
        // 居中气泡: 用 Auto 也行, 实际会回退到居中
        bubblePlacement = BubblePlacement.Auto,
      ),

      // ===== 步骤 2: 搜索按钮 (id 绑定 + Circle 形状 + 脉冲 + 霓虹主题) =====
      OnboardingStep(
        id = "search",
        content = BubbleContent(
          title = "点击这里搜索",
          subtitle = "支持文件名 / 类名 / 符号搜索",
          icon = Icons.Default.Search,
        ),
        target = searchTarget,                       // <-- id 绑定 (新 API)
        bubbleShape = BubbleShape.RoundedRectangle(),
        bubbleStyle = BubbleStyle.Tooltip,
        bubblePlacement = BubblePlacement.Above,
        // 三维度高亮
        highlightShape = HighlightShape.Circle,      // 圆形高亮 (因为搜索按钮是圆形)
        highlightTheme = HighlightTheme.Neon(         // 霓虹 (发光)
          color = Color(0xFF00E5FF),
          glowColor = Color(0x6600E5FF),
          glowRadius = 16.dp,
          scrimColor = Color(0x99000000),
        ),
        highlightAnimation = HighlightAnimation.Pulse(
          durationMs = 1400,
          minAlpha = 0.4f,
          maxAlpha = 1.0f,
        ),
      ),

      // ===== 步骤 3: 底部抽屉 (矩形形状 + 虚线主题 + 上滑手势模拟) =====
      OnboardingStep(
        id = "drawer",
        content = BubbleContent(
          title = "上滑打开底部抽屉",
          subtitle = "这里有更多功能: 终端, 调试, 设置等",
        ),
        target = drawerTarget,                       // <-- id 绑定
        bubbleShape = BubbleShape.SpeechBubble(
          cornerRadius = 20.dp,
          tailSize = 14.dp,
          tailPosition = BubbleShape.TailPosition.BottomCenter,
        ),
        bubbleStyle = BubbleStyle.WideCard,
        bubblePlacement = BubblePlacement.Above,
        highlightShape = HighlightShape.RoundedRect(cornerRadius = 12.dp),
        highlightTheme = HighlightTheme.Dashed(      // 虚线
          borderColor = Color(0xFFFFFFFF),
          borderWidth = 2.dp,
          dashLength = 12.dp,
          gapLength = 6.dp,
        ),
        highlightAnimation = HighlightAnimation.Scan(  // 扫描动画
          durationMs = 1800,
          lineWidth = 2.dp,
          lineColor = Color(0xCC00E5FF),
          lineAlpha = 0.7f,
        ),
        // 上滑操作模拟
        touchSimulator = TouchSimulator(
          type = GestureType.SWIPE,
          points = listOf(
            PathPoint(
              x = with(density) { 200.dp.toPx() },
              y = with(density) { 1400.dp.toPx() },
              timeMs = 0,
            ),
            PathPoint(
              x = with(density) { 200.dp.toPx() },
              y = with(density) { 800.dp.toPx() },
              timeMs = 800,
            ),
          ),
          loop = true,
        ),
      ),

      // ===== 步骤 4: 设置入口 (胶囊形状 + 胶带主题 + 波动动画) =====
      OnboardingStep(
        id = "settings",
        content = BubbleContent(
          title = "进入设置",
          subtitle = "自定义你的 IDE 体验",
        ),
        target = settingsTarget,                     // <-- id 绑定
        bubbleShape = BubbleShape.Pill(),
        bubbleStyle = BubbleStyle.Compact,
        bubblePlacement = BubblePlacement.Below,
        highlightShape = HighlightShape.Stadium,     // 胶囊形
        highlightTheme = HighlightTheme.Tape(        // 胶带 (四角 L 形)
          color = Color(0xFFFFC107),
          length = 24.dp,
          thickness = 3.dp,
        ),
        highlightAnimation = HighlightAnimation.Wave(  // 波动动画
          durationMs = 2000,
          waveCount = 3,
          maxRadiusMultiplier = 1.4f,
          waveColor = Color(0xFFFFC107),
          waveAlpha = 0.5f,
        ),
        touchSimulator = TouchSimulator.tap(x = 0f, y = 0f),
      ),
    )

    controller = LaunchOnboarding(
      steps = steps,
      config = OnboardingConfig(
        guideId = "demo_onboarding_v1",        // <-- 持久化 ID
        preferences = prefs,                   // <-- 持久化实现
        skipIfCompleted = true,                // <-- 已完成则自动跳过
      ),
      autoStart = true,
    )
  }

  Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
    // === 主内容 UI ===
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
      // 顶部栏
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .background(Color.White),
      ) {
        // 搜索按钮 (id 绑定, 自动跟踪位置)
        Box(
          modifier = Modifier
            .align(Alignment.CenterEnd)
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFFEEEEEE))
            .onboardingBind(searchTarget),  // <-- 关键: id 绑定
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Default.Search, contentDescription = "Search")
        }
      }

      // 主内容
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(400.dp)
          .background(Color(0xFFE0E0E0)),
        contentAlignment = Alignment.Center,
      ) {
        Text("主内容区")
      }

      // 设置按钮 (id 绑定)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .background(Color.White)
          .onboardingBind(settingsTarget),   // <-- 关键: id 绑定
        contentAlignment = Alignment.Center,
      ) {
        Text("设置入口")
      }
    }

    // 底部抽屉把手 (id 绑定 + 备用 Rect)
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 16.dp)
        .size(width = 80.dp, height = 4.dp)
        .background(Color.DarkGray, CircleShape)
        .onboardingBind(drawerTarget)        // <-- 关键: id 绑定
        .onGloballyPositioned { coords ->     // 备用: 同时记录 Rect
          val rect = coords.boundsInWindow()
          drawerHandleRect = Rect(rect.left, rect.top, rect.right, rect.bottom)
        },
    )

    // === 引导浮层 (在所有内容之上) ===
    controller?.let { c ->
      OnboardingOverlay(controller = c)
    }
  }
}

/**
 * 用法示例: 在你的 Activity / Fragment 中调用.
 *
 * ```
 * setContent {
 *   OnboardingGuideExample()
 * }
 * ```
 */

// =============================================================================
// API 用法速查
// =============================================================================

@Suppress("unused")
private fun apiCheatsheet() {
  // 1. 持久化: 在 Application.onCreate() 中初始化
  // val prefs = SharedPreferencesOnboardingPreferences(context)
  // OnboardingPreferences.setDefault(prefs)
  //
  // 2. 创建带持久化的引导
  // LaunchOnboarding(
  //   steps = steps,
  //   config = OnboardingConfig(
  //     guideId = "first_time_user",       // 唯一 ID
  //     skipIfCompleted = true,            // 已完成则跳过
  //   ),
  // )
  //
  // 3. id 绑定目标
  // val target = OnboardingTarget.of("my_button")
  // Box(modifier = Modifier.onboardingBind(target)) { ... }
  // OnboardingStep(id = "...", target = target, ...)
  //
  // 4. 选择高亮形状
  // HighlightShape.Auto              // 自动 (推荐)
  // HighlightShape.RoundedRect(8.dp)  // 圆角矩形
  // HighlightShape.Rect              // 矩形
  // HighlightShape.Circle            // 圆形
  // HighlightShape.Oval              // 椭圆
  // HighlightShape.Stadium           // 胶囊
  // HighlightShape.Polygon(6)        // 六边形
  // HighlightShape.Blob()            // 不规则形状
  // HighlightShape.Spotlight         // 聚光
  // HighlightShape.Custom(myShape)   // 自定义
  //
  // 5. 选择高亮主题
  // HighlightTheme.Default           // 实色
  // HighlightTheme.Dashed            // 虚线
  // HighlightTheme.Dotted            // 点线
  // HighlightTheme.Neon              // 霓虹
  // HighlightTheme.Tape              // 胶带
  // HighlightTheme.Corners           // 四角
  // HighlightTheme.Spotlight         // 聚光
  // HighlightTheme.Frosted           // 磨砂
  //
  // 6. 选择高亮动画
  // HighlightAnimation.None          // 无
  // HighlightAnimation.Pulse         // 脉冲
  // HighlightAnimation.Rotate        // 旋转
  // HighlightAnimation.Breathe       // 呼吸
  // HighlightAnimation.Scan          // 扫描
  // HighlightAnimation.Wave          // 波动
  // HighlightAnimation.Shimmer       // 微光
  //
  // 7. 气泡位置
  // BubblePlacement.Auto             // 自动选最大空间方向
  // BubblePlacement.Above            // 上方
  // BubblePlacement.Below            // 下方
  // BubblePlacement.Left             // 左侧
  // BubblePlacement.Right            // 右侧
  // BubblePlacement.TopCenter        // 屏幕顶部居中
  // BubblePlacement.BottomCenter     // 屏幕底部居中
  // BubblePlacement.Custom(x, y)     // 自定义
  //
  // 8. 重置已完成的引导
  // val controller = rememberOnboardingController(steps, config)
  // controller.clearCompleted()       // 清除持久化
  // controller.restartFromScratch()   // 清除 + 重新开始
  //
  // 9. 手动标记完成
  // controller.markCompleted()
  //
  // 10. 监听完成状态
  // val isDone by prefs.observeCompleted("first_time").collectAsState(initial = false)
}
