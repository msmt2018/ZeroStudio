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

package com.itsaky.androidide.actions.etc

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.KeyboardUtils
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.file.FileValidator
import com.itsaky.androidide.fragments.editor.FragmentTabRegistry
import com.itsaky.androidide.fragments.editor.image.ImagePreviewFragment
import com.itsaky.androidide.models.SaveResult
import com.itsaky.androidide.resources.R
import java.io.File

/**
 * Action menu item to render the currently open SVG / SVGZ / Android XML
 * vector drawable as an image in the integrated [ImagePreviewFragment] tab.
 *
 * ## 交互模型
 *
 * SVG / SVGZ / XML 矢量图在文件树点击时先以文本编辑器打开 (方便编辑源码),
 * 用户需要预览渲染效果时点击编辑器工具栏的 "Render As Image" 按钮, 通过
 * [com.itsaky.androidide.fragments.editor.EditorFragmentTabManager] 打开
 * [ImagePreviewFragment] tab. 这与 [PreviewLayoutAction] (布局 XML 先编辑
 * 后预览) 和 [MarkdownPreviewAction] 的交互模式一致.
 *
 * ## 可见性规则
 *
 * - 当前编辑器有打开文件, 且后缀是 `svg` / `svgz` → 始终显示.
 * - 后缀是 `xml` 且内容是 Android vector drawable
 *   ([FileValidator.isLikelyAndroidVector]) → 显示; layout / manifest /
 *   values 等普通 XML 不显示 (避免误判).
 * - 其余文件 → 隐藏.
 *
 * @author android_zero
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

    val editor = data.getEditor()
    val file = editor?.file

    if (file == null || !file.exists() || !file.isFile) {
      markInvisible()
      return
    }

    if (!isRenderableVectorFile(file)) {
      markInvisible()
      return
    }

    // 确认 image_preview tab 已注册 (EditorHandlerActivity.registerFragmentTabs).
    val extension = file.extension.lowercase()
    if (FragmentTabRegistry.getByFileExtension(extension).isEmpty()) {
      markInvisible()
      return
    }

    visible = true
    enabled = true
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
    // 保存文件确保 ImagePreviewFragment 读到的是最新内容
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
   * 通过 [com.itsaky.androidide.fragments.editor.EditorFragmentTabManager] 打开
   * [ImagePreviewFragment] tab 渲染给定文件.
   */
  private fun openImagePreview(activity: EditorHandlerActivity, file: File) {
    val extension = file.extension.lowercase()
    val fragmentTabManager = activity.fragmentTabManager ?: return
    fragmentTabManager.openFileTab(file.absolutePath, extension)
  }

  /**
   * 判断文件是否可通过本 action 渲染为图像:
   * - `svg` / `svgz` → true
   * - `xml` 且内容是 Android vector drawable → true
   * - 其余 → false
   */
  private fun isRenderableVectorFile(file: File): Boolean {
    val ext = file.extension.lowercase()
    if (ext.isEmpty()) return false
    if (ext in ImagePreviewFragment.SVG_FORMATS) return true
    if (ext in ImagePreviewFragment.XML_VECTOR_FORMATS) {
      return FileValidator.isLikelyAndroidVector(file)
    }
    return false
  }
}
