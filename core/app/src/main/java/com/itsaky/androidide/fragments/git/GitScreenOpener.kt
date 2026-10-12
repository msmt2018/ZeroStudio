/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.fragments.git

import android.content.Context
import androidx.activity.ComponentActivity
import com.itsaky.androidide.actions.sidebar.GitSidebarAction
import com.itsaky.androidide.activities.editor.BaseEditorActivity

object GitScreenOpener {

  @JvmStatic
  fun openGitUi(activity: ComponentActivity) {
    if (activity is BaseEditorActivity) {
      activity.content.sidebar.select(GitSidebarAction.ID)
      activity.content.drawerOpen = true
    }
  }

  @JvmStatic
  fun openGitUi(context: Context) {
    if (context is BaseEditorActivity) {
      context.content.sidebar.select(GitSidebarAction.ID)
      context.content.drawerOpen = true
    }
  }
}