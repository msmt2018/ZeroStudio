/*
 *  共享的 Git Compose UI 组件 —— 独立设计, 不依赖 puppygit 的 UI 层.
 *
 *  提供统一的 Loading / Empty / Error 状态组件, 以及统一的颜色/间距常量,
 *  让所有 Git 子页面视觉风格一致, 有呼吸感.
 */
package com.itsaky.androidide.fragments.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

// ===================== 协作子页面共享组件 =====================

/** Web 链接操作卡片数据. */
data class GitWebLinkAction(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit,
)

/**
 * 协作子页面 (PR / Pipelines / CodeReview) 共享的 Web 链接页面.
 *
 * UI 结构:
 * - 无远程仓库: 空状态提示
 * - 有远程仓库: 远程仓库信息卡片 + 页面标题/描述 + 操作卡片列表
 *
 * 每张操作卡片: 图标 + 标题 + 描述 + 右侧箭头, 点击调用 [GitWebLinkAction.onClick].
 *
 * @param pageTitle 页面标题 (如 "Pull Requests")
 * @param pageDescription 页面描述
 * @param links 已解析的 web 链接, null 表示无远程仓库
 * @param actions 操作卡片列表 (由各 Fragment 构造, onClick 内部处理跳转)
 */
@Composable
fun GitWebLinkPage(
    pageTitle: String,
    pageDescription: String,
    links: GitHostLinks?,
    actions: List<GitWebLinkAction>,
    modifier: Modifier = Modifier,
) {
  if (links == null) {
    GitEmptyState(
        message = "未检测到远程仓库\n请配置 .git/config 中的 remote",
        icon = Icons.Outlined.CloudOff,
        modifier = modifier,
    )
    return
  }

  LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // 远程仓库信息卡片
    item(key = "remote_info") {
      RemoteInfoCard(links = links)
    }

    // 页面标题 + 描述
    item(key = "page_header") {
      Column {
        Text(
            text = pageTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = pageDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      }
    }

    // 操作卡片列表
    items(actions, key = { it.title }) { action ->
      WebLinkActionCard(action = action, onClick = action.onClick)
    }
  }
}

/** 远程仓库信息卡片: 显示 host 类型 + base URL. */
@Composable
private fun RemoteInfoCard(links: GitHostLinks) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
          ),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Outlined.Cloud,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp),
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = hostDisplayName(links.hostKind),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = links.baseHttpUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/** 单个操作卡片: 图标 + 标题 + 描述 + 右侧箭头, 点击触发回调. */
@Composable
private fun WebLinkActionCard(action: GitWebLinkAction, onClick: () -> Unit) {
  Card(
      modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          ),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
        modifier =
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = action.icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(28.dp),
      )
      Spacer(Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = action.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = action.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      }
      Icon(
          imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
          modifier = Modifier.size(20.dp),
      )
    }
  }
}

/** 主机类型显示名. */
private fun hostDisplayName(kind: GitHostKind): String =
    when (kind) {
      GitHostKind.GITHUB -> "GitHub"
      GitHostKind.GITLAB -> "GitLab"
      GitHostKind.GITEE -> "Gitee"
      GitHostKind.UNKNOWN -> "Git Remote"
    }
