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
package com.itsaky.androidide.actions.filetree

import android.content.Context
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.requireFile
import com.itsaky.androidide.fragments.git.GitScreenOpener
import com.itsaky.androidide.resources.R

/**
 * 打开 git 完整 UI 的 Status 标签 — 查看仓库改动列表。
 * 复刻 puppygit `IndexScreen` / ChangeList 入口。
 *
 * @author android_zero
 */
class GitStatusAction(context: Context, override val order: Int) :
    BaseFileTreeGitAction(
        context,
        labelRes = R.string.git_action_status,
        iconRes = R.drawable.ic_git,
    ) {

  override val id: String = "ide.editor.fileTree.git.status"

  override suspend fun execAction(data: ActionData) {
    @Suppress("UNUSED_VARIABLE")
    val _file = data.requireFile()
    val activity = data.requireActivity()
    GitScreenOpener.openGitUi(activity)
  }
}
