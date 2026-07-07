package com.itsaky.androidide.actions.etc.markdown

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.KeyboardUtils
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.fragments.editor.markdown.MarkdownPreviewFragment
import com.itsaky.androidide.models.SaveResult
import com.itsaky.androidide.resources.R
import java.io.File

/**
 * Action menu item for previewing Markdown files in a tab.
 *
 * This action opens a Markdown preview tab in the EditorHandlerActivity
 * when the user clicks the preview button in the editor toolbar.
 *
 * **注意**: 只匹配 [MarkdownPreviewFragment.SUPPORTED_EXTENSIONS] 中的
 * 文件后缀 (md / markdown / mkd 等), 不匹配 image / C/C++ 等其它预览类型。
 * 图片预览见 [com.itsaky.androidide.actions.etc.image.ImagePreviewAction],
 * C/C++ 3D 预览见 [com.itsaky.androidide.actions.etc.universal.UniversalPreviewAction]。
 *
 * @author ZeroStudio
 */
class MarkdownPreviewAction(context: Context, override val order: Int) : EditorRelatedAction() {

  override val id: String = ID

  override var requiresUIThread: Boolean = false

  companion object {
    const val ID = "ide.editor.markdownPreview"
  }

  init {
    label = context.getString(R.string.title_markdown_preview)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_markdown_preview)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)

    val activity = data.getActivity() as? EditorHandlerActivity
    if (activity == null) {
      markInvisible()
      return
    }

    // 只匹配 Markdown 后缀 (不匹配 image / C/C++ 等其它预览类型)
    val editor = data.getEditor()
    val file = editor?.file

    if (file != null) {
      val extension = file.extension.lowercase()
      if (MarkdownPreviewFragment.SUPPORTED_EXTENSIONS.contains(extension)) {
        visible = true
        enabled = true
      } else {
        markInvisible()
      }
    } else {
      // No file open
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
      openMarkdownPreview(activity, file)
    }
  }

  /**
   * Opens the Markdown preview tab with the given file.
   *
   * @param activity The EditorHandlerActivity
   * @param file The Markdown file to preview
   */
  private fun openMarkdownPreview(activity: EditorHandlerActivity, file: File) {
    val extension = file.extension.lowercase()
    val fragmentTabManager = activity.fragmentTabManager ?: return

    // Open the file tab using the fragment tab manager
    fragmentTabManager.openFileTab(file.absolutePath, extension)
  }
}
