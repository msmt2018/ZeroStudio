/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package com.itsaky.androidide.activities.editor.compose

/** One-way user intents emitted by the editor Compose hierarchy. */
sealed interface EditorEvent {
  data object OpenDrawer : EditorEvent
  data object CloseDrawer : EditorEvent
  data object ExpandBottomPanel : EditorEvent
  data object CollapseBottomPanel : EditorEvent
  data class SelectTab(val index: Int) : EditorEvent
  data class ReselectTab(val index: Int) : EditorEvent
}
