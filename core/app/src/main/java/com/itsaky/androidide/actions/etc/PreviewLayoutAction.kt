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
import android.content.Intent
import android.view.MenuItem
import android.zero.studio.layouteditor.LayoutEditorContext
import android.zero.studio.layouteditor.LayoutFile
import android.zero.studio.layouteditor.ProjectFile
import android.zero.studio.layouteditor.activities.LayoutsEditorActivity
import android.zero.studio.layouteditor.managers.ProjectManager
import android.zero.studio.layouteditor.utils.Constants
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.KeyboardUtils
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.activities.editor.EditorActivityKt
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.resources.R
import java.io.File

/**
 * Action to preview and edit Android XML layouts using the integrated LayoutEditor.
 *
 * @author Akash Yadav (Early contributors)
 * @author android_zero
 */
class PreviewLayoutAction(context: Context, override val order: Int) : EditorRelatedAction() {

  override val id: String = ID
  override var requiresUIThread: Boolean = false

  companion object {
    const val ID = "ide.editor.previewLayout"
  }

  init {
    label = context.getString(R.string.title_preview_layout)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_preview_layout)
  }

  /**
   * Determines if the action should be visible. STRICT CHECK: Must be .xml AND inside res/layout*
   * folder.
   */
  override fun prepare(data: ActionData) {
    super.prepare(data)

    if (!visible) return

    val viewModel = data.requireActivity().editorViewModel
    if (viewModel.isInitializing) {
      visible = true
      enabled = false
      return
    }

    // Get the file from the current active editor tab
    // We use data.get() directly to avoid conflict with superclass extension methods
    val editor = data.get(IDEEditor::class.java)
    val file = editor?.file

    // Reset state to false, then prove it should be true
    visible = false
    enabled = false

    if (file != null && file.exists() && file.isFile) {
      // Is XML?
      val isXml = file.name.endsWith(".xml", ignoreCase = true)

      if (isXml) {
        // Is inside a layout folder? (layout, layout-land, layout-v21, etc.)
        // file parent -> "layout-xxx"
        val parentDir = file.parentFile
        val parentName = parentDir?.name ?: ""
        val isLayoutFolder = parentName.startsWith("layout")

        // Is the layout folder inside 'res'?
        // This prevents showing the button for generic XMLs in other folders.
        // file parent parent -> "res"
        val grandParentName = parentDir?.parentFile?.name ?: ""
        val isInsideRes = grandParentName == "res"

        if (isLayoutFolder && isInsideRes) {
          visible = true
          enabled = true
        }
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
    // Save files to ensure the LayoutEditor reads the latest content from disk
    val activity = data.requireActivity()
    activity.saveAll()
    return true
  }

  override fun postExec(data: ActionData, result: Any) {
    val activity = data.requireActivity()
    val editor = data.get(IDEEditor::class.java)

    if (editor?.file != null) {
      activity.previewLayout(editor.file!!)
    }
  }

  /** Launches the LayoutEditor with the context of the provided file. */
  private fun EditorActivityKt.previewLayout(file: File) {
    // FIX: Root-cause fix for "LayoutEditorContext is not initialized" IllegalArgumentException.
    //
    // Before this fix, `projectFile.currentLayout = layoutFile` below calls
    // `PreferencesManager.getPrefs` which dereferences `LayoutEditorContext.context`.
    // `LayoutEditorContext` is only initialised in `LayoutsEditorActivity.onCreate`
    // (via `BaseActivity`), so the very first call into the action that triggers
    // it BEFORE the activity is created would crash the app.
    //
    // The proper root-cause fix is to ensure the singleton is initialised as soon
    // as we know we are going to need it — i.e. here, before we touch any
    // LayoutEditorContext-dependent API. `LayoutEditorContext.init` is idempotent
    // and a no-op if already initialised, so calling it here is safe even when
    // the user has previously opened the layout editor.
    LayoutEditorContext.init(applicationContext)

    // Resolve the resource root directory (e.g., .../src/main/res)
    // We know from prepare() that file -> layout-xxx -> res
    val layoutDir = file.parentFile
    val resDir = layoutDir?.parentFile ?: file.parentFile

    // Create the Project Context
    // This tells the editor where to find all resources (colors, strings, drawables)
    // We use the 'res' folder as the project root for the editor.
    val projectFile = ProjectFile(resDir!!.absolutePath, System.currentTimeMillis().toString())

    // Create the Layout File Target
    // This tells the editor specifically which file to open
    val layoutFile = LayoutFile(file.absolutePath)

    // Set global state
    projectFile.currentLayout = layoutFile
    ProjectManager.instance.openProject(projectFile)

    val intent =
        Intent(this, LayoutsEditorActivity::class.java).apply {
          putExtra(Constants.EXTRA_KEY_PROJECT, projectFile)
          putExtra(Constants.EXTRA_KEY_LAYOUT, layoutFile)
        }

    startActivity(intent)
  }
}
