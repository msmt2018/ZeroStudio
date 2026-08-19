package com.itsaky.androidide.ui.screen.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compose scaffold contract for the editor activity migration.
 *
 * The current editor can host existing Fragment/View content inside [content] while the activity
 * main surface, top actions, left drawer and bottom drawer are moved to Compose screen files.
 */
@Composable
fun EditorActivityScaffoldScreen(
  modifier: Modifier = Modifier,
  drawerContent: @Composable () -> Unit,
  topBar: @Composable () -> Unit,
  bottomBar: @Composable () -> Unit,
  bottomDrawer: @Composable () -> Unit,
  content: @Composable (PaddingValues) -> Unit,
) {
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.95f)) {
        drawerContent()
      }
    },
    modifier = modifier,
  ) {
    Scaffold(
      topBar = topBar,
      bottomBar = bottomBar,
    ) { paddingValues ->
      Box(Modifier.fillMaxSize()) {
        content(paddingValues)
        bottomDrawer()
      }
    }
  }
}
