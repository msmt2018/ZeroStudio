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

package com.itsaky.androidide.handlers

import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.EditorActivityKt
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.services.builder.GradleBuildService
import com.itsaky.androidide.tooling.api.messages.result.BuildInfo
import com.itsaky.androidide.tooling.events.ProgressEvent
import com.itsaky.androidide.tooling.events.configuration.ProjectConfigurationStartEvent
import com.itsaky.androidide.tooling.events.task.TaskStartEvent
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import java.lang.ref.WeakReference
import org.slf4j.LoggerFactory

/**
 * Handles events received from [GradleBuildService] updates [EditorActivityKt].
 *
 * @author Akash Yadav
 * @author android_zero
 */
class EditorBuildEventListener : GradleBuildService.EventListener {

  private var enabled = true
  private var activityReference: WeakReference<EditorActivityKt> = WeakReference(null)

  companion object {

    private val log = LoggerFactory.getLogger(EditorBuildEventListener::class.java)
  }

  private val _activity: EditorActivityKt?
    get() = activityReference.get()

  private val activity: EditorActivityKt
    get() = checkNotNull(activityReference.get()) { "Activity reference has been destroyed!" }

  fun setActivity(activity: EditorActivityKt) {
    this.activityReference = WeakReference(activity)
    this.enabled = true
  }

  fun release() {
    activityReference.clear()
    this.enabled = false
  }

  override fun prepareBuild(buildInfo: BuildInfo) {
    val act = checkActivity("prepareBuild") ?: return

    val isFirstBuild = GeneralPreferences.isFirstBuild
    act.setStatus(act.getString(if (isFirstBuild) string.preparing_first else string.preparing))

    if (isFirstBuild) {
      act.showFirstBuildNotice()
    }

    act.editorViewModel.isBuildInProgress = true

    // 安全更新底栏
    act.clearBuildOutputSafely()

    if (buildInfo.tasks.isNotEmpty()) {
      act.appendBuildOutput(act.getString(R.string.title_run_tasks) + " : " + buildInfo.tasks)
    }
  }

  override fun onBuildSuccessful(tasks: List<String?>) {
    val act = checkActivity("onBuildSuccessful") ?: return

    analyzeCurrentFile()

    GeneralPreferences.isFirstBuild = false
    act.editorViewModel.isBuildInProgress = false

    act.flashSuccess(R.string.build_status_sucess)
  }

  override fun onProgressEvent(event: ProgressEvent) {
    val act = checkActivity("onProgressEvent") ?: return

    if (event is ProjectConfigurationStartEvent || event is TaskStartEvent) {
      act.setStatus(event.descriptor.displayName)
    }
  }

  override fun onBuildFailed(tasks: List<String?>) {
    val act = checkActivity("onBuildFailed") ?: return

    analyzeCurrentFile()

    GeneralPreferences.isFirstBuild = false
    act.editorViewModel.isBuildInProgress = false

    act.flashError(R.string.build_status_failed)
  }

  override fun onOutput(line: String?) {
    val act = checkActivity("onOutput") ?: return

    line?.let { act.appendBuildOutput(it) }

    if (line != null && (line.contains("BUILD SUCCESSFUL") || line.contains("BUILD FAILED"))) {
      act.setStatus(line)
    }
  }

  private fun analyzeCurrentFile() {
    val act = checkActivity("analyzeCurrentFile") ?: return

    val editorView = act.getCurrentEditor()
    if (editorView != null) {
      val editor = editorView.editor
      editor?.analyze()
    }
  }

  /**
   * Safe getter: Verifies that the activity is neither null, nor finishing, nor destroyed. This is
   * crucial for preventing IllegalStateException when trying to update UI bindings that have
   * already been torn down by the system.
   */
  private fun checkActivity(action: String): EditorActivityKt? {
    if (!enabled) return null

    val act = _activity
    if (act == null || act.isDestroyed || act.isFinishing) {
      log.warn("[{}] Activity reference is null or has been destroyed. Ignoring callback.", action)
      enabled = false
      return null
    }
    return act
  }
}
