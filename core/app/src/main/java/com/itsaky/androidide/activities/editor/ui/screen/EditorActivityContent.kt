package com.itsaky.androidide.activities.editor.ui.screen

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.MenuItem
import android.zero.studio.widget.editor.symbolinput.SymbolManagerActivity
import androidx.activity.compose.BackHandler
import androidx.appcompat.view.menu.MenuBuilder
import android.zero.studio.widget.editor.symbolinput.SymbolInputPanel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.children
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.BaseEditorActivity
import com.itsaky.androidide.activities.editor.ui.state.EditorUiState
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.ui.SymbolInputVisibilityManager
import com.itsaky.androidide.onboarding.OnboardingTarget
import com.itsaky.androidide.onboarding.onboardingBind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorActivityContent(activity: BaseEditorActivity, state: EditorUiState) {
    val guideTargets = remember { listOf("editor.drawer", "editor.status", "editor.symbols").map(OnboardingTarget::of) }
    val drawer = rememberDrawerState(if (state.drawerOpen) DrawerValue.Open else DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    LaunchedEffect(state.drawerOpen) { if (state.drawerOpen) drawer.open() else drawer.close() }
    LaunchedEffect(drawer) {
        snapshotFlow { drawer.currentValue }.drop(1).collect {
            state.drawerOpen = it == DrawerValue.Open
            activity.editorViewModel.startDrawerOpened = state.drawerOpen
        }
    }
    BackHandler(!drawer.isOpen && !state.bottomSheet.visible && state.dialog == null) {
        if (state.memoryVisible) state.memoryVisible = false else activity.doConfirmProjectClose()
    }
    val maxToolbarActions = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) 2 else 4
    val colors = editorColorScheme(activity)
    val documentStates = rememberEditorPageStates(state.documents)
    MaterialTheme(colorScheme = colors) {
        EditorWorkspaceScreen(
            title = stringResource(R.string.app_name),
            drawerState = drawer,
            bottomSheetState = sheet,
            bottomSheetVisible = state.bottomSheet.visible,
            onDismissBottomSheet = { state.bottomSheet.forceCollapse() },
            sidebar = state.sidebar,
            bottomPages = state.bottomSheet.pages,
            openDrawerDescription = stringResource(R.string.msg_file_tree),
            closeDescription = stringResource(R.string.btn_close),
            fragmentPage = { page -> EditorFragmentPage(page) },
            bottomFragmentPage = { page ->
                EditorFragmentPage(page, onReady = state.bottomSheet::attach, onReleased = state.bottomSheet::detach)
            },
            sidebarIcon = { page ->
                val action = ActionsRegistry.getInstance().getActions(ActionItem.Location.EDITOR_SIDEBAR)[page.id]
                val terminal = page.id == com.itsaky.androidide.actions.sidebar.TerminalSidebarAction.ID
                val modifier = if (terminal) Modifier.combinedClickable(
                    onClick = { state.sidebar.select(page.id) },
                    onLongClick = {
                        state.drawerOpen = false
                        val data = com.itsaky.androidide.actions.ActionData().apply { put(android.content.Context::class.java, activity) }
                        com.itsaky.androidide.actions.sidebar.TerminalSidebarAction.startTerminalActivity(data, true)
                    },
                ) else Modifier
                Box(modifier.padding(4.dp)) {
                    action?.icon?.let { DrawableIcon(it, page.title) } ?: Icon(Icons.Default.Folder, page.title)
                }
            },
            toolbarActions = {
                state.toolbarVersion // snapshot dependency for imperative action-registry updates
                val items = (0 until activity.toolbarMenu.size()).map(activity.toolbarMenu::getItem).filter { it.isVisible }
                items.filter { it.icon != null }.take(maxToolbarActions).forEach { item ->
                    IconButton(enabled = item.isEnabled, onClick = { showMenuItem(state, activity.toolbarMenu, item) }) { DrawableIcon(item.icon!!, item.title.toString()) }
                }
                IconButton(onClick = { state.memoryVisible = !state.memoryVisible }) { Icon(Icons.Default.Memory, stringResource(R.string.editor_memory_usage)) }
                IconButton(onClick = { state.dialog = { EditorMenuDialog(activity.toolbarMenu) { state.dialog = null } } }) { Icon(Icons.Default.MoreVert, stringResource(R.string.editor_actions)) }
            },
            editorTabs = {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.memoryVisible) EditorMemoryChart(state.memories)
                if (state.tabs.visible) state.tabs.Content()
            },
            editorContent = {
                // Keep open Sora engines attached so background autosave and preferences remain active.
                state.editorContainer.editors.forEach { editor ->
                    key(editor) {
                        val selected = state.surface == 0 && state.editorContainer.selected === editor
                        AndroidView(
                            factory = { editor },
                            modifier = if (selected) Modifier.fillMaxSize() else Modifier.size(0.dp),
                            update = { it.visibility = if (selected) android.view.View.VISIBLE else android.view.View.GONE },
                        )
                    }
                }
                when (state.surface) {
                    0 -> Unit
                    1 -> RegisteredEditorPage(state.documents, { EditorFragmentPage(it) }, Modifier.fillMaxSize(), documentStates)
                    else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        TextButton(onClick = { state.drawerOpen = true }) { Text(stringResource(R.string.msg_file_tree)) }
                        TextButton(onClick = { state.bottomSheet.visible = true }) { Text(stringResource(R.string.build_output)) }
                    }
                }
            },
            bottomBar = {
                Surface {
                    Column(Modifier.navigationBarsPadding()) {
                        Row(Modifier.fillMaxWidth().onboardingBind(guideTargets[1])) {
                            TextButton(onClick = { state.bottomSheet.visible = true }, modifier = Modifier.weight(1f)) { Text(state.bottomSheet.status.ifBlank { stringResource(R.string.build_output) }, maxLines = 1) }
                            Text(state.cursor, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
                            IconButton(modifier = Modifier.onboardingBind(guideTargets[0]), onClick = { state.bottomSheet.visible = true }) { Icon(Icons.Default.ExpandLess, stringResource(R.string.build_output)) }
                        }
                        if (state.headerVisible && !SymbolInputVisibilityManager.previewHidden) {
                            if (state.bottomSheet.headerPage == 1) EditorBottomAction(state.bottomSheet.actionText, state.bottomSheet.actionProgress)
                            SymbolInputPanel(if (state.surface == 0) state.currentEditor?.editor else null, onOpenManager = { activity.startActivity(Intent(activity, SymbolManagerActivity::class.java)) }, modifier = Modifier.onboardingBind(guideTargets[2]).onSizeChanged { state.symbolHeight = it.height })
                        }
                    }
                }
            },
            bottomActions = {
                IconButton(enabled = state.bottomSheet.selectedOutput != null, onClick = state.bottomSheet::clearSelectedOutput) { Icon(Icons.Default.Delete, stringResource(R.string.editor_clear_output)) }
                IconButton(enabled = !sharing && state.bottomSheet.selectedOutput != null, onClick = {
                    val (filename, text) = state.bottomSheet.selectedOutputSnapshot() ?: return@IconButton
                    scope.launch {
                        sharing = true
                        try {
                            val file = withContext(Dispatchers.IO) {
                                java.io.File(activity.filesDir, "$filename.txt").apply { writeText(text) }
                            }
                            com.itsaky.androidide.utils.IntentUtils.shareFile(activity, file, "text/plain")
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            activity.showEditorMessage(activity.getString(R.string.editor_share_output), activity.getString(R.string.msg_output_text_extraction_failed))
                        } finally { sharing = false }
                    }
                }) { Icon(Icons.Default.Share, stringResource(R.string.editor_share_output)) }
                if (sharing) CircularProgressIndicator(Modifier.size(24.dp))
            },
        )
        if (state.dialog == null && !state.bottomSheet.visible && !drawer.isOpen && !state.busy) EditorOnboarding(guideTargets)
        state.dialog?.invoke()
    }
}

