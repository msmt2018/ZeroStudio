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

package com.itsaky.androidide.actions.sidebar

import android.content.Context
import android.content.Intent
import android.zero.studio.termux.TermuxFragment
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.requireContext
import com.itsaky.androidide.activities.TerminalActivity
import com.itsaky.androidide.projects.IProjectManager
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
import kotlin.reflect.KClass

/**
 * Sidebar action for opening the terminal fragment directly in the sidebar.
 *
 * @author Akash Yadav
 * @author android_zero (Refactor)
 */
class TerminalSidebarAction(context: Context, override val order: Int) : AbstractSidebarAction() {

  companion object {
    const val ID = "ide.editor.sidebar.terminal"

    fun startTerminalActivity(data: ActionData, isFailsafe: Boolean) {
      val context = data.requireContext()
      val intent =
          Intent(context, TerminalActivity::class.java).apply {
            // Try to get project path safely
            try {
              val projectPath = IProjectManager.getInstance().projectDirPath
              if (!projectPath.isNullOrEmpty()) {
                putExtra(TERMUX_ACTIVITY.EXTRA_SESSION_WORKING_DIR, projectPath)
              }

              val projectName = IProjectManager.getInstance().getWorkspace()?.getRootProject()?.name
              if (!projectName.isNullOrEmpty()) {
                putExtra(TERMUX_ACTIVITY.EXTRA_SESSION_NAME, projectName)
              }
            } catch (e: Exception) {}

            putExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, isFailsafe)
          }
      context.startActivity(intent)
    }
  }

  override val id: String = ID

  // This defines the Fragment to show in the sidebar when CLICKED
  override val fragmentClass: KClass<out Fragment> = TermuxFragment::class

  init {
    // label = context.getString(R.string.title_terminal)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_terminal)
  }
}
