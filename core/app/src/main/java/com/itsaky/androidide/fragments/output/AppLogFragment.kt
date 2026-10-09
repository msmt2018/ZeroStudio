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

package com.itsaky.androidide.fragments.output

import android.os.Bundle
import android.view.View
import com.itsaky.androidide.R
import com.itsaky.androidide.preferences.internal.DevOpsPreferences

/**
 * Fragment to show application logs.
 *
 * @author Akash Yadav
 */
class AppLogFragment : LogViewFragment() {

  private var session: com.itsaky.androidide.activities.editor.ui.state.EditorAppLogSession? = null
  override fun isSimpleFormattingEnabled() = false
  fun appendAppLine(line: String) = appendLine(line)
  override fun getFilename() = "app_logs"
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    emptyMessage = getString(if (DevOpsPreferences.logsenderEnabled) R.string.msg_emptyview_applogs else R.string.msg_logsender_disabled)
    // The editor owns collection so switching pages cannot interrupt logs.
    if (activity !is com.itsaky.androidide.activities.editor.BaseEditorActivity) {
      session = com.itsaky.androidide.activities.editor.ui.state.EditorAppLogSession(requireContext(), this::appendLog).also { it.start() }
    }
  }
  override fun onDestroyView() {
    session?.close()
    session = null
    super.onDestroyView()
  }
}
