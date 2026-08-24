package com.itsaky.androidide.actions.etc.video

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.KeyboardUtils
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.activities.editor.EditorActivityKt
import com.itsaky.androidide.fragments.editor.video.VideoPreviewFragment
import com.itsaky.androidide.models.SaveResult
import com.itsaky.androidide.resources.R
import java.io.File

/**
 * Action menu item for previewing video files in a tab.
 *
 * This action opens a Video preview tab in the EditorActivityKt
 * when the user clicks the preview button in the editor toolbar.
 *
 * 支持的格式: mp4 / mkv / webm / avi / mov / 3gp / mpg / mpeg / ts /
 * m2ts / flv / wmv / m4v / vob / ogv. 完整列表见
 * [VideoPreviewFragment.SUPPORTED_EXTENSIONS]。
 *
 * 预览 UI 采用半透明高斯模糊磨砂玻璃控件 (FrostedGlass / FrostedIconButton /
 * FrostedSlider) overlay 在 PlayerView 之上, 磨砂效果本身不随主题切换,
 * 仅图标 / 文本颜色通过 [com.itsaky.androidide.ui.compose.LocalDarkMode]
 * 在黑白主题下自适应。
 *
 * @author ZeroStudio
 */
class VideoPreviewAction(context: Context, override val order: Int) : EditorRelatedAction() {

  override val id: String = ID

  override var requiresUIThread: Boolean = false

  companion object {
    const val ID = "ide.editor.videoPreview"
  }

  init {
    label = context.getString(R.string.title_video_preview)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_file_type_image)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)

    val activity = data.getActivity() as? EditorActivityKt
    if (activity == null) {
      markInvisible()
      return
    }

    // 只匹配视频后缀 (mp4 / mkv / webm 等)
    val editor = data.getEditor()
    val file = editor?.file

    if (file != null) {
      val extension = file.extension.lowercase()
      if (VideoPreviewFragment.SUPPORTED_EXTENSIONS.contains(extension)) {
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
    val activity = data.requireActivity() as? EditorActivityKt ?: return false
    val file = data.getEditor()?.file ?: return false
    val index = activity.findIndexOfEditorByFile(file)
    if (index >= 0) {
      activity.saveResult(index, SaveResult())
    }
    return true
  }

  override fun postExec(data: ActionData, result: Any) {
    val activity = data.requireActivity() as? EditorActivityKt ?: return
    val editor = data.getEditor()
    val file = editor?.file

    if (file != null && file.exists() && file.canRead()) {
      openVideoPreview(activity, file)
    }
  }

  /**
   * Opens the Video preview tab with the given file.
   *
   * @param activity The EditorActivityKt
   * @param file The video file to preview
   */
  private fun openVideoPreview(activity: EditorActivityKt, file: File) {
    val extension = file.extension.lowercase()
    val fragmentTabManager = activity.fragmentTabManager ?: return

    fragmentTabManager.openFileTab(file.absolutePath, extension)
  }
}
