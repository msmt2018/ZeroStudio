package com.itsaky.androidide.actions.sidebar

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Commit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.AbstractComposeSidebarAction
import com.itsaky.androidide.actions.ComposeActionVisuals
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.ui.screen.git.GitHostScreen

/**
 * 现代面向 Jetpack Compose 的移动端完整 Git 管理侧滑栏 Action。
 */
class GitSidebarAction(
    context: Context,
    override val order: Int,
) : AbstractComposeSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.git"
  }

  override val id: String = ID

  override val visuals: ComposeActionVisuals = ComposeActionVisuals(
      imageVector = Icons.Default.Commit,
      enablePressHaptic = true,
      enableScaleAnimation = true,
  )

  init {
    label = context.getString(R.string.title_git) ?: "Git"
    icon = ContextCompat.getDrawable(context, R.drawable.ic_git)
  }

  @Composable
  override fun Content(modifier: Modifier) {
    val activity = LocalContext.current as? EditorHandlerActivity
    if (activity != null) {
      // 100% 满屏展示，不受任何伪 padding 与固定伪 Header 束缚
      GitHostScreen(activity = activity)
    }
  }
}