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

package com.itsaky.androidide.actions.sidebar

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.itsaky.androidide.R
import com.itsaky.androidide.fragments.git.GitProjectsFragment
import kotlin.reflect.KClass

/**
 * Sidebar action for showing the file tree (项目文件树).
 *
 * 注: fragmentClass 指向 [GitProjectsFragment] — IDE 文件树核心 fragment,
 * 位于 `fragments/git/`, 顶部自带 git 操作 toolbar (branch switcher / refresh /
 * locate / collapse / expand / undo / redo) + 文件树操作 + 长按文件菜单触发 git 操作。
 * 这是真正在用的文件树实现; [com.itsaky.androidide.fragments.sidebar.FileTreeFragment]
 * 已淘汰, 仅作为备用方案保留。
 *
 * 完整 git UI (变更/历史/分支/远程 4 个 tab) 走单独的 [GitSidebarAction] 入口,
 * 与文件树页面相互独立。
 *
 * @author Akash Yadav
 */
class FileTreeSidebarAction(context: Context, override val order: Int) : AbstractSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.fileTree"
  }

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment> = GitProjectsFragment::class

  init {
    icon = null
    // label = context.getString(R.string.msg_file_tree)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_folder)
  }
}
