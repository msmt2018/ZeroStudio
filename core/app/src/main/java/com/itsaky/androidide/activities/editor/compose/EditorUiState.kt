/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package com.itsaky.androidide.activities.editor.compose

/** Immutable rendering model for the editor chrome.
 *
 * The code editor itself remains an Android view because Sora owns its rendering pipeline. Every
 * other editor-surface concern is represented here so composables never need to read a View.
 */
data class EditorUiState(
    val openFiles: List<EditorTabUiState> = emptyList(),
    val selectedFileIndex: Int = -1,
    val isDrawerOpen: Boolean = false,
    val isBottomPanelVisible: Boolean = false,
    val isBuildInProgress: Boolean = false,
    val buildStatus: CharSequence = "",
    val cursorPosition: String = "",
)

/** Stable tab data which is safe to retain across Compose recompositions. */
data class EditorTabUiState(
    val id: String,
    val title: String,
    val isDirty: Boolean = false,
)
