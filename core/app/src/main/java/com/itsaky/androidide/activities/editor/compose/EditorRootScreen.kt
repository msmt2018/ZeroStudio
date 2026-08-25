/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package com.itsaky.androidide.activities.editor.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.drop

/**
 * Compose-first editor shell.
 *
 * It deliberately receives the Sora host and drawer contents as slots: those are Android-view
 * integrations, while the ownership of chrome layout, tabs and empty state stays in Compose.
 */
@Composable
fun EditorRootScreen(
    state: EditorUiState,
    onEvent: (EditorEvent) -> Unit,
    drawerContent: @Composable () -> Unit,
    editorContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  LaunchedEffect(state.isDrawerOpen) {
    if (state.isDrawerOpen) drawerState.open() else drawerState.close()
  }
  LaunchedEffect(drawerState) {
    // A swipe/scrim tap changes DrawerState without going through the Activity. Feed that
    // interaction back into the same event stream so the state holder remains authoritative.
    snapshotFlow { drawerState.currentValue }
        .drop(1)
        .collect { value ->
          onEvent(if (value == DrawerValue.Open) EditorEvent.OpenDrawer else EditorEvent.CloseDrawer)
        }
  }

  ModalNavigationDrawer(
      modifier = modifier,
      drawerState = drawerState,
      drawerContent = { EditorDrawer(drawerContent) },
  ) {
    Scaffold(
        topBar = { EditorTopBar(state, onEvent) },
        bottomBar = { EditorBottomPanel(state, onEvent) },
    ) { contentPadding ->
      Column(Modifier.fillMaxSize().padding(contentPadding)) {
        EditorTabs(state, onEvent)
        if (state.openFiles.isEmpty()) {
          EditorEmptyState(onEvent)
        } else {
          EditorContent(editorContent)
        }
      }
    }
  }
}

@Composable
fun EditorDrawer(content: @Composable () -> Unit) {
  ModalDrawerSheet { content() }
}

@Composable
fun EditorTopBar(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
  androidx.compose.material3.TopAppBar(
      title = { Text(if (state.cursorPosition.isBlank()) "AndroidIDE" else state.cursorPosition) },
      navigationIcon = {
        androidx.compose.material3.TextButton(onClick = { onEvent(EditorEvent.OpenDrawer) }) {
          Text("Files")
        }
      },
  )
}

@Composable
fun EditorTabs(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
  if (state.openFiles.isEmpty()) return
  val selectedIndex = state.selectedFileIndex.coerceIn(0, state.openFiles.lastIndex)
  androidx.compose.material3.PrimaryTabRow(selectedTabIndex = selectedIndex) {
    state.openFiles.forEachIndexed { index, tab ->
      androidx.compose.material3.Tab(
          selected = index == selectedIndex,
          onClick = { onEvent(EditorEvent.SelectTab(index)) },
          text = { Text(if (tab.isDirty) "${tab.title} *" else tab.title) },
      )
    }
  }
}

/** Hosts the Sora [android.view.View] without giving Compose code direct access to it. */
@Composable
fun EditorContent(content: @Composable (Modifier) -> Unit) {
  content(Modifier.fillMaxSize())
}

@Composable
fun EditorEmptyState(onEvent: (EditorEvent) -> Unit) {
  Column(Modifier.fillMaxSize()) {
    Text("No file is open", style = MaterialTheme.typography.titleMedium)
    androidx.compose.material3.TextButton(onClick = { onEvent(EditorEvent.OpenDrawer) }) {
      Text("Browse files")
    }
    androidx.compose.material3.TextButton(onClick = { onEvent(EditorEvent.ExpandBottomPanel) }) {
      Text("Open build output")
    }
  }
}

@Composable
fun EditorBottomPanel(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
  if (!state.isBottomPanelVisible && state.buildStatus.isBlank()) return
  androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
    Column {
      Text(state.buildStatus)
      if (state.isBuildInProgress) androidx.compose.material3.LinearProgressIndicator()
      androidx.compose.material3.TextButton(onClick = { onEvent(EditorEvent.CollapseBottomPanel) }) {
        Text("Hide")
      }
    }
  }
}
