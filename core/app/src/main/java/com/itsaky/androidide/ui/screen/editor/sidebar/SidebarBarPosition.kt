package com.itsaky.androidide.ui.screen.editor.sidebar

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.itsaky.androidide.resources.R

/**
 * 侧滑栏抽屉内导航条的四向预制位置
 */
enum class SidebarBarPosition(
    val id: String,
    @StringRes val labelRes: Int,
) {
  TOP("top", R.string.sidebar_position_top),
  BOTTOM("bottom", R.string.sidebar_position_bottom),
  LEFT("left", R.string.sidebar_position_left),
  RIGHT("right", R.string.sidebar_position_right);

  val isVertical: Boolean
    get() = this == LEFT || this == RIGHT

  companion object {
    fun fromId(id: String?): SidebarBarPosition =
        entries.firstOrNull { it.id == id } ?: LEFT
  }
}