@Composable
private fun DrawableIcon(drawable: Drawable, description: String?) {
    val bitmap = remember(drawable) { drawable.toBitmap(32, 32).asImageBitmap() }
    Icon(bitmap, description, Modifier.size(24.dp), tint = Color.Unspecified)
}

private fun showMenuItem(state: EditorUiState, menu: MenuBuilder, item: MenuItem) {
    if (item.hasSubMenu()) state.dialog = { EditorMenuDialog(item.subMenu!!) { state.dialog = null } }
    else menu.performIdentifierAction(item.itemId, 0)
}

@Composable
fun EditorMenuDialog(menu: Menu, onDismiss: () -> Unit) {
    var current by remember(menu) { mutableStateOf(menu) }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            (0 until current.size()).map(current::getItem).filter { it.isVisible }.forEach { item ->
                TextButton(enabled = item.isEnabled, onClick = {
                    if (item.hasSubMenu()) current = item.subMenu!! else { onDismiss(); current.performIdentifierAction(item.itemId, 0) }
                }) {
                    if (item.isCheckable) Checkbox(checked = item.isChecked, onCheckedChange = null)
                    Text(item.title.toString())
                }
            }
        }
    })
}

@Composable
internal fun EditorMemoryChart(samples: List<Pair<String, List<Float>>>) {
    val palette = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        samples.forEach { (name, points) -> Text("$name: ${points.lastOrNull()?.toInt() ?: 0} MB", style = MaterialTheme.typography.labelSmall) }
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val max = samples.flatMap { it.second }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            samples.forEachIndexed { index, (_, points) ->
                points.zipWithNext().forEachIndexed { i, (a, b) ->
                    val unit = size.width / (points.size - 1).coerceAtLeast(1)
                    drawLine(palette[index % palette.size], Offset(i * unit, size.height * (1 - a / max)), Offset((i + 1) * unit, size.height * (1 - b / max)), strokeWidth = 2.dp.toPx())
                }
            }
        }
    }
}
