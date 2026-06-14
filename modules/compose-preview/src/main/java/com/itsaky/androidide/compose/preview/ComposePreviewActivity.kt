/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.itsaky.androidide.compose.preview.ui.BuildStats
import com.itsaky.androidide.compose.preview.ui.ColorEyedropper
import com.itsaky.androidide.compose.preview.ui.DebugDrawer
import com.itsaky.androidide.compose.preview.ui.DeviceFrame
import com.itsaky.androidide.compose.preview.ui.DeviceProfileSheet
import com.itsaky.androidide.compose.preview.ui.EditorState
import com.itsaky.androidide.compose.preview.ui.EditorStatusBar
import com.itsaky.androidide.compose.preview.ui.EditorToolbar
import com.itsaky.androidide.compose.preview.ui.EditorTool
import com.itsaky.androidide.compose.preview.ui.PreviewLog
import com.itsaky.androidide.compose.preview.ui.PreviewLogcatSink
import com.itsaky.androidide.compose.preview.ui.PreviewToolbar
import com.itsaky.androidide.compose.preview.ui.PreviewToolbarActions
import com.itsaky.androidide.compose.preview.ui.PreviewToolbarState
import com.itsaky.androidide.compose.preview.ui.RecomposeTracker
import com.itsaky.androidide.compose.preview.ui.ResolutionEditor
import com.itsaky.androidide.compose.preview.ui.Selection
import com.itsaky.androidide.compose.preview.ui.SelectionOverlay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.resources.R as ResourcesR
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * 预览入口 Activity v2.1.
 *
 * 全 Compose UI:
 * - 顶栏: PreviewToolbar (设备 / 主题 / 缩放 / 调试)
 * - 主体: 设备框 (DeviceFrame) 套预览内容
 * - 错误: 错误详情可滚动
 * - 加载: 居中 CircularProgressIndicator
 *
 * 保持向后兼容: 旧 [EXTRA_SOURCE_CODE] / [EXTRA_FILE_PATH] 仍然有效.
 */
class ComposePreviewActivity : androidx.appcompat.app.AppCompatActivity() {

    private val viewModel: ComposePreviewViewModel by viewModels()

    private val sourceCode: String by lazy {
        intent.getStringExtra(EXTRA_SOURCE_CODE) ?: ""
    }

    private val filePath: String by lazy {
        intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposePreviewScreen(
                viewModel = viewModel,
                onClose = { finish() },
            )
        }

        viewModel.initialize(this, filePath)
        if (sourceCode.isNotBlank()) {
            viewModel.onSourceChanged(sourceCode)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 卸载 logcat sink, 恢复原始 System.out / System.err
        PreviewLog.sink.uninstall()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ComposePreviewActivity::class.java)

        private const val EXTRA_SOURCE_CODE = "source_code"
        private const val EXTRA_FILE_PATH = "file_path"

        fun start(context: Context, sourceCode: String, filePath: String) {
            val intent = Intent(context, ComposePreviewActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_CODE, sourceCode)
                putExtra(EXTRA_FILE_PATH, filePath)
            }
            context.startActivity(intent)
        }
    }
}

