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

package com.itsaky.androidide.onboarding.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.onboarding.effects.frostedGlass
import com.itsaky.androidide.onboarding.effects.grainNoise

/**
 * 引导气泡 (Guide bubble).
 *
 * 这是整个库的核心组件: 偏白半透明磨砂气泡 + 标题 / 副标题 / 可选图标.
 *
 * 设计参考 dribbble 顶级设计师的 "磨砂半透明" 风格:
 *  - 偏白半透明底 (white-ish) + 高斯模糊
 *  - 微颗粒噪点 (grain noise) 模拟真实玻璃表面
 *  - 顶部 1px 高光内描边 增强立体感
 *  - 弹性进入 (Spring) 动画
 *  - 阴影 + 描边 让气泡"浮"在引导遮罩之上
 *
 * @param content 气泡内容 (主标题 + 副标题 + 可选图标)
 * @param shape 气泡形状 (默认圆角矩形)
 * @param style 气泡样式 (默认 BubbleStyle.Default)
 * @param visible 是否可见 (驱动淡入淡出)
 * @param modifier 外部 modifier
 * @param actions 底部可选操作行 (例如"下一步"按钮)
 */
@Composable
fun GuideBubble(
  content: BubbleContent,
  modifier: Modifier = Modifier,
  shape: BubbleShape = BubbleShape.Default,
  style: BubbleStyle = BubbleStyle.Default,
  visible: Boolean = true,
  actions: @Composable (() -> Unit)? = null,
) {
  val animShape = shape.shape

  // 进入/退出动画: scale + fade
  AnimatedVisibility(
    visible = visible,
    enter = scaleIn(
      initialScale = 0.86f,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
      ),
    ) + fadeIn(animationSpec = tween(220)),
    exit = scaleOut(
      targetScale = 0.94f,
      animationSpec = tween(160),
    ) + fadeOut(animationSpec = tween(160)),
    modifier = modifier,
  ) {
    // 阴影 + 磨砂玻璃 + 内容
    val shadow = style.shadowElevation
    Box(
      modifier = Modifier
        .widthIn(min = style.minWidth, max = style.maxWidth)
        .heightIn(min = 48.dp, max = if (style.height != null) style.height else Dp.Unspecified)
        .shadow(
          elevation = shadow,
          shape = animShape,
          ambientColor = style.shadowColor,
          spotColor = style.shadowColor,
        )
        .clip(shape = animShape)
        .frostedGlass(
          shape = animShape,
          tint = style.glassTint,
          alpha = style.glassAlpha,
          borderColor = style.borderColor,
          borderWidth = style.borderWidth,
          innerHighlight = style.innerHighlight,
          innerHighlightColor = style.innerHighlightColor,
        )
        .grainNoise(
          shape = animShape,
          grainColor = Color.White,
          grainAlpha = style.grainAlpha,
        )
        .padding(style.contentPadding),
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
      ) {
        // 标题行 (图标 + 标题)
        if (content.icon != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(
              imageVector = content.icon,
              contentDescription = content.iconDescription,
              tint = style.titleColor.copy(alpha = 0.85f),
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = content.title,
              style = style.titleStyle ?: BubbleContentDefaults.titleStyle(),
              color = style.titleColor,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        } else {
          Text(
            text = content.title,
            style = style.titleStyle ?: BubbleContentDefaults.titleStyle(),
            color = style.titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // 副标题
        if (content.subtitle.isNotEmpty()) {
          Text(
            text = content.subtitle,
            style = style.subtitleStyle ?: BubbleContentDefaults.subtitleStyle(),
            color = style.subtitleColor,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // 底部操作行
        if (actions != null) {
          Spacer(modifier = Modifier.height(4.dp))
          actions()
        }
      }
    }
  }
}
