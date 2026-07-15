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
import com.itsaky.androidide.ui.screen.git.ComposeHostFragment
import kotlin.reflect.KClass

/**
 * Sidebar action for showing the full git UI (完整 git 系统).
 *
 * 注: 打开 [ComposeHostFragment] — git UI Compose 宿主, 内部渲染 [com.itsaky.androidide.ui.screen.git.GitHostScreen]
 * (变更/历史/分支/远程 4 个 tab, 跳转至 puppygit 的 CommitListScreen / BranchListScreen /
 * RemoteListScreen / IndexScreen 等)。
 *
 * 这是与文件树页面 ([FileTreeSidebarAction] 打开的 [GitProjectsFragment]) 相互独立的入口。
 * FileTreeSidebarAction 负责文件树 + 顶部 git 操作 toolbar; GitSidebarAction 负责完整 git UI。
 *
 * @author android_zero
 */
class GitSidebarAction(context: Context, override val order: Int) : AbstractSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.git"
  }

  override val id: String = ID
  override val fragmentClass: KClass<out Fragment> = ComposeHostFragment::class

  init {
    icon = null
    label = context.getString(com.itsaky.androidide.resources.R.string.title_git)
    icon = ContextCompat.getDrawable(context, com.itsaky.androidide.resources.R.drawable.ic_git)
  }
}
