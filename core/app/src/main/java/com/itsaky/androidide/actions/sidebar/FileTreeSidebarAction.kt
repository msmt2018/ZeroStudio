package com.itsaky.androidide.actions.sidebar

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.AbstractComposeSidebarAction
import com.itsaky.androidide.actions.ComposeActionVisuals
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.fragments.git.GitProjectsScreen

/**
 * 现代文件树侧边栏 Action。
 *
 * 彻底消除对任何 Fragment 的依赖，直接绑定高性能满屏 [GitProjectsScreen]，
 * 消除布局边界压缩，给文件树提供 100% 的水平操作空间。
 *
 * @author android_zero
 */
class FileTreeSidebarAction(
    context: Context,
    override val order: Int,
) : AbstractComposeSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.fileTree"
  }

  override val id: String = ID

  override val visuals: ComposeActionVisuals = ComposeActionVisuals(
      imageVector = Icons.Default.Folder,
      enablePressHaptic = true,
      enableScaleAnimation = true,
  )

  init {
    label = context.getString(R.string.msg_file_tree)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_folder)
  }

  @Composable
  override fun Content(modifier: Modifier) {
    val activity = LocalContext.current as? EditorHandlerActivity
    if (activity != null) {
      // 100% 满屏展示，不受旧容器 padding 束缚
      GitProjectsScreen(activity = activity)
    }
  }
}