package com.itsaky.androidide.actions

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [ComposeSidebarActionItem] 的抽象基础实现。
 * 兼容通用 [ActionItem] 协议的同时，将核心逻辑切换为纯粹的 Compose 声明式状态流。
 *
 * @author android_zero
 */
abstract class AbstractComposeSidebarAction : ComposeSidebarActionItem {

  override var requiresUIThread: Boolean = true
  override var visible: Boolean = true
  override var enabled: Boolean = true
  override var icon: Drawable? = null
  override var label: String = ""

  // 严格归属于编辑器侧滑栏抽屉位置
  final override var location: ActionItem.Location = ActionItem.Location.EDITOR_SIDEBAR

  override suspend fun execAction(data: ActionData): Any {
    // 侧边栏页面由 Compose 自身的状态驱动，无需传统的反射执行逻辑
    return true
  }

  @Composable
  abstract override fun Content(modifier: Modifier)
}