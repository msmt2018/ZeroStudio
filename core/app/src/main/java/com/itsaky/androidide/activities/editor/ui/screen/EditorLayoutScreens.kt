package com.itsaky.androidide.activities.editor.ui.screen

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.R

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

/** Compose equivalent of `layout_mem_usage.xml`; callers provide the chart implementation. */
@Composable
fun MemoryUsageScreen(modifier: Modifier = Modifier, chart: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier = modifier.fillMaxWidth().height(200.dp), content = chart)
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
 * The Android-only gesture bubble, symbol input, tabs, and pager are explicit slots so existing
 * editor implementations can move to Compose independently without losing their behavior.
 */
@Composable
fun EditorBottomSheetScreen(
    cursorPosition: String,
    showBottomAction: Boolean,
    modifier: Modifier = Modifier,
    gestureBubble: @Composable () -> Unit = {},
    buildStatus: @Composable () -> Unit = {},
    bottomAction: @Composable () -> Unit = {},
    symbolInput: @Composable () -> Unit = {},
    tabs: @Composable () -> Unit = {},
    drawerContent: @Composable BoxScope.() -> Unit = {},
    bottomSpace: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(Color.Transparent)) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(24.dp)) { gestureBubble() }
            Surface(color = MaterialTheme.colorScheme.surface) {
                Box {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).scale(0.9f),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {}
                    Column {
                    HorizontalDivider(thickness = 0.1.dp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { if (showBottomAction) bottomAction() else buildStatus() }
                        Text(cursorPosition, Modifier.padding(end = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace)
                    }
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Surface(color = MaterialTheme.colorScheme.surface) { symbolInput() }
        }
        Column(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surface)) {
            tabs()
            Box(Modifier.fillMaxWidth().weight(1f), content = drawerContent)
            bottomSpace()
        }
    }
}
