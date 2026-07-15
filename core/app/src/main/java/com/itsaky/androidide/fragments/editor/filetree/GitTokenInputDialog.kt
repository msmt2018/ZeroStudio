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

package com.itsaky.androidide.fragments.editor.filetree

import android.content.Context
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.itsaky.androidide.resources.R

/**
 * Git Token 输入对话框 — IDE 文件树核心组件。
 *
 * 从 SharedPreferences 读取已保存的 token 并提供修改入口, 保存后通过
 * [GitCredentialManager.saveToken] 写回 (同时同步 username/email 到
 * Libgit2Helper global config)。
 *
 * 恢复自 fragments/git/menu/GitTokenInputDialog.kt (commit 4b81f60c 删除),
 * 迁移到 fragments/editor/filetree/ 路径以统一文件树相关代码组织。
 *
 * @author android_zero
 */
class GitTokenInputDialog(private val context: Context) {

  fun show(onSaved: (() -> Unit)? = null) {
    val initial = GitCredentialManager.read(context)
    val density = context.resources.displayMetrics.density
    val pad = (20 * density).toInt()

    val container =
        LinearLayout(context).apply {
          orientation = LinearLayout.VERTICAL
          setPadding(pad, (8 * density).toInt(), pad, 0)
        }

    val tokenLayout =
        TextInputLayout(context).apply {
          hint = context.getString(R.string.git_token_input_hint)
          endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
    val tokenEt = TextInputEditText(context).apply { setText(initial.token) }
    tokenLayout.addView(tokenEt)
    container.addView(tokenLayout)

    MaterialAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.git_token_dialog_title))
        .setMessage(context.getString(R.string.git_token_dialog_message))
        .setView(container)
        .setPositiveButton(context.getString(R.string.git_save)) { _, _ ->
          GitCredentialManager.saveToken(context, tokenEt.text?.toString().orEmpty())
          onSaved?.invoke()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
  }
}
