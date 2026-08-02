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
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * git checkout -- <file> — 丢弃工作区的文件修改, 还原到 HEAD/index。
 * 复刻 puppygit `Libgit2Helper.checkoutFiles`。
 *
 * @author android_zero
 */
class GitRevertAction(context: Context, override val order: Int) :
    BaseFileTreeGitAction(
        context,
        labelRes = R.string.git_action_revert,
        iconRes = R.drawable.ic_git_revert,
    ) {

  override val id: String = "ide.editor.fileTree.git.revert"

  override suspend fun execAction(data: ActionData) {
    val file = data.requireFile()
    val activity = data.requireActivity()

    // 二次确认
    val builder = DialogUtils.newMaterialDialogBuilder(activity)
    builder.setTitle(R.string.git_action_revert)
        .setMessage(
            activity.getString(
                com.itsaky.androidide.resources.R.string.msg_confirm_delete,
                "${file.name} [${file.absolutePath}]",
            )
        )
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.git_action_revert) { d, _ ->
            d.dismiss()
            actionScope.launch {
                val result = withContext(Dispatchers.IO) { FileTreeGitOps.revertFile(file) }
                when (result) {
                    is FileTreeGitOps.GitOpResult.Ok -> flashSuccess(R.string.git_msg_revert_success)
                    is FileTreeGitOps.GitOpResult.Err -> flashError(
                        activity.getString(R.string.git_msg_op_failed, result.msg)
                    )
                }
            }
        }
        .show()
  }
}
