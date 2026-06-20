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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 气泡内容 (Bubble content).
 *
 * 包含主标题 + 副标题 + 可选图标. 图标默认不设置, 只在需要时传入.
 * 标题和副标题均为必填, 但副标题可以传空字符串表示"无副标题".
 *
 * 设计理念 (参考 dribbble):
 *  - 标题: 粗体, 大字号, 高对比度
 *  - 副标题: 常规体, 较小字号, 半透明
 *  - 图标: 16-24dp, 与标题水平排列, 提供视觉锚点
 *
 * [onSubtitleClick] 为可点击副标题提供钩子:
 *  - 设置为非 null 后, 整段副标题会变为可点击 (ripple + 高亮)
 *  - 用户点击时调用此回调, 不会冒泡触发引导浮层外层的"下一步"
 *  - 典型用法: 副标题中描述"点此隐藏某区域", 点击后触发对应动作 + 视觉反馈
 */
@Immutable
data class BubbleContent(
  val title: String,
  val subtitle: String = "",
  val icon: ImageVector? = null,
  val iconDescription: String? = null,
  val onSubtitleClick: (() -> Unit)? = null,
) {
  /** 副标题是否可点击 (有 onSubtitleClick 回调) */
  val isSubtitleClickable: Boolean get() = onSubtitleClick != null

  companion object {
    /** 仅有标题的简单气泡 */
    fun simple(title: String): BubbleContent = BubbleContent(title = title)

    /** 主标题 + 副标题 */
    fun withSubtitle(title: String, subtitle: String): BubbleContent =
      BubbleContent(title = title, subtitle = subtitle)

    /** 主标题 + 副标题 + 点击副标题触发回调 */
    fun withClickableSubtitle(
      title: String,
      subtitle: String,
      onSubtitleClick: () -> Unit,
    ): BubbleContent = BubbleContent(
      title = title,
      subtitle = subtitle,
      onSubtitleClick = onSubtitleClick,
    )

    /** 主标题 + 图标 */
    fun withIcon(
      title: String,
      icon: ImageVector,
      iconDescription: String? = null,
    ): BubbleContent = BubbleContent(title = title, icon = icon, iconDescription = iconDescription)

    /** 完整: 主标题 + 副标题 + 图标 */
    fun full(
      title: String,
      subtitle: String,
      icon: ImageVector,
      iconDescription: String? = null,
    ): BubbleContent = BubbleContent(
      title = title,
      subtitle = subtitle,
      icon = icon,
      iconDescription = iconDescription,
    )
  }
}

/** 内部用于与 Composable 上下文对齐的辅助工具 */
object BubbleContentDefaults {
  /** 默认标题文本样式 */
  @Composable
  fun titleStyle(): androidx.compose.ui.text.TextStyle =
    androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
      fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    )

  /** 默认副标题文本样式 */
  @Composable
  fun subtitleStyle(): androidx.compose.ui.text.TextStyle =
    androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
      fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
    )
}
