package com.itsaky.androidide.actions.etc.image

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.KeyboardUtils
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.fragments.editor.image.ImagePreviewFragment
import com.itsaky.androidide.models.SaveResult
import com.itsaky.androidide.resources.R
import java.io.File

/**
 * Action menu item for previewing image files in a tab.
 *
 * This action opens an Image preview tab in the EditorHandlerActivity
 * when the user clicks the preview button in the editor toolbar.
 *
 * 支持的格式: PNG / JPG / WebP / GIF / HEIC / BMP / AVIF / ICO / TIFF /
 * SVG / SVGZ / Android XML vector drawable. 完整列表见
 * [ImagePreviewFragment.SUPPORTED_FORMATS]。
 *
 * **与 [com.itsaky.androidide.actions.etc.markdown.MarkdownPreviewAction] 分离**:
 * 之前图片预览复用了 Markdown 预览的 action button (MarkdownPreviewAction
 * 用 FragmentTabRegistry.getByFileExtension 匹配所有注册扩展名), 现在拆成
 * 独立的 ImagePreviewAction, 只匹配 [ImagePreviewFragment.SUPPORTED_FORMATS]。
 *
 * @author ZeroStudio
 */
class ImagePreviewAction(context: Context, override val order: Int) : EditorRelatedAction() {

  override val id: String = ID

  override var requiresUIThread: Boolean = false

  companion object {
    const val ID = "ide.editor.imagePreview"
  }

  init {
    label = context.getString(R.string.title_image_preview)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_file_type_image)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)

    val activity = data.getActivity() as? EditorHandlerActivity
    if (activity == null) {
      markInvisible()
      return
    }

    // 只匹配图片后缀 (不匹配 markdown / C/C++ 等其它预览类型)
    val editor = data.getEditor()
    val file = editor?.file

    if (file != null) {
      val extension = file.extension.lowercase()
      if (ImagePreviewFragment.SUPPORTED_FORMATS.contains(extension)) {
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
      openImagePreview(activity, file)
    }
  }

  /**
   * Opens the Image preview tab with the given file.
   *
   * @param activity The EditorHandlerActivity
   * @param file The image file to preview
   */
  private fun openImagePreview(activity: EditorHandlerActivity, file: File) {
    val extension = file.extension.lowercase()
    val fragmentTabManager = activity.fragmentTabManager ?: return

    fragmentTabManager.openFileTab(file.absolutePath, extension)
  }
}
