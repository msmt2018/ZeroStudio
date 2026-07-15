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
import com.itsaky.androidide.fragments.git.tree.FileTreeGitOps
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * git fetch — 从所有 remote 拉取 (不合并)。
 * 复刻 puppygit `Libgit2Helper.fetchRemoteForRepo`。
 *
 * @author android_zero
 */
class GitFetchAction(context: Context, override val order: Int) :
    BaseFileTreeGitAction(
        context,
        labelRes = R.string.git_action_fetch,
        iconRes = R.drawable.ic_git_fetch,
    ) {

  override val id: String = "ide.editor.fileTree.git.fetch"

  override suspend fun execAction(data: ActionData) {
    @Suppress("UNUSED_VARIABLE")
    val _file = data.requireFile()
    val activity = data.requireActivity()
    actionScope.launch {
        val result = withContext(Dispatchers.IO) { FileTreeGitOps.fetch() }
        when (result) {
            is FileTreeGitOps.GitOpResult.Ok -> flashSuccess(R.string.git_msg_fetch_success)
            is FileTreeGitOps.GitOpResult.Err -> flashError(
                activity.getString(R.string.git_msg_op_failed, result.msg)
            )
        }
    }
  }
}
