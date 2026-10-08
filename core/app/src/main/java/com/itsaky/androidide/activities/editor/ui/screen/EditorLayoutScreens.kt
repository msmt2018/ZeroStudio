package com.itsaky.androidide.activities.editor.ui.screen

import android.view.LayoutInflater
import android.zero.studio.widget.editor.symbolinput.AdvancedSymbolInputView
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.tabs.TabLayout
import com.itsaky.androidide.R
import com.itsaky.androidide.ui.EdgeSnapBubbleView
import io.github.rosemoe.sora.widget.CodeEditor
import androidx.viewpager2.widget.ViewPager2

/** Compose equivalent of `layout_search_project.xml`. */
@Composable
fun SearchProjectScreen(
    searchText: String,
    filterText: String,
    onSearchTextChange: (String) -> Unit,
    onFilterTextChange: (String) -> Unit,
    modules: List<SearchProjectModule> = emptyList(),
    onModuleCheckedChange: (moduleId: String, checked: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
    ) {
        Text(stringResource(R.string.msg_search_modules), modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            modules.forEach { module ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = module.checked,
                        onCheckedChange = { checked -> onModuleCheckedChange(module.id, checked) },
                    )
                    Text(module.name)
                }
            }
        }
        EditorTextField(searchText, onSearchTextChange, stringResource(R.string.text_to_search), R.drawable.ic_search)
        EditorTextField(filterText, onFilterTextChange, stringResource(R.string.hint_find_project_filter), R.drawable.ic_filter, stringResource(R.string.msg_find_project_filter))
    }
}

data class SearchProjectModule(val id: String, val name: String, val checked: Boolean = true)

@Composable
private fun EditorTextField(value: String, onValueChange: (String) -> Unit, label: String, iconRes: Int, helper: String? = null) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().padding(horizontal = 8.dp), label = { Text(label) }, leadingIcon = { Icon(painterResource(iconRes), null) }, supportingText = helper?.let { { Text(it) } }, singleLine = true)
}

/**
 * Hosts the MPAndroidChart control used by `layout_mem_usage.xml`.
 *
 * `LineChart` is deliberately retained instead of drawing a look-alike Canvas chart: the editor's
 * memory watcher updates its [LineChart.data], axes, legend, and invalidation state directly.
 */
@Composable
fun MemoryUsageScreen(
    modifier: Modifier = Modifier,
    onChartCreated: (LineChart) -> Unit = {},
    onChartUpdated: (LineChart) -> Unit = {},
) {
    AndroidView(
        factory = { context -> LineChart(context).also(onChartCreated) },
        modifier = modifier.fillMaxWidth().height(200.dp),
        update = onChartUpdated,
    )
}

/** Compose equivalent of the padded, medium-corner launcher image in `layout_editor_sidebar_header.xml`. */
@Composable
fun EditorSidebarHeader(
    modifier: Modifier = Modifier,
    launcher: @Composable BoxScope.() -> Unit = {
        Image(painterResource(R.mipmap.ic_launcher), contentDescription = null, modifier = Modifier.fillMaxSize())
    },
) {
    Box(modifier = modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).padding(8.dp), contentAlignment = Alignment.Center, content = launcher)
}

/** Compose equivalent of `layout_editor_file_tree.xml`. */
@Composable
fun EditorFileTreeScreen(
    branchStatus: String,
    loading: Boolean,
    onPull: () -> Unit,
    onPush: () -> Unit,
    modifier: Modifier = Modifier,
    fileTree: @Composable BoxScope.() -> Unit = {},
) {
    Column(modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(46.dp).padding(top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_git), stringResource(R.string.git_action_branch_switch), Modifier.size(20.dp))
            Text(branchStatus, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            IconButton(onPull, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_git_pull), stringResource(R.string.git_action_pull)) }
            IconButton(onPush, Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_git_push), stringResource(R.string.git_action_push)) }
        }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), content = fileTree)
            if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

