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

package com.itsaky.androidide.utils

import android.content.Context
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.actions.sidebar.BuildVariantsSidebarAction
import com.itsaky.androidide.actions.sidebar.CloseProjectSidebarAction
import com.itsaky.androidide.actions.sidebar.DataFileTreeSidebarAction
import com.itsaky.androidide.actions.sidebar.EditorToolboxSidebarAction
import com.itsaky.androidide.actions.sidebar.FileTreeSidebarAction
import com.itsaky.androidide.actions.sidebar.GitSidebarAction
import com.itsaky.androidide.actions.sidebar.PreferencesSidebarAction
import com.itsaky.androidide.actions.sidebar.TerminalSidebarAction
import com.itsaky.androidide.actions.sidebar.TerminalHostSidebarAction

/**
 * Sets up the actions that are shown in the
 * [EditorActivityKt][com.itsaky.androidide.activities.editor.EditorActivityKt]'s drawer's sidebar.
 *
 * @author Akash Yadav
 * @author android_zero (Refactor)
 */
internal object EditorSidebarActions {

  @JvmStatic
  fun registerActions(context: Context) {
    val registry = ActionsRegistry.getInstance()
    var order = -1

    @Suppress("KotlinConstantConditions")
    registry.registerAction(FileTreeSidebarAction(context, ++order))
    // 完整 Git UI 容器 (diff/branches/history/changes/stash/conflict/
    // repositories/settings/tags/interactive-rebase) — 与文件树页面相互独立。
    registry.registerAction(GitSidebarAction(context, ++order))
    registry.registerAction(DataFileTreeSidebarAction(context, ++order))
    registry.registerAction(BuildVariantsSidebarAction(context, ++order))
    EditorToolboxActions.registerActions(context)
    registry.registerAction(EditorToolboxSidebarAction(context, ++order))
    registry.registerAction(TerminalSidebarAction(context, ++order))
    registry.registerAction(TerminalHostSidebarAction(context, ++order))
    registry.registerAction(PreferencesSidebarAction(context, ++order))
    registry.registerAction(CloseProjectSidebarAction(context, ++order))
  }

}
