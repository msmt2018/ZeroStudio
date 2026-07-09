package com.itsaky.androidide.fragments.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.ProjectHistory
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 卡片化的“最近项目 / 高频项目”项。
 *
 * - 滑动支持：右到左拖动时露出右侧 Pin / Delete 操作菜单。
 * - 分层视差：拖动期间前景卡片轻微缩小 (~5–10%)，背景动作按钮同步
 *   滑入，阴影随拖动距离变化。
 * - 释放判定：拖动超过 35% 卡片宽度则自动停在“已展开”位置；否则回弹
 *   收起。
 * - 视觉：所有表面都使用 `Modifier.blur` + 半透明白底模拟毛玻璃效果。
 */
@Composable
fun SwipeableProjectItem(
    project: ProjectHistory,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showOpenCount: Boolean = false,
) {
  val density = LocalDensity.current
  val maxRevealPx = with(density) { 150.dp.toPx() }
  val collapseThresholdFraction = 0.35f

  var rawDragOffset by remember { mutableStateOf(0f) }
  var isExpanded by remember { mutableStateOf(false) }

  val animatedDragOffset by
      animateFloatAsState(
          targetValue = rawDragOffset,
          animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
          label = "swipe-offset",
      )

  // Foreground scales down between 0% and ~10% as the user drags, giving a
  // subtle "depress" parallax that pairs with the action menu reveal.
  val dragProgress = (abs(animatedDragOffset) / maxRevealPx).coerceIn(0f, 1f)
  val foregroundScale = 1f - dragProgress * 0.08f
  // Shadow only appears while dragging so the collapsed item reads as a flat
  // list row (no floating "card" wrapper) — see "多余的包裹层需要去除".
  val foregroundElevation = 4f * dragProgress
  val foregroundAlpha = 1f - dragProgress * 0.05f

  // Reset expansion when the project identity changes (e.g. list reordered).
  LaunchedEffect(project.path) {
    rawDragOffset = 0f
    isExpanded = false
  }

  Box(modifier = modifier.fillMaxWidth().height(58.dp)) {
    // ---- Background layer: action menu (Pin + Delete) ----
    // 背景层不再使用高斯模糊——高斯模糊会遮挡整个菜单区域，导致按钮不可见。
    // 高斯模糊改为设置在每个按钮的背景上（见 SwipeActionButton）。
    Row(
        modifier =
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFECEFF1).copy(alpha = 0.55f),
                            Color(0xFFD7DEE5).copy(alpha = 0.65f),
                        )
                    )
                )
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
      // The action buttons are visible only when the foreground has slid
      // enough to reveal them. We fade + scale them in to keep the layered
      // effect crisp.
      val actionVisible = dragProgress > 0.05f
      SwipeActionButton(
          icon = Icons.Filled.PushPin,
          label = if (project.isPinned) stringResource(R.string.swipe_action_unpin)
          else stringResource(R.string.swipe_action_pin),
          tint = Color(0xFF1565C0),
          visible = actionVisible,
          progress = dragProgress,
          onClick = {
            rawDragOffset = 0f
            isExpanded = false
            onPin()
          },
      )
      Spacer(modifier = Modifier.width(8.dp))
      SwipeActionButton(
          icon = Icons.Filled.Delete,
          label = stringResource(R.string.swipe_action_delete),
          tint = Color(0xFFC62828),
          visible = actionVisible,
          progress = dragProgress,
          onClick = {
            rawDragOffset = 0f
            isExpanded = false
            onDelete()
          },
      )
    }

    // ---- Foreground layer: project card, scales down on drag ----
    Box(
        modifier =
            Modifier.fillMaxSize()
                .offset { IntOffset(animatedDragOffset.roundToInt(), 0) }
                .scale(foregroundScale)
                .graphicsLayer {
                  shadowElevation = foregroundElevation
                  shape = RoundedCornerShape(12.dp)
                  clip = false
                  alpha = foregroundAlpha
                }
                .pointerInput(project.path) {
                  detectHorizontalDragGestures(
                      onDragEnd = {
                        // Re-evaluate snap based on the final drag distance.
                        val expandedByDrag =
                            abs(rawDragOffset) > maxRevealPx * collapseThresholdFraction
                        if (expandedByDrag != isExpanded) {
                          isExpanded = expandedByDrag
                        }
                        rawDragOffset = if (isExpanded) -maxRevealPx else 0f
                      },
                      onDragCancel = {
                        rawDragOffset = if (isExpanded) -maxRevealPx else 0f
                      },
                      onHorizontalDrag = { _, dragAmount ->
                        val proposed = rawDragOffset + dragAmount
                        // Only allow leftward (negative) drag to reveal the
                        // action menu; rightward drag collapses.
                        rawDragOffset = when {
                          isExpanded -> (proposed).coerceIn(-maxRevealPx, 0f)
                          else -> proposed.coerceIn(-maxRevealPx, 0f)
                        }
                      },
                  )
                }
                .clip(RoundedCornerShape(12.dp))
                // Flat white surface matching the page background — removes
                // the visible "card wrapper" rectangle the user reported
                // ("多余的包裹层需要去除"), while still occluding the action
                // menu behind it when collapsed.
                .background(Color.White)
                .clickable {
                  // A click on the card always opens the project, even if
                  // the user happens to be in the "expanded" state.
                  if (isExpanded) {
                    rawDragOffset = 0f
                    isExpanded = false
                  } else {
                    onClick()
                  }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxSize(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        // Pin indicator badge (small pin icon when pinned, sized 0 otherwise)
        if (project.isPinned) {
          Box(
              modifier =
                  Modifier.size(18.dp)
                      .clip(CircleShape)
                      .background(Color(0xFFFFC107).copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center,
          ) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = null,
                tint = Color(0xFFB26A00),
                modifier = Modifier.size(10.dp),
            )
          }
          Spacer(modifier = Modifier.width(6.dp))
        }

        Box(
            modifier =
                Modifier.size(26.dp).clip(CircleShape).background(project.color),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              project.letter,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                project.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = Color(0xFF1E1E1E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
          }
          Text(
              project.path,
              fontSize = 9.sp,
              color = Color.Gray,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
        }
        // 🔥点击次数：固定悬浮在右边边缘（仅高频项目显示）
        if (showOpenCount && project.openCount > 0) {
          Text(
              "🔥${project.openCount}",
              fontSize = 9.sp,
              color = Color(0xFF00897B),
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
          )
          Spacer(modifier = Modifier.width(4.dp))
        }
        // Faint open affordance on the right side. Hidden while the
        // action menu is fully expanded so the gesture reads as
        // "drag from the right".
        val openHintAlpha by
            animateFloatAsState(
                targetValue = if (dragProgress > 0.4f) 0f else 0.85f,
                animationSpec = tween(120),
                label = "open-hint",
            )
        Text(
            text = stringResource(R.string.swipe_action_open_hint),
            color = Color(0xFF00897B).copy(alpha = openHintAlpha),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

/**
 * 右侧滑出的单个动作按钮（Pin / Delete）。用 progress 驱动 opacity 与
 * 缩放，保证只有当前景已经划开一段距离时才完全可见，未划开时是淡出
 * 的占位，触感更细腻。
 *
 * 按钮为圆形，背景使用半透明高斯模糊（红/蓝半透明毛玻璃），尺寸紧凑
 * （直径 28dp，较原来 64dp 宽减少约 56%）。
 */
@Composable
private fun SwipeActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    visible: Boolean,
    progress: Float,
    onClick: () -> Unit,
) {
  val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(120), label = "btn-alpha")
  val scale = 0.85f + 0.15f * progress
  // 按钮背景：按钮颜色（红/蓝）+ 半透明 + 高斯模糊。
  // alpha 0.6f 让颜色明显可见（红色/蓝色清晰），同时保持半透明质感。
  val bg by
      animateColorAsState(
          targetValue = tint.copy(alpha = 0.6f),
          animationSpec = tween(140),
          label = "btn-bg",
      )
  Box(
      modifier =
          Modifier.graphicsLayer { this.alpha = alpha }
              .scale(scale)
              .size(28.dp)
              .clip(CircleShape)
              // 高斯模糊设置在按钮背景上，而非遮挡整个菜单
              .blur(4.dp)
              .background(bg)
              .clickable(enabled = visible, onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
  }
  // Hint to the compiler that the content color is fine.
  LocalContentColor
}
