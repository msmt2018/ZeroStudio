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
import com.itsaky.androidide.projects.IProjectManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        title = context.getString(com.itsaky.androidide.resources.R.string.git_token_credential_title),
        subtitle = context.getString(com.itsaky.androidide.resources.R.string.git_token_credential_subtitle),
    ) {
      showTokenCredentialDialog()
      dismiss()
    }

    addDivider()
    addSectionTitle(context.getString(com.itsaky.androidide.resources.R.string.git_operations_section))
    addMenuItem(
        iconRes = R.drawable.ic_initialize_target_24dp,
        title = context.getString(com.itsaky.androidide.resources.R.string.git_init_title),
        subtitle = context.getString(com.itsaky.androidide.resources.R.string.git_init_subtitle),
    ) {
      initRepositoryIfNeeded()
      dismiss()
    }

    // 2c2: 新增"快捷设置"区段,把"设置用户名 / 邮箱"入口从原 header 点击
    // 迁出(原 header 是只读的,只显示 + 眼睛切换邮箱可见性)。
    addDivider()
    addSectionTitle(context.getString(com.itsaky.androidide.resources.R.string.git_quick_settings_section))
    addMenuItem(
        iconRes = com.itsaky.androidide.resources.R.drawable.ic_account,
        title = context.getString(com.itsaky.androidide.resources.R.string.git_set_user_info_title),
        subtitle = context.getString(com.itsaky.androidide.resources.R.string.git_set_user_info_subtitle),
    ) {
      showSetUserInfoDialog()
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

    // 2c2: header 改为只读 — 不再绑点击事件触发 showSetUserInfoDialog;
    // 该入口已迁到"快捷设置"区段的"设置用户名 / 邮箱"菜单项。

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

    // 2c1 修复:同步读当前 global config,作为 ComposeMutableState 的初值。
    // 之前用 `mutableStateOf("")` 会导致:
    //   1. 对话框打开瞬间字段空白,等 AskGitUsernameAndEmailDialog 内部
    //      LaunchedEffect 异步读完 PuppyGitSettings.json 后才填充;
    //   2. 竞态:用户手快在 LaunchedEffect 跑完前点"确定",会把空字符串
    //      当成新配置写回 global config,把之前配置好的 username/email
    //      清掉。
    val (currentUsername, currentEmail) = Libgit2Helper.getGitUsernameAndEmailFromGlobalConfig()

    val composeHostDialog = ComponentDialog(context)
    composeHostDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    composeHostDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

    val composeView =
        ComposeView(context).apply {
          setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

          setContent {
            // 状态变量,初值已是当前配置 — 避免空白闪 + 竞态覆盖
            val usernameState = remember { mutableStateOf(currentUsername) }
            val emailState = remember { mutableStateOf(currentEmail) }

            val titleRes = com.catpuppyapp.puppygit.play.pro.R.string.set_global_username_and_email

            AskGitUsernameAndEmailDialog(
                title = stringResource(titleRes),
                text = stringResource(titleRes),
                username = usernameState,
                email = emailState,
                isForGlobal = true, // 标记为全局设置,触发内部的全局读取逻辑
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
                  // 允许点击确定,即使为空(可能代表清除配置)
                  true
                },
            )
          }
        }

    composeHostDialog.setContentView(composeView)
    composeHostDialog.show()
  }

  private fun showTokenCredentialDialog() {
    GitTokenInputDialog(context).show {
      Msg.requireShowLongDuration(context.getString(com.itsaky.androidide.resources.R.string.git_token_updated))
    }
  }

  private fun initRepositoryIfNeeded() {
    val projectDir = IProjectManager.getInstance().getWorkspace()?.getProjectDir()?.path
    val repoPath =
        projectDir?.takeIf { it.isNotBlank() }
            ?: IProjectManager.getInstance().projectDirPath.takeIf { it.isNotBlank() }

    if (repoPath.isNullOrBlank()) {
      Msg.requireShowLongDuration(context.getString(com.itsaky.androidide.resources.R.string.git_no_opened_project))
      return
    }

    CoroutineScope(Dispatchers.IO).launch {
      val msg =
          runCatching {
                val gitDir = java.io.File(repoPath, ".git")
                if (gitDir.exists()) {
                  context.getString(com.itsaky.androidide.resources.R.string.git_repository_already_initialized)
                } else {
                  Libgit2Helper.initGitRepo(repoPath, false)
                  context.getString(com.itsaky.androidide.resources.R.string.git_repository_initialized)
                }
              }
              .getOrElse {
                it.localizedMessage
                    ?: context.getString(com.itsaky.androidide.resources.R.string.git_init_failed)
              }

      withContext(Dispatchers.Main) { Msg.requireShowLongDuration(msg) }
    }
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

  private fun addSectionTitle(title: String) {
    val textView =
        TextView(context).apply {
          text = title
          setTextColor(
              com.google.android.material.color.MaterialColors.getColor(
                  this,
                  com.google.android.material.R.attr.colorOnSurfaceVariant,
              )
          )
          textSize = 12f
          setPadding(16, 10, 16, 6)
        }
    container?.addView(textView)
  }

  fun dismiss() {
    popupWindow?.dismiss()
  }
}
