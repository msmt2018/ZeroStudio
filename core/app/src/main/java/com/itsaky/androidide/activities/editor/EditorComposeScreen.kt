/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package com.itsaky.androidide.activities.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Immutable state required by the Compose editor chrome. */
internal data class EditorComposeUiState(
    val title: String = "AndroidIDE",
    val openFiles: List<String> = emptyList(),
    val selectedFileIndex: Int = -1,
    val buildStatus: String? = null,
    val cursorPosition: String = "1:1",
    val isBottomPanelVisible: Boolean = false,
)

/**
 * Compose replacement for the static parts of `activity_editor.xml` and its included layouts.
 *
 * Editor and fragment surfaces deliberately stay outside this screen: they have independent
 * lifecycles and are hosted by the existing editor/fragment containers while they are migrated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorComposeScreen(
    state: EditorComposeUiState,
    drawerOpen: Boolean,
    onDrawerDismiss: () -> Unit,
    onFileSelected: (Int) -> Unit,
    editorSurface: @Composable () -> Unit,
    bottomPanel: @Composable () -> Unit,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceDim)) {
    EditorMainContent(
        state = state,
        onFileSelected = onFileSelected,
        editorSurface = editorSurface,
        bottomPanel = bottomPanel,
    )

    if (drawerOpen) {
      EditorNavigationDrawer(onDismiss = onDrawerDismiss, content = drawerContent)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorMainContent(
    state: EditorComposeUiState,
    onFileSelected: (Int) -> Unit,
    editorSurface: @Composable () -> Unit,
    bottomPanel: @Composable () -> Unit,
) {
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
    Column {
      CenterAlignedTopAppBar(
          title = { Text(state.title) },
          colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface,
          ),
      )
      EditorTabStrip(
          files = state.openFiles,
          selectedIndex = state.selectedFileIndex,
          onFileSelected = onFileSelected,
      )
      Box(modifier = Modifier.weight(1f)) { editorSurface() }
      state.buildStatus?.let { status ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(status, style = MaterialTheme.typography.bodySmall)
          Text(state.cursorPosition, style = MaterialTheme.typography.labelSmall)
        }
      }
      if (state.isBottomPanelVisible) {
        HorizontalDivider()
        bottomPanel()
      }
    }
  }
}

@Composable
private fun EditorTabStrip(files: List<String>, selectedIndex: Int, onFileSelected: (Int) -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    files.forEachIndexed { index, file ->
      Text(
          text = file,
          color = if (index == selectedIndex) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.clickable { onFileSelected(index) }.padding(horizontal = 16.dp, vertical = 12.dp),
          style = MaterialTheme.typography.labelLarge,
      )
    }
  }
}

/**
 * A full-height Compose drawer that intentionally leaves only 2.5% of the editor visible.
 *
 * This replaces the legacy 24%-visible swipe drawer behavior. The remaining editor sliver is a
 * clear visual affordance for dismissal while prioritising the project tree.
 */
@Composable
private fun EditorNavigationDrawer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
  val screenWidth = LocalConfiguration.current.screenWidthDp.dp
  val drawerWidth = (LocalConfiguration.current.screenWidthDp * 0.975f).dp
  Row(modifier = Modifier.fillMaxSize()) {
    Surface(
        modifier = Modifier.width(drawerWidth).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
      Column {
        content()
        Spacer(Modifier.height(1.dp))
      }
    }
    Box(
        modifier = Modifier.width((screenWidth - drawerWidth).coerceAtLeast(0.dp)).fillMaxHeight()
            .clickable(onClick = onDismiss),
    )
  }
}
