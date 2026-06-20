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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.onboarding.bubble.BubbleContent
import com.itsaky.androidide.onboarding.bubble.BubbleShape
import com.itsaky.androidide.onboarding.bubble.BubbleStyle
import com.itsaky.androidide.onboarding.bubble.GuideBubble
import com.itsaky.androidide.onboarding.simulation.TouchSimulator
import com.itsaky.androidide.onboarding.simulation.TouchSimulatorOverlay

/**
 * Bubble 画廊: 展示所有 10 种内置形状.
 */
@Composable
fun BubbleGallery() {
  val shapes = listOf(
    "RoundedRectangle" to BubbleShape.RoundedRectangle(),
    "Square" to BubbleShape.Square(),
    "Pill" to BubbleShape.Pill(),
    "Circle" to BubbleShape.Circle,
    "WideCard" to BubbleShape.WideCard(),
    "StretchedBar" to BubbleShape.StretchedBar(),
    "Hexagon" to BubbleShape.Hexagon(),
    "Diamond" to BubbleShape.Diamond(),
    "SpeechBubble" to BubbleShape.SpeechBubble(),
    "Tabbed" to BubbleShape.Tabbed(),
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFF5F5F5))
      .padding(16.dp),
  ) {
    shapes.forEach { (name, shape) ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        GuideBubble(
          content = BubbleContent(
            title = name,
            subtitle = "Demo bubble in $name shape",
          ),
          shape = shape,
          style = BubbleStyle.Default,
        )
      }
    }
  }
}

/**
 * TouchSimulator 演示: 各种手势的动画.
 */
@Composable
fun TouchSimulatorGallery() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFF5F5F5)),
  ) {
    // TAP 演示
    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
      TouchSimulatorOverlay(
        simulator = TouchSimulator.tap(200f, 100f),
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // LONG_PRESS 演示
    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
      TouchSimulatorOverlay(
        simulator = TouchSimulator.longPress(200f, 100f),
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // SWIPE 演示
    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
      TouchSimulatorOverlay(
        simulator = TouchSimulator.swipe(
          fromX = 50f, fromY = 100f,
          toX = 350f, toY = 100f,
        ),
      )
    }
  }
}
