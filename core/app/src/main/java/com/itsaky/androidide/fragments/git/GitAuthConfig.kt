package com.itsaky.androidide.fragments.git

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.edit
import com.catpuppyapp.puppygit.data.entity.CredentialEntity
import com.catpuppyapp.puppygit.utils.Libgit2Helper

object GitAuthConfig {
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
    showConfigDialog(context, current, onConfigured)
  }

  private fun showConfigDialog(context: Context, initial: Config, onConfigured: (Config) -> Unit) {
    val container = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      val pad = (16 * resources.displayMetrics.density).toInt()
      setPadding(pad, pad, pad, 0)
    }
    val usernameEt = EditText(context).apply { hint = "Git Username"; setText(initial.username) }
    val emailEt = EditText(context).apply { hint = "Git Email"; setText(initial.email) }
    val tokenEt = EditText(context).apply { hint = "Git Token / Password"; setText(initial.token) }

    container.addView(usernameEt)
    container.addView(emailEt)
    container.addView(tokenEt)

    AlertDialog.Builder(context)
        .setTitle("Configure Git Identity")
        .setMessage("Push/Pull 前需要配置 username、email、token。")
        .setView(container)
        .setPositiveButton("Save") { _, _ ->
          val cfg =
              Config(
                  usernameEt.text.toString().trim(),
                  emailEt.text.toString().trim(),
                  tokenEt.text.toString().trim(),
              )
          val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
          sp.edit {
            putString(KEY_USERNAME, cfg.username)
            putString(KEY_EMAIL, cfg.email)
            putString(KEY_TOKEN, cfg.token)
          }
          Libgit2Helper.saveGitUsernameAndEmailForGlobal({}, cfg.username, cfg.email)
          onConfigured(cfg)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
  }

  fun toHttpCredential(cfg: Config): CredentialEntity {
    return CredentialEntity(name = "git-auth", value = cfg.username, pass = cfg.token)
  }
}
