package com.itsaky.androidide.fragments.git.menu

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.ComponentDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.core.view.isVisible
import com.catpuppyapp.puppygit.compose.AskGitUsernameAndEmailDialog
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.catpuppyapp.puppygit.utils.Msg
import com.catpuppyapp.puppygit.utils.doJobThenOffLoading
import com.itsaky.androidide.R

/**
 * Git 弹出菜单管理器 (Native View 实现)。
 *
 * @author android_zero
 */
class GitPopupManager(private val context: Context) {

  private var popupWindow: PopupWindow? = null
  private var container: LinearLayout? = null

  // 状态缓存
  private var isEmailHidden = true // 默认隐藏邮箱
  private var currentEmail = ""
  private var currentUsername = ""

  // 视图缓存 (用于更新)
  private var tvUsername: TextView? = null
  private var tvEmail: TextView? = null
  private var tvAvatar: TextView? = null
  private var btnEye: ImageView? = null

  fun show(anchor: View) {
    val rootView = LayoutInflater.from(context).inflate(R.layout.layout_git_popup_menu_v2, null)
    container = rootView.findViewById(R.id.popup_container)

    setupHeader()
    addDivider()

    addMenuItem(
        iconRes = R.drawable.ic_key_24,
        title = context.getString(R.string.credentials),
        subtitle = "Manage HTTPS credentials",
    ) {
      Msg.requireShowLongDuration("Credentials manager is under construction")
      dismiss()
    }

    addMenuItem(
        iconRes = R.drawable.ic_lock_24,
        title = context.getString(R.string.ssh_credential),
        subtitle = "Manage SSH keys",
    ) {
      Msg.requireShowLongDuration("SSH manager is under construction")
      dismiss()
    }

    addDivider()

    addMenuItem(iconRes = R.drawable.ic_settings_24, title = context.getString(R.string.settings)) {
      Msg.requireShowLongDuration("Git settings page is under construction")
      dismiss()
    }

    val displayMetrics = context.resources.displayMetrics
    val width =
        (displayMetrics.widthPixels * 0.7)
            .toInt()
            .coerceAtLeast((280 * displayMetrics.density).toInt())

    popupWindow =
        PopupWindow(rootView, width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
          setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
          elevation = 16f
          isOutsideTouchable = true
        }

    popupWindow?.showAsDropDown(anchor, 0, 0)

    refreshUserInfo()
  }

  private fun setupHeader() {
    val headerView =
        LayoutInflater.from(context).inflate(R.layout.item_git_popup_header, container, false)

    tvAvatar = headerView.findViewById(R.id.tv_avatar)
    tvUsername = headerView.findViewById(R.id.tv_username)
    tvEmail = headerView.findViewById(R.id.tv_email)
    btnEye = headerView.findViewById(R.id.btn_toggle_email)

    btnEye?.setOnClickListener {
      isEmailHidden = !isEmailHidden
      updateEmailDisplay()
    }

    headerView.setOnClickListener { showSetUserInfoDialog() }

    container?.addView(headerView)
  }

  /** 刷新用户信息 读取 PuppyGitSettings.json 中的全局配置 */
  private fun refreshUserInfo() {
    try {
      // 使用 Libgit2Helper 读取全局配置
      val info = Libgit2Helper.getGitUsernameAndEmailFromGlobalConfig()

      val name = info.first
      val email = info.second

      currentUsername = if (name.isBlank()) context.getString(R.string.username) else name
      currentEmail = if (email.isBlank()) context.getString(R.string.email) else email

      tvUsername?.text = currentUsername
      updateEmailDisplay()

      if (currentUsername.isNotEmpty() && currentUsername != context.getString(R.string.username)) {
        tvAvatar?.text = currentUsername.first().uppercase()
      } else {
        tvAvatar?.text = "?"
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun updateEmailDisplay() {
    if (isEmailHidden) {
      val masked =
          if (currentEmail.length > 3 && currentEmail.contains("@")) {
            val prefix = currentEmail.substring(0, 1)
            val domainIndex = currentEmail.indexOf('@')
            val suffix = if (domainIndex > 0) currentEmail.substring(domainIndex) else ""
            "$prefix****$suffix"
          } else {
            "****"
          }
      tvEmail?.text = masked
      btnEye?.setImageResource(R.drawable.ic_visibility_off_24)
    } else {
      tvEmail?.text = currentEmail
      btnEye?.setImageResource(R.drawable.ic_visibility_24)
    }
  }

  /** 显示全局用户信息设置对话框 使用 ComponentDialog 承载 Compose 源码内容 */
  private fun showSetUserInfoDialog() {
    dismiss() // 关闭 Popup

    val composeHostDialog = ComponentDialog(context)
    composeHostDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    composeHostDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

    val composeView =
        ComposeView(context).apply {
          setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

          setContent {
            // 状态变量，用于接收和修改用户名/邮箱
            // AskGitUsernameAndEmailDialog 内部的 LaunchedEffect 会自动读取 PuppyGitSettings.json 并填充这些变量
            val usernameState = remember { mutableStateOf("") }
            val emailState = remember { mutableStateOf("") }

            val titleRes = com.catpuppyapp.puppygit.play.pro.R.string.set_global_username_and_email

            AskGitUsernameAndEmailDialog(
                title = stringResource(titleRes),
                text = stringResource(titleRes),
                username = usernameState,
                email = emailState,
                isForGlobal = true, // 标记为全局设置，触发内部的全局读取逻辑
                repos = emptyList(), // 全局设置不需要传仓库列表
                onOk = {
                  doJobThenOffLoading {
                    val success =
                        Libgit2Helper.saveGitUsernameAndEmailForGlobal(
                            requireShowErr = { msg -> Msg.requireShowLongDuration(msg) },
                            username = usernameState.value,
                            email = emailState.value,
                        )

                    if (success) {
                      Msg.requireShow(context.getString(R.string.saved))
                      composeHostDialog.dismiss()
                      refreshUserInfo()
                    }
                  }
                },
                onCancel = { composeHostDialog.dismiss() },
                enableOk = {
                  // 允许点击确定，即使为空（可能代表清除配置
                  true
                },
            )
          }
        }

    composeHostDialog.setContentView(composeView)
    composeHostDialog.show()
  }

  private fun addMenuItem(
      iconRes: Int = 0,
      title: String,
      subtitle: String? = null,
      onClick: () -> Unit,
  ) {
    val itemView =
        LayoutInflater.from(context).inflate(R.layout.item_git_popup_menu, container, false)

    val ivIcon = itemView.findViewById<ImageView>(R.id.iv_icon)
    val tvTitle = itemView.findViewById<TextView>(R.id.tv_title)
    val tvSubtitle = itemView.findViewById<TextView>(R.id.tv_subtitle)

    tvTitle.text = title

    if (iconRes != 0) {
      ivIcon.setImageResource(iconRes)
      ivIcon.isVisible = true
    } else {
      ivIcon.isVisible = false
    }

    if (!subtitle.isNullOrEmpty()) {
      tvSubtitle.text = subtitle
      tvSubtitle.isVisible = true
    } else {
      tvSubtitle.isVisible = false
    }

    itemView.setOnClickListener { onClick() }

    container?.addView(itemView)
  }

  private fun addDivider() {
    val divider =
        LayoutInflater.from(context).inflate(R.layout.item_git_popup_divider, container, false)
    container?.addView(divider)
  }

  fun dismiss() {
    popupWindow?.dismiss()
  }
}