/**
 * 主屏 Composable v2.1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposePreviewScreen(
    viewModel: ComposePreviewViewModel,
    onClose: () -> Unit,
) {
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val deviceConfig by viewModel.deviceConfig.collectAsStateWithLifecycle()
    val viewport by viewModel.viewport.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val debugEnabled by viewModel.debugEnabled.collectAsStateWithLifecycle()

    // v2.1 调试面板状态
    val inspectorNodes by viewModel.inspectorNodes.collectAsStateWithLifecycle()
    val buildStats by viewModel.buildStats.collectAsStateWithLifecycle()

    // v2.1 可视化编辑器状态
    val editorEnabled by viewModel.editorEnabled.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val aspectLock by viewModel.aspectLock.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showResolutionEditor by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // v2.1 调试面板: 单例 per-Activity (跨 recompose 保持状态)
    val logcatSink = remember { PreviewLog.sink.also { it.install() } }
    val recomposeTracker = remember { RecomposeTracker() }

    // 应用主题
    val colorScheme = when (theme) {
        PreviewTheme.Light -> lightColorScheme()
        PreviewTheme.Dark -> darkColorScheme()
        PreviewTheme.Custom -> lightColorScheme() // TODO: 真实自定义
    }
    androidx.compose.material3.MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏
                PreviewToolbar(
                    state = PreviewToolbarState(
                        deviceName = deviceConfig.profile.displayName,
                        themeLabel = theme.name,
                        zoom = viewport.zoom,
                        showSystemBars = deviceConfig.showStatusBar,
                        debugEnabled = debugEnabled,
                        editorEnabled = editorEnabled,
                    ),
                    actions = PreviewToolbarActions(
                        onOpenDeviceSheet = { showDeviceSheet = true },
                        onCycleTheme = { viewModel.cycleTheme() },
                        onSetZoom = { viewModel.setZoom(it) },
                        onFitZoom = { viewModel.fitZoom() },
                        onToggleSystemBars = { viewModel.toggleSystemBars() },
                        onToggleDebug = { viewModel.toggleDebug() },
                        onToggleEditor = { viewModel.toggleEditor() },
                        onClose = onClose,
                    ),
                )

                HorizontalDivider()

                // v2.1 编辑器工具栏 (二级)
                if (editorEnabled) {
                    EditorToolbar(
                        tool = editorState.tool,
                        onToolChange = { viewModel.setEditorTool(it) },
                        onClose = { viewModel.toggleEditor() },
                        aspectLock = aspectLock,
                        onToggleAspectLock = { viewModel.toggleAspectLock() },
                        onReset = {
                            viewModel.resetTranslation()
                            viewModel.resetViewportOffset()
                        },
                        hasSelection = editorState.selection != null,
                    )
                    EditorStatusBar(state = editorState)
                }

                // 主体
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val s = previewState) {
                        is PreviewState.Idle, PreviewState.Initializing -> LoadingPanel("Initializing…")
                        is PreviewState.Compiling -> LoadingPanel("Compiling…")
                        is PreviewState.Building -> LoadingPanel("Building project…\nFirst build may take 10-15 minutes")
                        is PreviewState.Empty -> EmptyPanel()
                        is PreviewState.NeedsBuild -> NeedsBuildPanel(
                            modulePath = s.modulePath,
                            onBuild = {
                                triggerBuild(context, viewModel, s.modulePath, s.variantName)
                            }
                        )
                        is PreviewState.Error -> ErrorPanel(
                            message = s.message,
                            diagnostics = s.diagnostics,
                        )
                        is PreviewState.Ready -> ReadyPanel(
                            previewState = s,
                            deviceConfig = deviceConfig,
                            viewport = viewport,
                            onBuildFailed = {
                                viewModel.setBuildFailed()
                            }
                        )
                    }

                    // v2.1 编辑器: 选中覆盖层 + 点击响应
                    if (editorEnabled) {
                        EditorInteractionLayer(
                            editorState = editorState,
                            inspectorNodes = inspectorNodes,
                            aspectLock = aspectLock,
                            onSelectAt = { offset, node ->
                                if (editorState.tool == EditorTool.Select ||
                                    editorState.tool == EditorTool.Drag) {
                                    viewModel.setSelection(
                                        Selection(
                                            nodeId = node.id,
                                            composableName = node.composableName,
                                            bounds = node.bounds,
                                        )
                                    )
                                } else if (editorState.tool == EditorTool.Eyedropper) {
                                    val color = ColorEyedropper.estimateColor(node)
                                    viewModel.setSelection(
                                        Selection(
                                            nodeId = node.id,
                                            composableName = node.composableName,
                                            bounds = node.bounds,
                                            sampledColor = color,
                                        )
                                    )
                                }
                            },
                            onClearSelection = { viewModel.clearSelection() },
                            onTranslate = { dx, dy ->
                                viewModel.translateSelection(dx, dy)
                            },
                            onResize = { handle, dx, dy ->
                                viewModel.resizeSelection(handle, dx, dy)
                            },
                            onPan = { dxDp, dyDp ->
                                viewModel.panViewport(dxDp, dyDp)
                            },
                        )
                    }
                }
            }
        }
    }

    // 设备 Sheet
    if (showDeviceSheet) {
        DeviceProfileSheet(
            sheetState = sheetState,
            selectedId = deviceConfig.profile.id,
            onSelect = { profile ->
                viewModel.selectDevice(profile)
            },
            onCustom = {
                showResolutionEditor = true
            },
            onDismiss = {
                showDeviceSheet = false
            }
        )
    }

    // 自定义设备 Dialog
    if (showResolutionEditor) {
        ResolutionEditor(
            initial = deviceConfig.profile,
            onConfirm = { newProfile ->
                viewModel.selectDevice(newProfile)
                showResolutionEditor = false
            },
            onDismiss = {
                showResolutionEditor = false
            }
        )
    }

    // v2.1 调试面板 (4 tabs: Inspector / Recompose / Logcat / Stats)
    if (debugEnabled) {
        val debugSheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
        DebugDrawer(
            sheetState = debugSheetState,
            onDismiss = { viewModel.toggleDebug() },
            inspectorNodes = inspectorNodes,
            recomposeTracker = recomposeTracker,
            logcat = logcatSink,
            stats = buildStats,
        )
    }
}

@Composable
private fun LoadingPanel(message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyPanel() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("未发现 @Preview 标注的 Composable", style = MaterialTheme.typography.titleMedium)
        Text(
            "在 Composable 函数上添加 @Preview 标注后, 重新打开预览",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NeedsBuildPanel(modulePath: String, onBuild: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("需要先构建项目", style = MaterialTheme.typography.titleMedium)
        Text(
            "模块: $modulePath",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Button(onClick = onBuild) {
            Text("Build Project")
        }
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    diagnostics: List<com.itsaky.androidide.compose.preview.compiler.CompileDiagnostic>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "Error",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (diagnostics.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                diagnostics.forEach { d ->
                    val loc = buildString {
                        d.file?.let { append(it.substringAfterLast('/')) }
                        d.line?.let { append(":$it") }
                        d.column?.let { append(":$it") }
                    }
                    Text(
                        text = if (loc.isNotBlank()) "$loc\n  [${d.severity}] ${d.message}" else "[${d.severity}] ${d.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ReadyPanel(
    previewState: PreviewState.Ready,
    deviceConfig: DeviceConfig,
    viewport: ViewportState,
    onBuildFailed: () -> Unit,
) {
    // 设备框 + 内容
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // v2.1 视口平移 (Pan 工具): 用 offset 包裹 DeviceFrame
        val panOffsetX = viewport.offsetXdp.dp
        val panOffsetY = viewport.offsetYdp.dp
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .offset(x = panOffsetX, y = panOffsetY)
        ) {
            // 用 DeviceFrame 包裹实际预览 ComposeView
            DeviceFrame(
                profile = deviceConfig.profile,
                systemBarsTheme = deviceConfig.systemBarsTheme,
                showStatusBar = deviceConfig.showStatusBar,
                showNavigationBar = deviceConfig.showNavigationBar,
                showCutout = deviceConfig.showCutout,
                showChassis = deviceConfig.showChassis,
                useGestureNav = deviceConfig.useGestureNav,
            ) {
            // 实际渲染由 ComposableRenderer 负责
            // 这里放置一个标记 Box
            RenderTargetMarker(
                dexFile = previewState.dexFile,
                className = previewState.className,
                runtimeDex = previewState.runtimeDex,
                projectDexFiles = previewState.projectDexFiles,
                previewConfigs = previewState.previewConfigs,
                classLoader = remember { com.itsaky.androidide.compose.preview.runtime.ComposeClassLoader(context) },
            )
        }
    }
}

/**
 * 标记渲染目标 - 实际由 ComposableRenderer 接管.
 */
