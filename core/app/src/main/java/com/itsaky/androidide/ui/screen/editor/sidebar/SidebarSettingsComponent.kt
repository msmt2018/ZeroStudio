package com.itsaky.androidide.ui.screen.editor.sidebar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.resources.R

/**
 * 现代风格的侧滑栏导航条位置选择器。
 * 纯 Compose API 设计，每个方块通过模拟屏幕布局展示工具条所处位置。
 */
@Composable
fun SidebarPositionSelectorCard(
    currentPosition: SidebarBarPosition,
    onPositionChanged: (SidebarBarPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Text(
        text = stringResource(R.string.title_sidebar_tab_position),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.desc_sidebar_tab_position),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
    )

    Spacer(Modifier.height(12.dp))

    // 四向方块水平流式排布 (均分宽度，弹性响应)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SidebarBarPosition.entries.forEach { position ->
        PositionSelectionTile(
            position = position,
            isSelected = position == currentPosition,
            onClick = { onPositionChanged(position) },
            modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

/**
 * 单个位置示意卡片：以微缩手机框体现“工具栏长条”的实际方位
 */
@Composable
private fun PositionSelectionTile(
    position: SidebarBarPosition,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val haptic = LocalHapticFeedback.current
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()

  val scale by animateFloatAsState(
      targetValue = if (isPressed) 0.92f else 1.0f,
      animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
      label = "tile_scale",
  )

  val borderColor by animateColorAsState(
      targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
      animationSpec = tween(180, easing = FastOutSlowInEasing),
      label = "tile_border",
  )

  val backgroundColor by animateColorAsState(
      targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow,
      animationSpec = tween(180, easing = FastOutSlowInEasing),
      label = "tile_bg",
  )

  Surface(
      modifier = modifier
          .scale(scale)
          .aspectRatio(0.85f)
          .clip(RoundedCornerShape(12.dp))
          .border(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
          .clickable(
              interactionSource = interactionSource,
              indication = null,
              onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
              },
          ),
      color = backgroundColor,
  ) {
    Column(
        modifier = Modifier.fillMaxSize().padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
      // 内部微缩屏幕模拟视口
      Box(
          modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
      ) {
        val barColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        when (position) {
          SidebarBarPosition.TOP -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(barColor)
                    .align(Alignment.TopCenter),
            )
          }
          SidebarBarPosition.BOTTOM -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(barColor)
                    .align(Alignment.BottomCenter),
            )
          }
          SidebarBarPosition.LEFT -> {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(barColor)
                    .align(Alignment.CenterStart),
            )
          }
          SidebarBarPosition.RIGHT -> {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(barColor)
                    .align(Alignment.CenterEnd),
            )
          }
        }
      }

      Spacer(Modifier.height(6.dp))

      Text(
          text = when (position) {
            SidebarBarPosition.TOP -> "顶部"
            SidebarBarPosition.BOTTOM -> "底部"
            SidebarBarPosition.LEFT -> "左侧"
            SidebarBarPosition.RIGHT -> "右侧"
          },
          fontSize = 10.sp,
          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
          color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}