/** Compose equivalent of `layout_editor_build_status.xml`. */
@Composable
fun EditorBuildStatus(status: String, modifier: Modifier = Modifier) {
    Text(status, modifier.fillMaxWidth().height(14.dp).padding(horizontal = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
}

/** Compose equivalent of `layout_editor_bottom_action.xml`. */
@Composable
fun EditorBottomAction(action: String, progress: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().height(14.dp)) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(action, Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Compose equivalent of `layout_diagnostic_info.xml`. */
@Composable
fun DiagnosticInfo(message: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Text(message, modifier.padding(8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 11.sp)
    }
}

/**
 * Compose equivalent of `layout_editor_bottom_sheet.xml`.
 *
 * The custom controls that Compose cannot replace ([EdgeSnapBubbleView],
 * [AdvancedSymbolInputView], [TabLayout], and [ViewPager2]) remain real Android Views. This is
 * the supported migration path for their existing gesture, adapter, and editor-binding contracts.
 */
@Composable
fun EditorBottomSheetScreen(
    cursorPosition: String,
    headerPage: EditorBottomSheetHeaderPage,
    buildStatus: String,
    actionText: String,
    actionProgress: Float,
    editor: CodeEditor?,
    modifier: Modifier = Modifier,
    onBubbleCreated: (EdgeSnapBubbleView) -> Unit = {},
    onBubbleUpdated: (EdgeSnapBubbleView) -> Unit = {},
    onSymbolInputCreated: (AdvancedSymbolInputView) -> Unit = {},
    onOpenSymbolManager: (() -> Unit)? = null,
    onTabsCreated: (TabLayout) -> Unit = {},
    onTabsUpdated: (TabLayout) -> Unit = {},
    onPagerCreated: (ViewPager2) -> Unit = {},
    onPagerUpdated: (ViewPager2) -> Unit = {},
    bottomSpace: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        EdgeSnapBubble(onViewCreated = onBubbleCreated, onViewUpdated = onBubbleUpdated)
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box {
                Surface(
                    modifier = Modifier.matchParentSize().padding(horizontal = 16.dp).scale(0.9f),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {}
                Column {
                    HorizontalDivider(thickness = 0.1.dp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            when (headerPage) {
                                EditorBottomSheetHeaderPage.BuildStatus ->
                                    EditorBuildStatus(buildStatus)
                                EditorBottomSheetHeaderPage.Action ->
                                    EditorBottomAction(actionText, actionProgress)
                            }
                        }
                        Text(
                            text = cursorPosition,
                            modifier = Modifier.padding(end = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            AdvancedSymbolInput(
                editor = editor,
                onOpenManager = onOpenSymbolManager,
                onViewCreated = onSymbolInputCreated,
            )
        }
        Column(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surface)) {
            AndroidView(
                factory = { context ->
                    (LayoutInflater.from(context).inflate(
                        R.layout.layout_editor_bottom_sheet_tabs, null, false,
                    ) as TabLayout).also(onTabsCreated)
                },
                modifier = Modifier.fillMaxWidth(),
                update = onTabsUpdated,
            )
            AndroidView(
                factory = { context -> ViewPager2(context).also(onPagerCreated) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                update = onPagerUpdated,
            )
            bottomSpace()
        }
    }
}

enum class EditorBottomSheetHeaderPage { BuildStatus, Action }

/** Hosts the real custom gesture view and preserves its click and drag callbacks. */
@Composable
fun EdgeSnapBubble(
    modifier: Modifier = Modifier,
    onViewCreated: (EdgeSnapBubbleView) -> Unit = {},
    onViewUpdated: (EdgeSnapBubbleView) -> Unit = {},
) {
    AndroidView(
        factory = { context -> EdgeSnapBubbleView(context).also(onViewCreated) },
        modifier = modifier.fillMaxWidth().height(24.dp),
        update = onViewUpdated,
    )
}

/**
 * Hosts the real advanced symbol input control, including its paging, preferences, and editor
 * insertion gestures. Callers must pass the live Sora [CodeEditor] instance.
 */
@Composable
fun AdvancedSymbolInput(
    editor: CodeEditor?,
    modifier: Modifier = Modifier,
    onOpenManager: (() -> Unit)? = null,
    onViewCreated: (AdvancedSymbolInputView) -> Unit = {},
) {
    var symbolInput by remember { mutableStateOf<AdvancedSymbolInputView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, symbolInput) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            symbolInput?.onHostResume()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) symbolInput?.onHostResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        factory = { context ->
            AdvancedSymbolInputView(context).also {
                it.elevation = 4f * context.resources.displayMetrics.density
                symbolInput = it
                onViewCreated(it)
            }
        },
        modifier = modifier.fillMaxWidth(),
        update = { view ->
            editor?.let(view::bindEditor)
            view.onOpenManagerListener = onOpenManager
        },
    )
}
