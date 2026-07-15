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
import androidx.core.content.edit
import com.catpuppyapp.puppygit.data.entity.CredentialEntity
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Git 凭据管理 (用户名/邮箱/Token), 用于支持 push/pull 认证。
 *
 * 文件树核心组件 — 通过 SharedPreferences 存储用户输入的 git 凭据, 并将
 * 用户名/邮箱同步到 git global config (供 puppygit Libgit2Helper 使用)。
 *
 * 恢复自 fragments/git/GitCredentialManager.kt (commit 4b81f60c 删除),
 * 迁移到 fragments/editor/filetree/ 路径以统一文件树相关代码组织。
 *
 * @author android_zero
 */
object GitCredentialManager {
  private const val PREFS = "git_auth_config"
  private const val KEY_USERNAME = "username"
  private const val KEY_EMAIL = "email"
  private const val KEY_TOKEN = "token"

  data class Config(val username: String, val email: String, val token: String) {
    fun isComplete(): Boolean = username.isNotBlank() && email.isNotBlank() && token.isNotBlank()
  }

  fun read(context: Context): Config {
    val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return Config(
        username = sp.getString(KEY_USERNAME, "").orEmpty(),
        email = sp.getString(KEY_EMAIL, "").orEmpty(),
        token = sp.getString(KEY_TOKEN, "").orEmpty(),
    )
  }

  fun ensureConfigured(context: Context, onConfigured: (Config) -> Unit) {
    val current = read(context)
    if (current.isComplete()) {
      onConfigured(current)
      return
    }
    showMaterialAuthDialog(context, current, onConfigured)
  }

  fun saveToken(context: Context, token: String): Config {
    val current = read(context)
    val cfg = current.copy(token = token.trim())
    save(context, cfg)
    return cfg
  }

  private fun showMaterialAuthDialog(
      context: Context,
      initial: Config,
      onConfigured: (Config) -> Unit,
  ) {
    val density = context.resources.displayMetrics.density
    val pad = (20 * density).toInt()

    val container =
        android.widget.LinearLayout(context).apply {
          orientation = android.widget.LinearLayout.VERTICAL
          setPadding(pad, (8 * density).toInt(), pad, 0)
        }

    fun field(hint: String, text: String, password: Boolean = false): TextInputEditText {
      val til =
          TextInputLayout(context).apply {
            this.hint = hint
            if (password) {
              endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            }
          }
      val et = TextInputEditText(context)
      et.setText(text)
      til.addView(et)
      container.addView(til)
      return et
    }

    val usernameEt = field("Git Username", initial.username)
    val emailEt = field("Git Email", initial.email)
    val tokenEt = field("Git Token", initial.token, password = true)

    MaterialAlertDialogBuilder(context)
        .setTitle("Git 凭据设置")
        .setMessage("请输入用于 Push/Pull 的用户名、邮箱和 Token。")
        .setView(container)
        .setPositiveButton("保存") { _, _ ->
          val cfg =
              Config(
                  usernameEt.text?.toString()?.trim().orEmpty(),
                  emailEt.text?.toString()?.trim().orEmpty(),
                  tokenEt.text?.toString()?.trim().orEmpty(),
              )
          save(context, cfg)
          onConfigured(cfg)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
  }

  private fun save(context: Context, cfg: Config) {
    val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    sp.edit {
      putString(KEY_USERNAME, cfg.username)
      putString(KEY_EMAIL, cfg.email)
      putString(KEY_TOKEN, cfg.token)
    }
    Libgit2Helper.saveGitUsernameAndEmailForGlobal({}, cfg.username, cfg.email)
  }

  fun toHttpCredential(cfg: Config): CredentialEntity {
    return CredentialEntity(name = "git-auth", value = cfg.username, pass = cfg.token)
  }
}
