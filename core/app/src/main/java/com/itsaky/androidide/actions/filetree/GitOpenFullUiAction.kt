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
 * 打开 git 完整 UI (用于查看 diff / log / history / 全部 branches 等)。
 * 复刻 puppygit `DiffScreen` / `FileHistoryScreen` / `CommitListScreen` 等。
 *
 * @author android_zero
 */
class GitOpenFullUiAction(context: Context, override val order: Int) :
    BaseFileTreeGitAction(
        context,
        labelRes = R.string.git_action_open_full_git_ui,
        iconRes = R.drawable.ic_git,
    ) {

  override val id: String = "ide.editor.fileTree.git.openFullUi"

  override suspend fun execAction(data: ActionData) {
    val activity = data.requireActivity()
    // 文件级 Diff 比较复杂, 简化为打开完整 git UI,
    // 用户在 UI 里选择具体 file 来 diff 即可
    @Suppress("UNUSED_VARIABLE")
    val _file = data.requireFile()  // 保留语义, 未来可作"打开 diff 并预选该文件"用
    GitScreenOpener.openGitUi(activity)
  }
}
