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
 * git pull — fetch + merge upstream 到当前 HEAD。
 * 复刻 puppygit `Libgit2Helper.mergeOrRebase` + `fetchRemoteForRepo`。
 *
 * @author android_zero
 */
class GitPullAction(context: Context, override val order: Int) :
    BaseFileTreeGitAction(
        context,
        labelRes = R.string.git_action_pull,
        iconRes = R.drawable.ic_git_pull,
    ) {

  override val id: String = "ide.editor.fileTree.git.pull"

  override suspend fun execAction(data: ActionData) {
    @Suppress("UNUSED_VARIABLE")
    val _file = data.requireFile()  // 仓库级 action, 文件可作触发入口但不影响 pull
    val activity = data.requireActivity()
    actionScope.launch {
        val result = withContext(Dispatchers.IO) { FileTreeGitOps.pull() }
        when (result) {
            is FileTreeGitOps.GitOpResult.Ok -> flashSuccess(R.string.git_msg_pull_success)
            is FileTreeGitOps.GitOpResult.Err -> flashError(
                activity.getString(R.string.git_msg_op_failed, result.msg)
            )
        }
    }
  }
}