@Composable
private fun RenderTargetMarker(
    dexFile: java.io.File,
    className: String,
    runtimeDex: java.io.File?,
    projectDexFiles: List<java.io.File>,
    previewConfigs: List<PreviewConfig>,
    classLoader: com.itsaky.androidide.compose.preview.runtime.ComposeClassLoader,
) {
    androidx.compose.runtime.LaunchedEffect(dexFile, className) {
        classLoader.setProjectDexFiles(projectDexFiles)
        classLoader.setRuntimeDex(runtimeDex)
    }
    // 实际渲染由 Activity / Renderer 接管
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        // 这里只是占位; 真实渲染由 [ComposePreviewActivity] 持有的
        // ComposableRenderer 通过 binding.singlePreviewView.setContent() 驱动
        // (该 v2.1 屏幕只负责 chrome, 渲染仍由旧 XML 流程驱动以保持兼容)
    }
}

private fun triggerBuild(
    context: Context,
    viewModel: ComposePreviewViewModel,
    modulePath: String,
    variantName: String,
) {
    val buildService = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
    if (buildService == null) {
        LOG.error("BuildService not available")
        return
    }

    if (buildService.isBuildInProgress) {
        return
    }

    viewModel.setBuildingState()

    val capitalizedVariant = variantName.replaceFirstChar { it.uppercaseChar() }
    val task = if (modulePath.isNotEmpty()) {
        "$modulePath:assemble$capitalizedVariant"
    } else {
        "assemble$capitalizedVariant"
    }

    buildService.executeTasks(task).whenComplete { result, error ->
        (context as? android.app.Activity)?.runOnUiThread {
            if (error != null || !result.isSuccessful) {
                viewModel.setBuildFailed()
            } else {
                viewModel.refreshAfterBuild(context)
            }
        }
    }
}

