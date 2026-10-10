package com.itsaky.androidide.activities.editor.ui.screen

import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.MenuItem
import android.zero.studio.widget.editor.symbolinput.SymbolInputPanel
import android.zero.studio.widget.editor.symbolinput.SymbolManagerActivity
import androidx.activity.compose.BackHandler
import androidx.appcompat.view.menu.MenuBuilder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.ActionsRegistry
import com.itsaky.androidide.activities.editor.BaseEditorActivity
import com.itsaky.androidide.activities.editor.ui.state.EditorUiState
import com.itsaky.androidide.onboarding.OnboardingTarget
import com.itsaky.androidide.onboarding.onboardingBind
import com.itsaky.androidide.ui.SymbolInputVisibilityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑器主操作区：完全纯 Compose 现代架构，吸附式 PopupMenu 取代旧版模态 Dialog。
 */
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

    val colors = editorColorScheme(activity)
    val documentStates = rememberEditorPageStates(state.documents)

    MaterialTheme(colorScheme = colors) {
        EditorWorkspaceScreen(
            title = "",
            drawerState = drawer,
            bottomSheetState = sheet,
            bottomSheetVisible = state.bottomSheet.visible,
            onDismissBottomSheet = { state.bottomSheet.forceCollapse() },
            sidebar = state.sidebar,
            bottomPages = state.bottomSheet.pages,
            openDrawerDescription = stringResource(R.string.msg_file_tree),
            closeDescription = stringResource(R.string.btn_close),
            fragmentPage = { page -> EditorFragmentPage(page) },
            sidebarIcon = { page ->
                val action = ActionsRegistry.getInstance().getActions(ActionItem.Location.EDITOR_SIDEBAR)[page.id]
                val isSelected = state.sidebar.selectedId == page.id
                val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Box(Modifier.padding(4.dp)) {
                    action?.icon?.let { ActionIcon(it, page.title, tint = tint) }
                        ?: Icon(Icons.Default.Folder, page.title, tint = tint)
                }
            },
            toolbarActions = {
                state.toolbarVersion // 监听版本变更以触发重绘
                val items = (0 until activity.toolbarMenu.size())
                    .map(activity.toolbarMenu::getItem)
                    .filter { it.isVisible }

                // 各个 Action 图标按钮：支持动态着色与子菜单吸附弹出
                items.filter { it.icon != null }.forEach { item ->
                    ActionToolbarItem(item = item, menu = activity.toolbarMenu)
                }

                IconButton(onClick = { state.memoryVisible = !state.memoryVisible }) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = stringResource(R.string.editor_memory_usage),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // 2. 核心：Overflow 菜单彻底重做为贴附在按钮下方的 Compose DropdownMenu
                var overflowExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.editor_actions),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                        offset = DpOffset(0.dp, 4.dp),
                    ) {
                        items.filter { it.icon == null }.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = item.title?.toString().orEmpty(),
                                        fontSize = 13.sp,
                                        color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    )
                                },
                                enabled = item.isEnabled,
                                onClick = {
                                    overflowExpanded = false
                                    activity.toolbarMenu.performIdentifierAction(item.itemId, 0)
                                }
                            )
                        }
                    }
                }
            },
            editorTabs = {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.memoryVisible) EditorMemoryChart(state.memories)
                if (state.tabs.visible) state.tabs.Content()
            },
            editorContent = {
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
                            TextButton(onClick = { state.bottomSheet.visible = true }, modifier = Modifier.weight(1f)) {
                                Text(state.bottomSheet.status.ifBlank { stringResource(R.string.build_output) }, maxLines = 1)
                            }
                            Text(state.cursor, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelSmall)
                            IconButton(modifier = Modifier.onboardingBind(guideTargets[0]), onClick = { state.bottomSheet.visible = true }) {
                                Icon(Icons.Default.ExpandLess, stringResource(R.string.build_output))
                            }
                        }
                        if (state.headerVisible && !SymbolInputVisibilityManager.previewHidden) {
                            if (state.bottomSheet.headerPage == 1) {
                                EditorBottomAction(state.bottomSheet.actionText, state.bottomSheet.actionProgress)
                            }
                            SymbolInputPanel(
                                if (state.surface == 0) state.currentEditor?.editor else null,
                                onOpenManager = { activity.startActivity(Intent(activity, SymbolManagerActivity::class.java)) },
                                modifier = Modifier
                                    .onboardingBind(guideTargets[2])
                                    .onSizeChanged { state.symbolHeight = it.height },
                            )
                        }
                    }
                }
            },
            bottomActions = {
                IconButton(onClick = state.bottomSheet::clearSelectedOutput) {
                    Icon(Icons.Default.Delete, stringResource(R.string.editor_clear_output))
                }
                IconButton(
                    enabled = !sharing,
                    onClick = {
                        val snapshot = state.bottomSheet.selectedOutputSnapshot() ?: return@IconButton
                        val (filename, text) = snapshot
                        scope.launch {
                            sharing = true
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    java.io.File(activity.filesDir, "$filename.txt").apply { writeText(text) }
                                }
                                com.itsaky.androidide.utils.IntentUtils.shareFile(activity, file, "text/plain")
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                activity.showEditorMessage(activity.getString(R.string.editor_share_output), activity.getString(R.string.msg_output_text_extraction_failed))
                            } finally {
                                sharing = false
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.Share, stringResource(R.string.editor_share_output))
                }
                if (sharing) CircularProgressIndicator(Modifier.size(24.dp))
            },
        )
        state.dialog?.invoke()
    }
}

/**
 * 工具栏单项与下沉吸附式 SubMenu
 */
@Composable
private fun ActionToolbarItem(
    item: MenuItem,
    menu: MenuBuilder,
) {
    var subMenuExpanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            enabled = item.isEnabled,
            onClick = {
                if (item.hasSubMenu()) {
                    subMenuExpanded = true
                } else {
                    menu.performIdentifierAction(item.itemId, 0)
                }
            },
            modifier = Modifier.size(36.dp),
        ) {
            val tint = if (item.isEnabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) // 规范的禁用暗色/灰色
            }
            ActionIcon(drawable = item.icon!!, description = item.title?.toString(), tint = tint)
        }

        // 子菜单吸附弹出
        if (item.hasSubMenu()) {
            DropdownMenu(
                expanded = subMenuExpanded,
                onDismissRequest = { subMenuExpanded = false },
                offset = DpOffset(0.dp, 4.dp),
            ) {
                val subMenu = item.subMenu!!
                (0 until subMenu.size()).map(subMenu::getItem).filter { it.isVisible }.forEach { subItem ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = subItem.title?.toString().orEmpty(),
                                fontSize = 13.sp,
                                color = if (subItem.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        },
                        enabled = subItem.isEnabled,
                        onClick = {
                            subMenuExpanded = false
                            subMenu.performIdentifierAction(subItem.itemId, 0)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionIcon(
    drawable: Drawable,
    description: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(drawable) {
        drawable.toBitmap(24, 24).asImageBitmap()
    }
    Icon(
        bitmap = bitmap,
        contentDescription = description,
        modifier = modifier.size(20.dp),
        tint = tint,
    )
}