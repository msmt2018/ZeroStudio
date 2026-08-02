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
import com.itsaky.androidide.actions.requireContext
import com.itsaky.androidide.actions.requireFile
import com.itsaky.androidide.fragments.git.tree.FileTreeGitOps
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * git restore --staged <file> — 取消暂存 (从 index 移除, 工作区不动)。
 * 复刻 puppygit `Libgit2Helper.removePathSpecListFromIndexThenWriteToDisk`。
 *
 * @author android_zero
 */
class GitUnstageAction(context: Context, override val order: Int) :
    BaseFileTreeGitAction(
        context,
        labelRes = R.string.git_action_unstage,
        iconRes = R.drawable.ic_git_unstage,
    ) {

  override val id: String = "ide.editor.fileTree.git.unstage"

  override suspend fun execAction(data: ActionData) {
    val file = data.requireFile()
    val ctx = data.requireContext()
    val result = withContext(Dispatchers.IO) { FileTreeGitOps.unstageFile(file) }
    when (result) {
      is FileTreeGitOps.GitOpResult.Ok -> flashSuccess(R.string.git_msg_unstage_success)
      is FileTreeGitOps.GitOpResult.Err -> flashError(
          ctx.getString(R.string.git_msg_op_failed, result.msg)
      )
    }
  }
}
