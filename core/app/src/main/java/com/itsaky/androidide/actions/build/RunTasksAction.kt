/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.actions.build

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.BaseBuildAction
import com.itsaky.androidide.ui.screen.RunTasksScreen

/**
 * 用于运行gradle task，可以获得完整任务列表
 * @author android_zero
 */
class RunTasksAction(context: Context, override val order: Int) : BaseBuildAction() {

  override val id: String = "ide.editor.build.runTasks"
  override var requiresUIThread: Boolean = true

  init {
    label = context.getString(R.string.title_run_tasks)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_run_tasks)
  }

  override suspend fun execAction(data: ActionData): Boolean {
    val activity = data.getActivity() ?: return false
    activity.content.dialog = {
      RunTasksScreen(
          activity = activity,
          onDismiss = { activity.content.dialog = null },
      )
    }
    return true
  }

  override fun getShowAsActionFlags(data: ActionData): Int = MenuItem.SHOW_AS_ACTION_ALWAYS
}