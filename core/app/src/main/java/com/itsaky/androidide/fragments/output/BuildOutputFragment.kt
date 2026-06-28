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
import com.blankj.utilcode.util.ThreadUtils
import com.itsaky.androidide.R

class BuildOutputFragment : NonEditableEditorFragment() {
  private val outputBuffer = StringBuilder()
  @Volatile private var flushScheduled = false

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    emptyMessage = getString(R.string.msg_emptyview_buildoutput)
    flushOutputToEditor()
  }

  override fun onDestroyView() {
    flushScheduled = false
    editor?.release()
    super.onDestroyView()
  }

  override fun clearOutput() {
    synchronized(outputBuffer) { outputBuffer.clear() }
    flushScheduled = false
    super.clearOutput()
  }

  override fun getContent(): String {
    return synchronized(outputBuffer) { outputBuffer.toString() }
  }

  fun appendOutput(output: String?) {
    val normalized = normalizeOutput(output)
    synchronized(outputBuffer) { outputBuffer.append(normalized) }
    scheduleFlush()
  }

  private fun normalizeOutput(output: String?): String {
    if (output.isNullOrEmpty()) {
      return "\n"
    }
    return if (output.endsWith("\n")) output else "$output\n"
  }

  private fun scheduleFlush() {
    if (flushScheduled) {
      return
    }
    flushScheduled = true
    ThreadUtils.runOnUiThreadDelayed(
        {
          flushScheduled = false
          flushOutputToEditor()
        },
        OUTPUT_FLUSH_DELAY_MS,
    )
  }

  private fun flushOutputToEditor() {
    val target = editor ?: return
    val snapshot = synchronized(outputBuffer) { outputBuffer.toString() }
    target.setText(snapshot)
    target.goToEnd()
    isEmpty = snapshot.isEmpty()
  }

  companion object {
    private const val OUTPUT_FLUSH_DELAY_MS = 50L
  }
}
