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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.onboarding.LaunchOnboarding
import com.itsaky.androidide.onboarding.OnboardingController
import com.itsaky.androidide.onboarding.OnboardingOverlay
import com.itsaky.androidide.onboarding.OnboardingStep
import com.itsaky.androidide.onboarding.bubble.BubbleContent
import com.itsaky.androidide.onboarding.bubble.BubblePlacement
import com.itsaky.androidide.onboarding.bubble.BubbleShape
import com.itsaky.androidide.onboarding.bubble.BubbleStyle
import com.itsaky.androidide.onboarding.highlight.HighlightStyle
import com.itsaky.androidide.onboarding.simulation.GestureType
import com.itsaky.androidide.onboarding.simulation.PathPoint
import com.itsaky.androidide.onboarding.simulation.TouchSimulator

/**
 * 完整示例: 演示如何使用 zero-onboarding-guide 库引导用户操作.
 *
 * 场景: 引导用户
 *  1. 欢迎 (居中气泡)
 *  2. 搜索按钮 (高亮 + 气泡)
 *  3. 上滑打开底部抽屉 (操作模拟)
 *  4. 设置入口
 */
@Composable
fun OnboardingGuideExample() {
  // 目标控件的 Rect (来自 onGloballyPositioned)
  var searchBtnRect by remember { mutableStateOf<Rect?>(null) }
  var drawerHandleRect by remember { mutableStateOf<Rect?>(null) }
  var settingsBtnRect by remember { mutableStateOf<Rect?>(null) }

  // 引导控制器
  var controller by remember { mutableStateOf<OnboardingController?>(null) }
  val density = LocalDensity.current

  // 启动引导
  LaunchedEffect(Unit) {
    val steps = listOf(
      // 步骤 1: 欢迎 (无目标, 居中气泡)
      OnboardingStep(
        id = "welcome",
        content = BubbleContent(
          title = "欢迎使用 ZeroStudio!",
          subtitle = "这是一个 3 步快速引导, 帮你熟悉主要功能.",
        ),
        bubbleShape = BubbleShape.WideCard(),
        bubbleStyle = BubbleStyle.WideCard,
      ),

      // 步骤 2: 搜索按钮 (有目标 + 高亮)
      OnboardingStep(
        id = "search",
        content = BubbleContent(
          title = "点击这里搜索",
          subtitle = "支持文件名, 类名, 符号搜索",
          icon = Icons.Default.Search,
        ),
        targetRect = searchBtnRect,
        bubbleShape = BubbleShape.RoundedRectangle(),
        bubbleStyle = BubbleStyle.Tooltip,
        bubblePlacement = BubblePlacement.Above,
        highlightStyle = HighlightStyle.Strong,
      ),

      // 步骤 3: 底部抽屉 (有目标 + 高亮 + 操作模拟)
      OnboardingStep(
        id = "drawer",
        content = BubbleContent(
          title = "上滑打开底部抽屉",
          subtitle = "这里有更多功能, 例如终端, 调试, 设置等",
        ),
        targetRect = drawerHandleRect,
        bubbleShape = BubbleShape.SpeechBubble(
          cornerRadius = 20.dp,
          tailSize = 14.dp,
          tailPosition = BubbleShape.TailPosition.BottomCenter,
        ),
        bubbleStyle = BubbleStyle.WideCard,
        bubblePlacement = BubblePlacement.Above,
        highlightStyle = HighlightStyle.Default,
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

      // 步骤 4: 设置入口
      OnboardingStep(
        id = "settings",
        content = BubbleContent(
          title = "进入设置",
          subtitle = "自定义你的 IDE 体验",
        ),
        targetRect = settingsBtnRect,
        bubbleShape = BubbleShape.Pill(),
        bubbleStyle = BubbleStyle.Compact,
        bubblePlacement = BubblePlacement.Below,
        highlightStyle = HighlightStyle.Default,
        touchSimulator = TouchSimulator.tap(x = 0f, y = 0f),
      ),
    )

    controller = LaunchOnboarding(steps = steps, autoStart = true)
  }

  Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
    // 主内容 UI
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
        // 搜索按钮 (有目标定位)
        Box(
          modifier = Modifier
            .align(Alignment.CenterEnd)
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFFEEEEEE))
            .onGloballyPositioned { coords ->
              val rect = coords.boundsInWindow()
              searchBtnRect = Rect(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
              )
            },
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

      // 设置按钮
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .background(Color.White)
          .onGloballyPositioned { coords ->
            val rect = coords.boundsInWindow()
            settingsBtnRect = Rect(
              left = rect.left,
              top = rect.top,
              right = rect.right,
              bottom = rect.bottom,
            )
          },
        contentAlignment = Alignment.Center,
      ) {
        Text("设置入口")
      }
    }

    // 底部抽屉把手 (有目标定位)
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 16.dp)
        .size(width = 80.dp, height = 4.dp)
        .background(Color.DarkGray, CircleShape)
        .onGloballyPositioned { coords ->
          val rect = coords.boundsInWindow()
          drawerHandleRect = Rect(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
          )
        },
    )

    // 引导浮层 (在所有内容之上)
    controller?.let { c ->
      OnboardingOverlay(controller = c)
    }
  }
}
