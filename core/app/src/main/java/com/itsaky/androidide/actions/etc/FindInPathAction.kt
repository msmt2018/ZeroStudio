/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.actions.etc

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorActivityAction
import com.itsaky.androidide.fragments.sheets.FindInPathDialog

/**
 * 用于打开高级搜索对话框的 Action.
 *
 * 原本以独立 toolbar action 形式注册在 [com.itsaky.androidide.utils.EditorActivityActions]
 * 中, 现已迁移为 [FindActionMenu] 的子项, 与 [FindInFileAction] 和 [FindInProjectAction]
 * 一起收纳在搜索子菜单里.
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

    if (activity.supportFragmentManager.findFragmentByTag(TAG_DIALOG) != null) {
      return false
    }

    val dialog = FindInPathDialog()
    dialog.show(activity.supportFragmentManager, TAG_DIALOG)
    return true
  }

  /**
   * The parent [FindActionMenu] is collapsed into the toolbar overflow menu, so this child action
   * should never be shown directly in the toolbar.
   */
  override fun getShowAsActionFlags(data: ActionData): Int = MenuItem.SHOW_AS_ACTION_NEVER

  companion object {
    private const val TAG_DIALOG = "FindInPathDialog"
  }
}
