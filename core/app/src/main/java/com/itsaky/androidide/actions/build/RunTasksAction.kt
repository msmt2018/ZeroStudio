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

package com.itsaky.androidide.actions.build

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.BaseBuildAction
import com.itsaky.androidide.activities.editor.EditorActivityKt
import com.itsaky.androidide.fragments.RunTasksDialogFragment
import com.itsaky.androidide.preferences.internal.EditorPreferences
import com.itsaky.androidide.resources.R

/**
 * @author Akash Yadav
 * @author android_zero
 */
class RunTasksAction(context: Context, override val order: Int) : BaseBuildAction() {
  override val id: String = "ide.editor.build.runTasks"
  private var dialog: RunTasksDialogFragment? = null

  init {
    label = context.getString(R.string.title_run_tasks)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_run_tasks)
  }

  /**
   * Executes the action to show the run tasks dialog.
   *
   * @param data The context data for the action.
   * @return The [RunTasksDialogFragment] instance to be shown.
   * @see com.itsaky.androidide.actions.ActionItem.execAction
   * @see IEditorHandler.saveAll
   */
  override suspend fun execAction(data: ActionData): Any {
    if (EditorPreferences.autoSaveBeforeBuild) {
      val activity = data.getActivity() as? EditorActivityKt
      activity?.saveAll(notify = false, requestSync = false)
    }
    dialog?.dismiss()
    dialog = null
    dialog = RunTasksDialogFragment()
    return dialog!!
  }

  override fun postExec(data: ActionData, result: Any) {
    if (result !is RunTasksDialogFragment) {
      return
    }

    val activity = data.getActivity() ?: return
    result.show(activity.supportFragmentManager, this.id)
  }

  override fun destroy() {
    super.destroy()
    try {
      dialog?.dismiss()
    } catch (e: Exception) {
      // ignored
    }
    dialog = null
  }
}
