/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.actions.etc

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorActivityAction

/**
 * 用于打开高级搜索对话框的 Action.
 *
 * @author android_zero
 */
class FindInPathAction() : EditorActivityAction() {

  override val id: String = "ide.action.find.in.path"
  override var requiresUIThread: Boolean = true
  override var order: Int = 0

  constructor(context: Context, order: Int) : this() {
    this.label = context.getString(R.string.menu_find_in_path)
    this.icon = ContextCompat.getDrawable(context, R.drawable.ic_search)
    this.order = order
  }

  override suspend fun execAction(data: ActionData): Any {
    val activity = data.getActivity() ?: return false
    // 直接调用 Compose 弹窗句柄，彻底抛弃 supportFragmentManager
    activity.findInProjectDialog.show()
    return true
  }

  override fun getShowAsActionFlags(data: ActionData): Int = MenuItem.SHOW_AS_ACTION_NEVER
}