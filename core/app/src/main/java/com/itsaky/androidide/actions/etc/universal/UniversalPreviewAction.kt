package com.itsaky.androidide.actions.etc.universal

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.KeyboardUtils
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.models.SaveResult
import com.itsaky.androidide.resources.R
import com.zerostudio.preview.UniversalPreviewEngineFragment
import java.io.File

/**
 * Action menu item for previewing C/C++ source files in a 3D/2D universal
 * preview tab.
 *
 * This action opens a [UniversalPreviewEngineFragment] tab in the
 * EditorHandlerActivity when the user clicks the preview button in the
 * editor toolbar.
 *
 * 支持的格式: C/C++ 源码 (c / cpp / cc / cxx / h / hpp / hxx / cu /
 * glsl / frag / vert / comp / geom / tesc / tese / m / mm). 完整列表见
 * [UniversalPreviewEngineFragment.SUPPORTED_EXTENSIONS]。
 *
 * 双核架构:
 * - 核心A (WebView + Three.js): AST 拓扑 / 代码结构可视化
 * - 核心B (GLSurfaceView + JNI): 3D 模型渲染 / Dear ImGui 交互
 *
 * @author ZeroStudio
 */
class UniversalPreviewAction(context: Context, override val order: Int) : EditorRelatedAction() {

  override val id: String = ID

  override var requiresUIThread: Boolean = false

  companion object {
    const val ID = "ide.editor.universalPreview"
  }

  init {
    label = context.getString(R.string.title_universal_preview)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_code)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)

    val activity = data.getActivity() as? EditorHandlerActivity
    if (activity == null) {
      markInvisible()
      return
    }

    // 只匹配 C/C++ / GLSL 等源码后缀
    val editor = data.getEditor()
    val file = editor?.file

    if (file != null) {
      val extension = file.extension.lowercase()
      if (UniversalPreviewEngineFragment.SUPPORTED_EXTENSIONS.contains(extension)) {
        visible = true
        enabled = true
      } else {
        markInvisible()
      }
    } else {
      visible = true
      enabled = false
    }
  }

  override fun getShowAsActionFlags(data: ActionData): Int {
    val activity = data.getActivity() ?: return super.getShowAsActionFlags(data)
    return if (KeyboardUtils.isSoftInputVisible(activity)) {
      MenuItem.SHOW_AS_ACTION_IF_ROOM
    } else {
      MenuItem.SHOW_AS_ACTION_ALWAYS
    }
  }

  override suspend fun execAction(data: ActionData): Boolean {
    val activity = data.requireActivity() as? EditorHandlerActivity ?: return false
    val file = data.getEditor()?.file ?: return false
    val index = activity.findIndexOfEditorByFile(file)
    if (index >= 0) {
      activity.saveResult(index, SaveResult())
    }
    return true
  }

  override fun postExec(data: ActionData, result: Any) {
    val activity = data.requireActivity() as? EditorHandlerActivity ?: return
    val editor = data.getEditor()
    val file = editor?.file

    if (file != null && file.exists() && file.canRead()) {
      openUniversalPreview(activity, file)
    }
  }

  /**
   * Opens the Universal preview tab with the given file.
   *
   * @param activity The EditorHandlerActivity
   * @param file The C/C++ source file to preview
   */
  private fun openUniversalPreview(activity: EditorHandlerActivity, file: File) {
    val extension = file.extension.lowercase()
    val fragmentTabManager = activity.fragmentTabManager ?: return

    fragmentTabManager.openFileTab(file.absolutePath, extension)
  }
}
