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
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
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
import com.itsaky.androidide.activities.editor.EditorActivityKt
import com.itsaky.androidide.compose.preview.ComposePreviewActivity
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.ModuleProject
import com.itsaky.androidide.projects.android.AndroidModule
import com.itsaky.androidide.resources.R
import java.io.File
import org.slf4j.LoggerFactory

/**
 * Action menu item specifically for rendering Jetpack Compose Previews.
 *
 * @author Akash Yadav
 * @author android_zero
 */
class PreviewComposeAction(context: Context, override val order: Int) : EditorRelatedAction() {

  override val id: String = ID

  override var requiresUIThread: Boolean = false

  companion object {
    const val ID = "ide.editor.previewCompose"
    private val LOG = LoggerFactory.getLogger(PreviewComposeAction::class.java)
  }

  init {
    label = context.getString(R.string.title_jetpack_preview_compose)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_editor_jetpack_compose_preview_layout)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)

    val activity = data.getActivity() as? EditorActivityKt
    if (activity == null) {
      markInvisible()
      return
    }

    val viewModel = activity.editorViewModel
    val editor = data.getEditor()
    val file = editor?.file

    if (file != null && !viewModel.isInitializing) {
      // 仅当文件是 Kotlin 文件并且当前模块使用了 Compose 时，才显示并启用该按钮
      if ((file.name.endsWith(".kt") || file.name.endsWith(".kts")) && moduleUsesCompose(file)) {
        visible = true
        enabled = true
      } else {
        markInvisible()
      }
    } else {
      // 当没有文件被打开，但项目模块支持 Compose 时，按钮显示但不可点击
      if (moduleUsesCompose()) {
        visible = true
        enabled = false
      } else {
        markInvisible()
      }
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
    val activity = data.requireActivity() as? EditorActivityKt
    activity?.saveAll()
    return true
  }

  override fun postExec(data: ActionData, result: Any) {
    val activity = data.requireActivity() as? EditorActivityKt ?: return
    val editor = data.getEditor() ?: return
    val file = editor.file ?: return

    // 直接调用 ComposePreviewActivity 的启动方法
    ComposePreviewActivity.start(activity, editor.text.toString(), file.absolutePath)
  }

  /** 检查整个工作区中是否有任何模块引入了 Compose */
  private fun moduleUsesCompose(): Boolean {
    val workspace = IProjectManager.getInstance().getWorkspace() ?: return false
    return workspace.androidProjects().any { module -> checkHasComposeDependency(module) }
  }

  /** 检查指定文件所在的模块是否引入了 Compose */
  private fun moduleUsesCompose(file: File): Boolean {
    val module = IProjectManager.getInstance().findModuleForFile(file, false) ?: return false
    return checkHasComposeDependency(module)
  }

  /** 依赖检查逻辑： */
  private fun checkHasComposeDependency(module: ModuleProject): Boolean {
    if (module !is AndroidModule) return false

    // 检查当前模块的依赖映射表中是否包含 Compose 运行时的包名
    return module.libraryMap.keys.any { dependencyKey ->
      dependencyKey.contains("androidx.compose.runtime:runtime")
    }
  }
}
