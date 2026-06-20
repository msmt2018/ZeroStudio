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
 */
@Immutable
data class BubbleContent(
  val title: String,
  val subtitle: String = "",
  val icon: ImageVector? = null,
  val iconDescription: String? = null,
) {
  companion object {
    /** 仅有标题的简单气泡 */
    fun simple(title: String): BubbleContent = BubbleContent(title = title)

    /** 主标题 + 副标题 */
    fun withSubtitle(title: String, subtitle: String): BubbleContent =
      BubbleContent(title = title, subtitle = subtitle)

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