/**
 * v2.1 编辑器交互层 (P2 + Resize + Pan).
 *
 * 叠在预览内容之上, 提供:
 * 1. 点击响应: 根据 [EditorState.tool] 选择 / 取色 / 清除选中
 * 2. 选中框绘制: 包裹 [SelectionOverlay] (8 手柄 + 拖动 + Resize)
 * 3. Pan 工具: 在空白处拖动 → 视口平移
 *
 * @param editorState 当前 editor state
 * @param inspectorNodes 全部节点 (hit-test 用)
 * @param aspectLock 纵横比锁定状态 (传给 SelectionOverlay)
 * @param onSelectAt 命中节点回调 (offset + 命中的 NodeInfo)
 * @param onClearSelection 清除选中回调
 * @param onTranslate 拖动选中元素回调 (Drag 工具 - 平移)
 * @param onResize resize 选中元素回调 (Drag 工具 - 8 手柄)
 * @param onPan 视口平移回调 (Pan 工具, 单位 dp)
 */
@Composable
private fun EditorInteractionLayer(
    editorState: EditorState,
    inspectorNodes: List<NodeInfo>,
    aspectLock: Boolean,
    onSelectAt: (androidx.compose.ui.geometry.Offset, NodeInfo) -> Unit,
    onClearSelection: () -> Unit,
    onTranslate: (Float, Float) -> Unit,
    onResize: (com.itsaky.androidide.compose.preview.ui.HandlePosition, Float, Float) -> Unit,
    onPan: (Float, Float) -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        // 点击 + Pan 响应层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(editorState.tool, inspectorNodes) {
                    detectTapGestures(
                        onTap = { offset ->
                            when (editorState.tool) {
                                EditorTool.Select, EditorTool.Drag -> {
                                    val node = ColorEyedropper.findNodeAt(inspectorNodes, offset)
                                    if (node != null) onSelectAt(offset, node)
                                    else onClearSelection()
                                }
                                EditorTool.Eyedropper -> {
                                    val node = ColorEyedropper.findNodeAt(inspectorNodes, offset)
                                    if (node != null) onSelectAt(offset, node)
                                }
                                EditorTool.Pan -> { /* pan 由 drag 处理 */ }
                            }
                        },
                        onLongPress = { onClearSelection() },
                    )
                }
                .pointerInput(editorState.tool) {
                    if (editorState.tool == EditorTool.Pan) {
                        androidx.compose.foundation.gestures.detectDragGestures(
                            onDragStart = { },
                            onDragEnd = { },
                            onDragCancel = { },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dxDp = dragAmount.x / density.density
                                val dyDp = dragAmount.y / density.density
                                onPan(dxDp, dyDp)
                            },
                        )
                    }
                }
        )

        // 选中覆盖层 (含 8 手柄 + 拖动 + resize 响应)
        SelectionOverlay(
            selection = editorState.selection,
            tool = editorState.tool,
            aspectLock = aspectLock,
            onSelectionChange = { newSel ->
                val old = editorState.selection
                if (old == null) return@SelectionOverlay
                val dx = newSel.translationX - old.translationX
                val dy = newSel.translationY - old.translationY
                if (dx != 0f || dy != 0f) {
                    onTranslate(dx, dy)
                }
            },
            onResize = onResize,
        )
    }
}

private val LOG = LoggerFactory.getLogger("ComposePreviewActivity")
