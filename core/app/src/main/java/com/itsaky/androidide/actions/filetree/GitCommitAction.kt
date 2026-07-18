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
import android.view.LayoutInflater
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.requireFile
import com.itsaky.androidide.fragments.git.tree.FileTreeGitOps
import com.itsaky.androidide.preferences.databinding.LayoutDialogTextInputBinding
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.utils.DialogUtils
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * git commit -m "..." — 创建提交 (使用 index 中已暂存的内容)。
 * 复刻 puppygit `Libgit2Helper.createCommit`。
 *
 * 注意: 提交前需先 git add 暂存。
 *
 * @author android_zero
 */
class GitCommitAction(context: Context, override val order: Int) :
    BaseFileTreeGitAction(
        context,
        labelRes = R.string.git_action_commit,
        iconRes = R.drawable.ic_git_commit,
    ) {

  override val id: String = "ide.editor.fileTree.git.commit"

  override suspend fun execAction(data: ActionData) {
    @Suppress("UNUSED_VARIABLE")
    val _file = data.requireFile()
    val activity = data.requireActivity()

    val binding = LayoutDialogTextInputBinding.inflate(LayoutInflater.from(activity))
    binding.name.editText?.hint = activity.getString(R.string.git_msg_commit_message_hint)
    binding.name.editText?.setText("")

    val builder = DialogUtils.newMaterialDialogBuilder(activity)
    builder.setTitle(R.string.git_msg_commit_message_title)
        .setView(binding.root)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.git_action_commit) { d, _ ->
            d.dismiss()
            val msg = binding.name.editText?.text?.toString().orEmpty().trim()
            if (msg.isEmpty()) {
                flashError(R.string.git_msg_commit_message_hint)
                return@setPositiveButton
            }
            actionScope.launch {
                val result = withContext(Dispatchers.IO) { FileTreeGitOps.commit(msg) }
                when (result) {
                    is FileTreeGitOps.GitOpResult.Ok -> flashSuccess(R.string.git_msg_commit_success)
                    is FileTreeGitOps.GitOpResult.Err -> flashError(
                        activity.getString(R.string.git_msg_op_failed, result.msg)
                    )
                }
            }
        }
        .show()
  }
}
