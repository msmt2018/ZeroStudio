package com.itsaky.androidide.activities.editor.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.itsaky.androidide.activities.editor.ui.state.EditorPage
import com.itsaky.androidide.activities.editor.ui.state.EditorPageRegistry
import kotlinx.coroutines.launch

/**
 * Compose editor chrome. Page rendering and editor engines are explicit slots, so the shell
 * does not inflate layouts, manage Fragment transactions, or retain Activity instances.
 * The caller owns state and wires action dispatch, project changes, and output buffering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorWorkspaceScreen(
    title: String,
    drawerState: DrawerState,
    bottomSheetState: SheetState,
    bottomSheetVisible: Boolean,
    onDismissBottomSheet: () -> Unit,
    sidebar: EditorPageRegistry,
    bottomPages: EditorPageRegistry,
    openDrawerDescription: String,
    closeDescription: String,
    fragmentPage: @Composable (EditorPage.FragmentPage) -> Unit,
    editorContent: @Composable () -> Unit,
    editorTabs: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    toolbarActions: @Composable RowScope.() -> Unit = {},
    sidebarIcon: @Composable (EditorPage) -> Unit = {},
    bottomActions: @Composable RowScope.() -> Unit = {},
    bottomFragmentPage: @Composable (EditorPage.FragmentPage) -> Unit = fragmentPage,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bottomPageStates = rememberEditorPageStates(bottomPages)
    BackHandler(drawerState.isOpen) { scope.launch { drawerState.close() } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val drawerWidth = maxWidth
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // requiredWidth overrides Material's default maximum drawer width. This is
                // intentionally edge-to-edge even on a wide display, with no exposed editor gap.
                ModalDrawerSheet(Modifier.requiredWidth(drawerWidth).fillMaxHeight()) {
                    TopAppBar(
                        title = { Text(sidebar.selectedPage?.title.orEmpty(), maxLines = 1) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.close() } }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, closeDescription)
                            }
                        },
                    )
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                            sidebar.pages.forEach { page ->
                                key(page.id) {
                                    NavigationRailItem(
                                        selected = sidebar.selectedId == page.id,
                                        onClick = { sidebar.select(page.id) },
                                        icon = { sidebarIcon(page) },
                                        label = { Text(page.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                        }
                        RegisteredEditorPage(sidebar, fragmentPage, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            },
        ) {
            Scaffold(
                modifier = Modifier.imePadding(),
                topBar = {
                    TopAppBar(
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, openDrawerDescription)
                            }
                        },
                        actions = toolbarActions,
                    )
                },
                bottomBar = bottomBar,
            ) { insets ->
                Column(Modifier.fillMaxSize().padding(insets)) {
                    editorTabs()
                    Box(Modifier.fillMaxWidth().weight(1f)) { editorContent() }
                }
            }
        }
    }
    if (bottomSheetVisible) {
        ModalBottomSheet(
            sheetState = bottomSheetState,
            onDismissRequest = onDismissBottomSheet,
        ) {
            Column(Modifier.fillMaxHeight().imePadding()) {
                Row(Modifier.fillMaxWidth()) {
                    bottomActions()
                    IconButton(onClick = {
                        scope.launch {
                            bottomSheetState.hide()
                            if (!bottomSheetState.isVisible) onDismissBottomSheet()
                        }
                    }) { Icon(Icons.Default.Close, closeDescription) }
                }
                val pages = bottomPages.pages
                if (pages.isNotEmpty()) {
                    key(pages.map { it.id }) {
                        ScrollableTabRow(
                            selectedTabIndex = pages.indexOfFirst { it.id == bottomPages.selectedId }.coerceAtLeast(0),
                        ) {
                            pages.forEach { page ->
                                key(page.id) {
                                    Tab(
                                        selected = bottomPages.selectedId == page.id,
                                        onClick = { bottomPages.select(page.id) },
                                        text = { Text(page.title, maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                }
                RegisteredEditorPage(bottomPages, bottomFragmentPage, Modifier.fillMaxWidth().weight(1f), bottomPageStates)
            }
        }
    }
}

/** Preserve per-page saveable state on selection changes and release it on unregistration. */
@Composable
fun RegisteredEditorPage(
    registry: EditorPageRegistry,
    fragmentPage: @Composable (EditorPage.FragmentPage) -> Unit,
    modifier: Modifier = Modifier,
    states: SaveableStateHolder = rememberEditorPageStates(registry),
) {
    Box(modifier) {
        registry.selectedPage?.let { page ->
            key(page.id) {
                states.SaveableStateProvider(page.id) {
                    when (page) {
                        is EditorPage.ActionPage -> Unit
                        is EditorPage.ScreenPage -> page.content()
                        is EditorPage.FragmentPage -> fragmentPage(page)
                    }
                }
            }
        }
    }
}

/** Keep cleanup alive even when a modal sheet or the document surface is not composed. */
@Composable
fun rememberEditorPageStates(registry: EditorPageRegistry): SaveableStateHolder {
    val states = rememberSaveableStateHolder()
    val previous = remember { mutableSetOf<String>() }
    val ids = registry.pages.map { it.id }
    LaunchedEffect(ids) {
        (previous - ids.toSet()).forEach(states::removeState)
        previous.clear()
        previous.addAll(ids)
    }
    return states
}
