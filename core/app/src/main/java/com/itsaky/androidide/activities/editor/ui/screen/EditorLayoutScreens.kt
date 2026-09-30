package com.itsaky.androidide.activities.editor.ui.screen

import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.zero.studio.widget.editor.symbolinput.AdvancedSymbolInputView
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.FileDownload
import androidx.compose.material.icons.automirrored.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.github.mikephil.charting.charts.LineChart
import com.itsaky.androidide.R
import com.itsaky.androidide.ui.EdgeSnapBubbleView
import com.itsaky.androidide.ui.EditorBottomSheet
import io.github.rosemoe.sora.widget.CodeEditor

/** Compose equivalent of `layout_search_project.xml`. */
@Composable
fun SearchProjectScreen(
    searchText: String,
    filterText: String,
    onSearchTextChange: (String) -> Unit,
    onFilterTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    modules: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
    ) {
        Text(stringResource(R.string.msg_search_modules), modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), content = modules)
        EditorTextField(searchText, onSearchTextChange, stringResource(R.string.text_to_search), Icons.Default.Search)
        EditorTextField(filterText, onFilterTextChange, stringResource(R.string.hint_find_project_filter), Icons.Default.FilterList, stringResource(R.string.msg_find_project_filter))
    }
}

@Composable
private fun EditorTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, helper: String? = null) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().padding(horizontal = 8.dp), label = { Text(label) }, leadingIcon = { Icon(icon, null) }, supportingText = helper?.let { { Text(it) } }, singleLine = true)
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
            Icon(Icons.AutoMirrored.Filled.CallSplit, stringResource(R.string.git_action_branch_switch), Modifier.size(20.dp))
            Text(branchStatus, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            IconButton(onPull, Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Filled.FileDownload, stringResource(R.string.git_action_pull)) }
            IconButton(onPush, Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Filled.FileUpload, stringResource(R.string.git_action_push)) }
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
 * This function intentionally hosts [EditorBottomSheet], rather than reimplementing its children
 * as empty Compose slots. `EditorBottomSheet` owns the symbol-input touch exclusion, IME/peek
 * height synchronization, page adapter, TabLayoutMediator, and custom bottom-sheet behavior.
 * Replacing it with Compose placeholders breaks these contracts.
 */
@Composable
fun EditorBottomSheetScreen(
    modifier: Modifier = Modifier,
    onBottomSheetCreated: (EditorBottomSheet) -> Unit = {},
    onBottomSheetUpdated: (EditorBottomSheet) -> Unit = {},
) {
    val activity = requireFragmentActivity()
    AndroidView(
        factory = {
            CoordinatorLayout(activity).apply {
                addView(
                    EditorBottomSheet(activity).also(onBottomSheetCreated),
                    CoordinatorLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { coordinator ->
            (coordinator.getChildAt(0) as? EditorBottomSheet)?.let(onBottomSheetUpdated)
        },
    )
}

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
    AndroidView(
        factory = { context ->
            AdvancedSymbolInputView(context).also {
                it.elevation = 4f * context.resources.displayMetrics.density
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

@Composable
private fun requireFragmentActivity(): FragmentActivity {
    var context: Context = androidx.compose.ui.platform.LocalContext.current
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return context as? FragmentActivity
        ?: error("EditorBottomSheetScreen must be hosted by a FragmentActivity")
}
