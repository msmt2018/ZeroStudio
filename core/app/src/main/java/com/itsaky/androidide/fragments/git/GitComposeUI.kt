/*
 *  共享的 Git Compose UI 组件 —— 独立设计, 不依赖 puppygit 的 UI 层.
 *
 *  提供统一的 Loading / Empty / Error 状态组件, 以及统一的颜色/间距常量,
 *  让所有 Git 子页面视觉风格一致, 有呼吸感.
 */
package com.itsaky.androidide.fragments.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Git 页面统一间距常量, 营造呼吸感. */
object GitSpacing {
  val cardPadding = 16.dp
  val cardCorner = 12.dp
  val itemSpacing = 8.dp
  val sectionSpacing = 12.dp
  val iconSize = 24.dp
}

/** 加载中状态: 居中转圈. */
@Composable
fun GitLoadingState(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator(
      color = MaterialTheme.colorScheme.primary,
      strokeWidth = 3.dp,
      modifier = Modifier.size(40.dp),
    )
  }
}

/** 空状态: 图标 + 提示文字.
 * @param icon 图标, 默认 Inbox
 * @param message 提示文字
 */
@Composable
fun GitEmptyState(
  message: String,
  icon: ImageVector = Icons.Outlined.Inbox,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(48.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      textAlign = TextAlign.Center,
    )
  }
}

/** 错误状态: 图标 + 错误信息 + 重试按钮.
 * @param message 错误信息
 * @param onRetry 重试回调
 */
@Composable
fun GitErrorState(
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Outlined.CloudOff,
      contentDescription = null,
      modifier = Modifier.size(48.dp),
      tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      text = message,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    TextButton(onClick = onRetry) {
      Text("重试")
    }
  }
}
