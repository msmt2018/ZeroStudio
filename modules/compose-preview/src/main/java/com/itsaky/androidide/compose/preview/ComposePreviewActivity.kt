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
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.itsaky.androidide.compose.preview.runtime.PreviewRenderEngine
import com.itsaky.androidide.compose.preview.ui.AttributeEditPanel
import com.itsaky.androidide.compose.preview.ui.ComposableFunctionPicker
import com.itsaky.androidide.compose.preview.ui.DebugToolbar
import com.itsaky.androidide.compose.preview.ui.DebugToolbarActions
import com.itsaky.androidide.compose.preview.ui.DebugToolbarState
import com.itsaky.androidide.compose.preview.ui.DeviceFrame
import com.itsaky.androidide.compose.preview.ui.DeviceProfileSheet
import com.itsaky.androidide.compose.preview.ui.ErrorBadge
import com.itsaky.androidide.compose.preview.ui.ErrorDetailSheet
import com.itsaky.androidide.compose.preview.ui.LayoutTreeBottomSheet
import com.itsaky.androidide.compose.preview.ui.LayoutInspectorOverlay
import com.itsaky.androidide.compose.preview.ui.PreviewToolbar
import com.itsaky.androidide.compose.preview.ui.PreviewToolbarActions
import com.itsaky.androidide.compose.preview.ui.PreviewToolbarState
import com.itsaky.androidide.compose.preview.ui.ResolutionEditor
import com.itsaky.androidide.compose.preview.data.device.DesktopApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.resources.R as ResourcesR
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import androidx.compose.ui.platform.ComposeView

/**
 * 预览入口 Activity v3.
 *
 * ## v3 vs v2/v2.1 关键变化
 *
 * 之前 v2.1 把 `RenderTargetMarker` 放在 Composable 子树内, 用 `remember` 创建 [ComposeView]
 * + `LaunchedEffect` 驱动 `ComposableRenderer.render`. 这种做法有几个根深蒂固的 bug:
 *
 * 1. **AndroidView 测量冲突**: `AndroidView` 把 [ComposeView] 当作传统 View 嵌入到 Composable 子树,
 *    但 ComposeView 自己也是一个 Compose 容器, 在 SubcomposeLayout 嵌套测量时分配到的尺寸为 0.
 *    结果是 "BUILD SUCCESSFUL 后不显示预览, 只显示一个空白框".
 *
 * 2. **LaunchedEffect 时序问题**: `LaunchedEffect(dexFile, className, ...)` 的 keys 在
 *    `PreviewState.Ready` 第一次进入时会触发, 但 setContent 内部的组合是异步的, 导致
 *    `currentComposer` 拿不到正确值.
 *
 * 3. **可变的 ComposeClassLoader**: `setProjectDexFiles` / `setRuntimeDex` 是 setContent 后
 *    异步调用, 此时 ClassLoader 已开始使用, 出现 `ClassNotFoundException`.
 *
 * v3 修复:
 *
 * - **把 [ComposeView] 装到 Activity 的根 FrameLayout**, 而不是 Composable 子树.
 *   引擎 [PreviewRenderEngine] 在 Activity onCreate 时 attach, 在 onDestroy 时 detach.
 * - **[DexRuntime] 一次性不可变加载**所有 dex (preview + project + runtime).
 * - **[ComposableInvoker] 处理 4 种签名变种** (含 $default), 不再因参数数量错误而 invoke 失败.
 * - **错误显式上抛**到 UI 层.
 */
class ComposePreviewActivity : androidx.appcompat.app.AppCompatActivity() {

    internal val viewModel: ComposePreviewViewModel by viewModels()

    private val sourceCode: String by lazy {
        intent.getStringExtra(EXTRA_SOURCE_CODE) ?: ""
    }

    private val filePath: String by lazy {
        intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 用 ComposeView 作为 setContent 的根容器, 这样 Activity 已经有了一个
        // "已知大小" 的 Compose 容器, ReadyPanel 内嵌的 AndroidView/FrameLayout 容器
        // 会被这个 ComposeView 正确测量.
        val rootComposeView = ComposeView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(rootComposeView)

        rootComposeView.setContent {
            // 把 Activity 自身显式传进 Composable, 而不是通过 LocalContext.current as Activity 拿,
            // 因为 LocalContext 在 AndroidView 嵌套时可能 wrap 一层 ContextWrapper, 导致
            // 类型转换返回 null. 显式参数最稳定可靠.
            ComposePreviewScreen(
                activity = this,
                viewModel = viewModel,
                onClose = { finish() },
            )
        }

        viewModel.initialize(this, filePath)
        if (sourceCode.isNotBlank()) {
            viewModel.onSourceChanged(sourceCode)
        }

        // 【PR-C】订阅 previewState 拿 modulePath, 第一次 Ready 时加载用户 app
        // (解析 manifest android:icon + label). 失败不阻塞, 桌面还能用.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.previewState.collect { state ->
                    if (state is PreviewState.Ready) {
                        // 加载用户 app 一次 (addUserApp 内部有重复检查)
                        viewModel.loadUserApp(viewModel.getModulePath().takeIf { it.isNotEmpty() })
                        // 渲染 preview
                        renderEngine?.let { engine ->
                            val functionName = viewModel.selectedPreview.value
                                ?: state.previewConfigs.firstOrNull()?.functionName
                                ?: return@collect
                            // v3.4: 传入对应 @Composable 的 PreviewConfig, 让 uiMode /
                            // showBackground / backgroundColor 真正生效.
                            // v3.5: 传 deviceConfig.orientation, 让 LocalConfiguration 注入横竖屏.
                            val previewConfig = state.previewConfigs
                                .firstOrNull { it.functionName == functionName }
                            engine.render(
                                previewDex = state.dexFile,
                                projectDex = state.projectDexFiles,
                                className = state.className,
                                functionName = functionName,
                                previewConfig = previewConfig,
                                orientation = state.deviceConfig.orientation,
                            )
                        }
                    }
                }
            }
        }

        // 【v3.3】订阅 selectedPreview 变化 — 用户从调试模式下拉选择新 @Composable 时
        // 立刻重新渲染. 注意: 这里 ready 状态没变, 所以上面的 collect 不会重新触发.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedPreview.collect { functionName ->
                    if (functionName == null) return@collect
                    val state = viewModel.previewState.value
                    if (state !is PreviewState.Ready) return@collect
                    renderEngine?.let { engine ->
                        val previewConfig = state.previewConfigs
                            .firstOrNull { it.functionName == functionName }
                        engine.render(
                            previewDex = state.dexFile,
                            projectDex = state.projectDexFiles,
                            className = state.className,
                            functionName = functionName,
                            previewConfig = previewConfig,
                            orientation = state.deviceConfig.orientation,
                        )
                    }
                }
            }
        }
    }

    /**
     * 【v3.3】公开方法 — ComposableFunctionPicker 选中函数时调, 重新渲染.
     * (等价于 selectedPreview 的 collect, 但走 Activity 路径更直接, 适合从 sheet 调)
     */
    fun renderSelectedComposable(functionName: String) {
        viewModel.selectComposable(functionName)
    }

    /**
     * 【v3.3】拿 preview view 引用 (供 [ScreenshotExporter] 用).
     * 返回 render engine 的 ComposeView, 如果 engine 还没 attach 返回 null.
     */
    fun previewViewForExport(): android.view.View? {
        return renderEngine?.let { engine ->
            // 反射拿私有字段 composeView
            val field = engine.javaClass.getDeclaredField("composeView").apply { isAccessible = true }
            field.get(engine) as? android.view.View
        } ?: run {
            // fallback: 找根 FrameLayout 中的 ComposeView
            window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
                ?.let { root ->
                    findComposeView(root)
                }
        }
    }

    private fun findComposeView(view: android.view.View): android.view.View? {
        if (view is androidx.compose.ui.platform.ComposeView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findComposeView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private var renderEngine: PreviewRenderEngine? = null

    /**
     * 由 ComposePreviewScreen 内 AndroidView 调用, 注入预览容器.
     * 引擎 attach 到此 container 后, container 会显示 Compose 渲染.
     *
     * 【v3.2】如果上次 attach 的 container 与当前不同 (切 deviceSim / 切 profile
     * 触发 AndroidView 重建时), 强制 detach 旧引擎 + 新建引擎. 否则旧 ComposeView
     * 仍然 addView 在旧 FrameLayout 上, 用户看到的是"切回后黑屏".
     *
     * 【v3.6】在新建引擎时拷贝 [com.itsaky.androidide.compose.preview.runtime.LastRender]
     * 到新引擎, 让 [PreviewRenderEngine.attach] 自动重放上一次的渲染, 避免"切
     * 设备模式 / 切 profile 后预览区黑屏". 之前 v3.2 把旧引擎 detach 后, 新引擎
     * 的 [PreviewRenderEngine.lastRender] 默认是 null, attach 时不会自动重放,
     * 只能等 [viewModel.previewState] 重新发射 Ready 才会触发 render. 但
     * collect 不会对"当前值"重新发射, 第一次发射时 new engine 还没创建好,
     * 后续的 Ready 发射要么不会发生, 要么时序与 attach 错位, 表现为黑屏.
     */
    fun attachPreviewContainer(container: android.widget.FrameLayout) {
        val existing = renderEngine
        if (existing == null) {
            renderEngine = PreviewRenderEngine(this, container).also { it.attach() }
            return
        }
        if (existing.container !== container) {
            LOG.info(
                "Container changed ({} -> {}), recreating PreviewRenderEngine",
                System.identityHashCode(existing.container),
                System.identityHashCode(container),
            )
            // 拷贝 lastRender — 新 engine 的 attach 会用它自动 replay, 避免黑屏.
            val savedLastRender = existing.lastRender
            existing.detach()
            val newEngine = PreviewRenderEngine(this, container).apply {
                lastRender = savedLastRender
            }.also { it.attach() }
            renderEngine = newEngine
        }
    }

    /**
     * 【PR-B】应用全屏模式到 Activity 的 window insets controller.
     *
     * - [FullscreenMode.OFF]: 显示系统状态栏 + 导航栏.
     * - [FullscreenMode.WITH_SYSTEM_BARS]: 全屏, 但保留系统状态栏 (用户能在顶部看到时间 / 信号).
     * - [FullscreenMode.WITHOUT_SYSTEM_BARS]: 沉浸式全屏, 隐藏系统栏, 适合看沉浸式 UI.
     *
     * 用户在 Preview 内点击设备套壳 / compose 内容时, 需要 swipe to show system bars.
     * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE 让系统栏在被 swipe 时短暂显示.
     */
    fun applyFullscreenMode(mode: FullscreenMode) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        when (mode) {
            FullscreenMode.OFF -> {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
            FullscreenMode.WITH_SYSTEM_BARS -> {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            FullscreenMode.WITHOUT_SYSTEM_BARS -> {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        LOG.info("Applied fullscreen mode: {}", mode)
    }

    override fun onDestroy() {
        renderEngine?.detach()
        renderEngine = null
        super.onDestroy()
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
 * 主屏 Composable v3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposePreviewScreen(
    activity: ComposePreviewActivity,
    viewModel: ComposePreviewViewModel,
    onClose: () -> Unit,
) {
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val deviceConfig by viewModel.deviceConfig.collectAsStateWithLifecycle()
    val viewport by viewModel.viewport.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val debugEnabled by viewModel.debugEnabled.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()
    val fullscreenMode by viewModel.fullscreenMode.collectAsStateWithLifecycle()
    // 【PR-C】桌面 launcher 状态
    val desktopApps by viewModel.desktopApps.collectAsStateWithLifecycle()
    val foregroundApp by viewModel.foregroundApp.collectAsStateWithLifecycle()
    // 【v3.3】调试模式
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val composableFunctions by viewModel.composableFunctions.collectAsStateWithLifecycle()
    val selectedPreview by viewModel.selectedPreview.collectAsStateWithLifecycle()

    // 【PR-B】全屏模式: 控制手机系统状态栏. LaunchedEffect 在 mode 变化时调用
    // Activity 的 windowInsetsController.
    val activityCtx = LocalContext.current as ComposePreviewActivity
    LaunchedEffect(fullscreenMode) {
        activityCtx.applyFullscreenMode(fullscreenMode)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showResolutionEditor by remember { mutableStateOf(false) }
    var showComposablePicker by remember { mutableStateOf(false) }
    var showLayoutTreeSheet by remember { mutableStateOf(false) }
    var showErrorSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

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
                // 【PR-B】顶栏: 全屏时隐藏. 退出全屏按钮在 main content 区右上角
                // 用 Box 叠层显示 (见下面 MainBox).
                if (!isFullscreen) {
                    // 【v3.5】横竖屏 chip label 派生.
                    val orientationLabel = when (deviceConfig.orientation) {
                        com.itsaky.androidide.compose.preview.ui.DeviceOrientation.PORTRAIT -> "竖屏"
                        com.itsaky.androidide.compose.preview.ui.DeviceOrientation.LANDSCAPE -> "横屏"
                        com.itsaky.androidide.compose.preview.ui.DeviceOrientation.REVERSE_LANDSCAPE -> "反向横屏"
                        com.itsaky.androidide.compose.preview.ui.DeviceOrientation.REVERSE_PORTRAIT -> "倒置竖屏"
                    }
                    PreviewToolbar(
                        state = PreviewToolbarState(
                            deviceName = deviceConfig.profile.displayName,
                            themeLabel = theme.name,
                            zoom = viewport.zoom,
                            showSystemBars = deviceConfig.showStatusBar,
                            deviceSimEnabled = deviceConfig.deviceSimEnabled,
                            fullscreen = false,
                            // 【v3.3】调试模式 — 替换旧的 debugEnabled
                            debugModeEnabled = debugMode.enabled,
                            // 【v3.5】横竖屏 chip
                            orientationLabel = orientationLabel,
                            orientationIsLandscape = deviceConfig.orientation.isLandscape,
                        ),
                        actions = PreviewToolbarActions(
                            onOpenDeviceSheet = { showDeviceSheet = true },
                            onCycleTheme = { viewModel.cycleTheme() },
                            onSetZoom = { viewModel.setZoom(it) },
                            onFitZoom = { viewModel.fitZoom() },
                            onToggleSystemBars = { viewModel.toggleSystemBars() },
                            // 【v3.3】调试模式 toggle
                            onToggleDebugMode = { viewModel.toggleDebugMode() },
                            onClose = onClose,
                            onToggleDeviceSim = { viewModel.toggleDeviceSim() },
                            onToggleFullscreen = { viewModel.toggleFullscreen() },
                            onSetFullscreenMode = { mode -> viewModel.setFullscreenMode(mode) },
                            // 【v3.5】横竖屏
                            onCycleOrientation = { viewModel.cycleOrientationFast() },
                            onSetOrientation = { o -> viewModel.setOrientation(o) },
                        ),
                    )
                    HorizontalDivider()

                    // 【v3.3】调试模式开启时, 在主 toolbar 下方显示 Debug Toolbar
                    if (debugMode.enabled) {
                        DebugToolbar(
                            state = DebugToolbarState(
                                currentFunctionName = selectedPreview,
                                functionCount = composableFunctions.size,
                                analysisMode = debugMode.analysisMode,
                                editMode = debugMode.editMode,
                                showRecompositionHighlight = debugMode.showRecompositionHighlight,
                                showLayoutTree = showLayoutTreeSheet,
                                hiddenNodeCount = debugMode.hiddenNodeIds.size,
                            ),
                            actions = DebugToolbarActions(
                                onOpenFunctionPicker = { showComposablePicker = true },
                                onToggleAnalysisMode = { viewModel.toggleAnalysisMode() },
                                onToggleEditMode = { viewModel.toggleEditMode() },
                                onToggleRecomposition = { viewModel.toggleRecompositionHighlight() },
                                onToggleLayoutTree = { showLayoutTreeSheet = !showLayoutTreeSheet },
                                onClearHiddenNodes = { viewModel.clearHiddenNodes() },
                                // 【v3.3】截图导出 — 通过 Activity 拿 preview view
                                onExportScreenshot = {
                                    val previewView = activityCtx.previewViewForExport()
                                    com.itsaky.androidide.compose.preview.util.ScreenshotExporter
                                        .export(context, previewView) { ok, uri, msg ->
                                            activityCtx.runOnUiThread {
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                                if (!ok) {
                                                    LOG.warn("Screenshot export failed: {}", msg)
                                                }
                                            }
                                        }
                                },
                                onClose = { viewModel.toggleDebugMode() },
                            ),
                        )
                        HorizontalDivider()

                        // 【v3.3】错误 badge — 紧跟 debug toolbar 之后, 仅在调试模式显示
                        if (debugMode.showErrorBadge) {
                            val errorState = previewState
                            val errorMessage = (errorState as? PreviewState.Error)?.message
                            val diagnostics = (errorState as? PreviewState.Error)?.diagnostics.orEmpty()
                            if (errorMessage != null || diagnostics.isNotEmpty()) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    ErrorBadge(
                                        errorMessage = errorMessage,
                                        diagnostics = diagnostics,
                                        onClick = { showErrorSheet = true },
                                    )
                                }
                            }
                        }
                    }
                }

                // 主体 — Box 叠层: 底层是 preview / loading / error, 顶层是退出全屏按钮
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
                            activity = activity,
                            deviceConfig = deviceConfig,
                            viewport = viewport,
                            isFullscreen = isFullscreen,
                            desktopApps = desktopApps,
                            foregroundApp = foregroundApp,
                            modulePath = viewModel.getModulePath(),
                            onLaunchApp = { app -> viewModel.launchDesktopApp(app) },
                            onGoHome = { viewModel.goToHome() },
                            // 【PR-D】传 previewState 让 ReadyPanel 在重新编译
                            // (新 Ready 进入) 时重置 pan, 避免旧 pan 在新 dex 上残留.
                            previewState = s,
                        )
                    }

                    // 【PR-C 修复】退出全屏按钮 — 放在 main content 的右上角 (叠层),
                    // 而不是单独占一栏. 之前 v3.2 把它放在 Column 的另一个 Box 里,
                    // Box fillMaxWidth 没设 height 导致 IconButton align TopEnd 时
                    // 父容器 0 高度, 按钮渲染但位置跑到屏幕外, 点击没反应或
                    // exit 后布局混乱 (preview 不显示).
                    if (isFullscreen) {
                        IconButton(
                            onClick = { viewModel.toggleFullscreen() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FullscreenExit,
                                contentDescription = "退出全屏",
                                tint = Color.White,
                            )
                        }
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

    // 【v3.3】@Composable 函数选择器 (调试模式)
    if (showComposablePicker) {
        ComposableFunctionPicker(
            functions = composableFunctions,
            currentFunctionName = selectedPreview,
            onSelected = { info ->
                // 1) 选新函数
                viewModel.selectComposable(info.name)
                // 2) 触发重新渲染 — Activity 的 previewState.collect 已经监听,
                //    但只取 firstOrNull. selectedPreview 变化时需要主动 render.
                //    这里手动调用 Activity.renderComposable 即可.
                activityCtx.renderSelectedComposable(info.name)
            },
            onDismiss = {
                showComposablePicker = false
            },
        )
    }

    // 【v3.3】布局树底部抽屉
    if (showLayoutTreeSheet) {
        // 【v3.3.1】属性编辑参数 — 从 previewState.Ready 拿 dexFile + className,
        // 从 composableFunctions 找选中函数对应的行号.
        val readyState = previewState as? PreviewState.Ready
        val attrClass = readyState?.className
        val attrMethod = selectedPreview
        val attrLine = composableFunctions
            .firstOrNull { it.name == attrMethod }
            ?.line ?: -1
        val attrs by viewModel.selectedNodeAttributes.collectAsStateWithLifecycle()
        val editResult by viewModel.lastAttributeEditResult.collectAsStateWithLifecycle()
        // 【v3.4】属性加载状态 — 给底部抽屉头部 Refresh 按钮显示加载动画.
        val isRefreshingAttr by viewModel.isRefreshingAttributes.collectAsStateWithLifecycle()
        // 选中节点 / 函数变化时重新 load attributes
        LaunchedEffect(attrClass, attrMethod) {
            viewModel.loadAttributesForSelectedNode(readyState?.dexFile, attrClass, attrMethod)
        }
        LayoutTreeBottomSheet(
            debugMode = debugMode,
            layoutSnapshot = viewModel.layoutSnapshot.value,
            attributeClassName = attrClass,
            attributeMethodName = attrMethod,
            attributeCallLine = attrLine,
            attributes = attrs,
            lastEditResult = editResult,
            onSelectNode = { nodeId -> viewModel.selectLayoutNode(nodeId) },
            onToggleHidden = { nodeId -> viewModel.toggleNodeHidden(nodeId) },
            onClearHidden = { viewModel.clearHiddenNodes() },
            onEditAttribute = { paramName, newValue ->
                val projectRoot = viewModel.getModulePath()
                    .takeIf { it.isNotEmpty() }
                    ?.let { java.io.File(it) }
                if (attrClass == null) return@LayoutTreeBottomSheet
                viewModel.editAttribute(
                    projectRoot = projectRoot,
                    className = attrClass,
                    callLine = attrLine,
                    parameterName = paramName,
                    newValue = newValue,
                ) {
                    // 触发 build — taskName 形如 "assembleDebug" / "assembleRelease",
                    // 跟 triggerBuild(context, viewModel, modulePath, variantName) 一致.
                    // ComposeAttributeEditor 默认返回 "assembleDebug", variantName 用 default.
                    triggerBuild(
                        context = context,
                        viewModel = viewModel,
                        modulePath = viewModel.getModulePath(),
                        variantName = "debug",
                    )
                }
            },
            onClearEditResult = { viewModel.clearAttributeEditResult() },
            // 【v3.4】用户主动重新解析 dex — 用于源文件改了但 dex 未重建的情况.
            onRefreshAttributes = { viewModel.refreshAttributes() },
            isRefreshingAttributes = isRefreshingAttr,
            onDismiss = { showLayoutTreeSheet = false },
        )
    }

    // 【v3.3】错误详情 Sheet
    if (showErrorSheet) {
        val errState = previewState as? PreviewState.Error
        ErrorDetailSheet(
            errorMessage = errState?.message,
            diagnostics = errState?.diagnostics.orEmpty(),
            onDismiss = { showErrorSheet = false },
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
    activity: ComposePreviewActivity,
    deviceConfig: DeviceConfig,
    viewport: ViewportState,
    isFullscreen: Boolean,
    desktopApps: List<DesktopApp>,
    foregroundApp: DesktopApp?,
    modulePath: String,
    onLaunchApp: (DesktopApp) -> Unit,
    onGoHome: () -> Unit,
    previewState: PreviewState,
) {
    // 【v3.3.1】ComposeView 引用 — LayoutInspector 通过这个反射读 LayoutNode 树.
    // 由 PreviewContainer 通过 onComposeViewReady 回调设置.
    var composeView by remember { mutableStateOf<android.view.View?>(null) }

    // 【v3.3.1】Activity-scoped ViewModel / debugMode (Composable 函数里取).
    val viewModel = activity.viewModel
    val debugModeState by viewModel.debugMode.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    // 设备框 + 内容
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (deviceConfig.deviceSimEnabled) 16.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {

        // 【v3.2】用 key() 强制 deviceSimEnabled / profile 变化时整体重组.
        // 【PR-D】previewState 也加入 key, 重新编译时彻底重置 (LaunchedEffect 已经
        // resetPan, 但 key() 还能让 graphicsLayer 缓存的 layout 状态一起刷新).
        androidx.compose.runtime.key(
            deviceConfig.deviceSimEnabled,
            deviceConfig.profile.id,
            previewState,
        ) {
            // 把 zoom 后的内容 + pan 手势都包在 BoxWithConstraints 里, 这样能拿到
            // 父容器尺寸, 计算 maxPan 范围.
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val parentWidthDp = maxWidth.value
                val parentHeightDp = maxHeight.value
                // zoom > 1 时, 内容宽度 = parent * zoom, 多出来的部分 = parent * (zoom - 1)
                // 中心为 TransformOrigin.Center, 所以左右各溢出 (parent * (zoom - 1) / 2).
                val zoomScale = viewport.zoom
                val canPan = zoomScale != 1f
                val overflowXDp = (parentWidthDp * (zoomScale - 1f) / 2f).coerceAtLeast(0f)
                val overflowYDp = (parentHeightDp * (zoomScale - 1f) / 2f).coerceAtLeast(0f)

                val translationXDp = viewport.offsetXdp
                val translationYDp = viewport.offsetYdp

                // 把 dp 偏移转成 px 喂给 graphicsLayer (translationX/Y 用 px).
                val translationXPx = with(density) { translationXDp.dp.toPx() }
                val translationYPx = with(density) { translationYDp.dp.toPx() }

                val graphicsModifier = Modifier
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = translationXPx,
                        translationY = translationYPx,
                        transformOrigin = TransformOrigin.Center,
                    )
                    // 【PR-D】触摸平移手势. 只在 canPan (zoom != 1.0) 时启用, 避免
                    // 误吞点击事件 (例如设备模式下用户想点物理键/状态栏).
                    .then(
                        if (canPan) {
                            Modifier.pointerInput(zoomScale, parentWidthDp, parentHeightDp) {
                                // PointerInputScope 继承自 Density, 这里拿 px-per-dp
                                // 把 dragAmount (px) 转成 dp.
                                val pxPerDp = density.density
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // dragAmount 是 px, 还原成 dp.
                                    // 手指往右拖 → 内容跟随手指往右移 → offsetXdp
                                    // 增大 → translationX 增大 (graphicsLayer 中
                                    // 正值 = 向右). 所以 deltaXDp 保持原符号.
                                    val deltaXDp = dragAmount.x / pxPerDp
                                    val deltaYDp = dragAmount.y / pxPerDp
                                    viewModel.panBy(
                                        deltaXDp = deltaXDp,
                                        deltaYDp = deltaYDp,
                                        maxXDp = overflowXDp,
                                        maxYDp = overflowYDp,
                                    )
                                }
                            }
                        } else {
                            Modifier
                        }
                    )

                if (deviceConfig.deviceSimEnabled) {
                    // 设备模式: 整个 device frame 一起缩放 + 拖动
                    // 【v3.5】把 deviceConfig.orientation 应用到 profile.orientation, 让
                    // DeviceFrame 的 effective* 系列 (effectiveWidthDp / effectiveHeightDp /
                    // effectiveCutout / effectivePhysicalKeys / effectiveBezels) 正确旋转.
                    val orientedProfile = deviceConfig.profile.copy(
                        orientation = deviceConfig.orientation,
                    )
                    Box(modifier = graphicsModifier) {
                        DeviceFrame(
                            profile = orientedProfile,
                            systemBarsTheme = deviceConfig.systemBarsTheme,
                            showStatusBar = deviceConfig.showStatusBar,
                            showNavigationBar = deviceConfig.showNavigationBar,
                            showCutout = deviceConfig.showCutout,
                            showChassis = deviceConfig.showChassis,
                            useGestureNav = deviceConfig.useGestureNav,
                            desktopApps = desktopApps,
                            foregroundApp = foregroundApp,
                            modulePath = modulePath,
                            onLaunchApp = onLaunchApp,
                            onGoHome = onGoHome,
                        ) {
                            PreviewContainer(
                                activity = activity,
                                onComposeViewReady = { v -> composeView = v },
                            )
                        }
                    }
                } else {
                    // 无设备模式: 缩放 compose UI 本身, 不缩放外层
                    Box(modifier = graphicsModifier) {
                        PreviewContainer(
                            activity = activity,
                            onComposeViewReady = { v -> composeView = v },
                        )
                    }
                }

                // 【PR-D 提示】缩放 != 1.0 时显示一个小提示 "拖动平移"
                if (canPan) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        androidx.compose.material3.Text(
                            text = "${(zoomScale * 100).toInt()}% · 拖动平移",
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        // 【v3.3.1】Layout Inspector Overlay — 在分析 / 编辑模式下叠加在 preview 内容之上.
        // 它本身用 Canvas 画虚线 + 节点类型角标, 同时把 layoutSnapshot 传给 LayoutTreeBottomSheet.
        if (debugModeState.isInspectorActive && composeView != null) {
            val snapshot = viewModel.layoutSnapshot.collectAsStateWithLifecycle().value
            LayoutInspectorOverlay(
                snapshot = snapshot,
                selectedNodeId = debugModeState.selectedNodeId,
                isInspectorActive = true,
                showRecomposition = debugModeState.showRecompositionHighlight,
                onNodeClick = { node: com.itsaky.androidide.compose.preview.data.model.LayoutNodeSnapshot ->
                    viewModel.selectLayoutNode(node.id)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 【v3.3.1】布局快照 capture — 仅在分析 / 编辑模式下采集, 避免 layout 变化频繁时反射开销.
        // 500ms debounce 足够用户感知 + 不卡顿.
        if (debugModeState.isInspectorActive) {
            LaunchedEffect(composeView, previewState, deviceConfig.profile.id, deviceConfig.deviceSimEnabled) {
                if (composeView == null) return@LaunchedEffect
                kotlinx.coroutines.delay(500)
                viewModel.captureLayoutSnapshot(composeView, force = true)
            }
        }
    }
}

@Composable
private fun PreviewContainer(
    activity: ComposePreviewActivity,
    onComposeViewReady: (android.view.View) -> Unit = {},
) {
    // 容器 Box, 通过 AndroidView 嵌入一个 FrameLayout, 让 PreviewRenderEngine 把
    // 自己的 ComposeView addView 进去. 显式使用 MATCH_PARENT layoutParams 避免
    // SubcomposeLayout 嵌套测量把 ComposeView 的尺寸压成 0 (这是 v2.1 的根因).
    AndroidView(
        factory = { ctx ->
            android.widget.FrameLayout(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // 通知 Activity 创建引擎, 把这个容器给引擎.
                // activity 由 ComposePreviewScreen 显式传入, 不会因重组失效.
                activity.attachPreviewContainer(this)
            }
        },
        // 【v3.3.1】layout 变化时通知 layout inspector 重新 capture.
        // 用 update 回调 + onGloballyPositioned 拿真实 on-screen view 引用.
        update = { frameLayout ->
            // 拿 RenderEngine 注入的 ComposeView
            val composeView = (0 until frameLayout.childCount)
                .map { frameLayout.getChildAt(it) }
                .firstOrNull { it is androidx.compose.ui.platform.ComposeView }
            if (composeView != null) {
                onComposeViewReady(composeView)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
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

    // Tooling server 必须在项目编辑器打开时由 ProjectHandlerActivity 启动
    // (buildService 不在 compose-preview 模块里自己起 server, 避免与编辑器
    // 端的 init 流程争抢状态). 未启动时直接告诉用户先去打开项目, 而不是
    // 让 executeTasks 抛 ToolingServerNotStartedException 炸成未捕获异常。
    // 这跟 QuickRunWithCancellationAction.onModuleSelected 的兜底一致。
    if (!buildService.isToolingServerStarted()) {
        LOG.warn("Tooling server has not been started; ask the user to open the project in the editor first.")
        viewModel.setBuildFailed()
        (context as? android.app.Activity)?.runOnUiThread {
            android.widget.Toast.makeText(
                context,
                "请先在编辑器中打开该项目并完成 Gradle 同步, 再使用 Compose 预览。",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
        return
    }

    // 完全对齐 QuickRunWithCancellationAction.quickRun: 直接向 buildService
    // 发送 gradle assemble 任务. 在 IO 线程阻塞 .get() 等待构建完成, gradle
    // 服务端会把 dex 写到项目 build 目录, 然后 ProjectContextSource 重新解析
    // 时就能拿到新的 projectDexFiles. 构建完成后 dex 刷新, Compose Preview
    // 通过 ComposeClassLoader 加载最新 Composable 即可.
    val capitalizedVariant = variantName.replaceFirstChar { it.uppercaseChar() }
    val task = if (modulePath.isNotEmpty()) {
        "$modulePath:assemble$capitalizedVariant"
    } else {
        "assemble$capitalizedVariant"
    }

    viewModel.setBuildingState()

    // lifecycleScope 是 androidx.activity.ComponentActivity / AppCompatActivity
    // 提供的扩展属性, 不能 cast 到 android.app.Activity 拿不到. 这里 cast 到
    // AppCompatActivity (ComposePreviewActivity 的实际基类) 才有 lifecycleScope.
    val activity = context as? androidx.appcompat.app.AppCompatActivity ?: return
    activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val result = runCatching {
            buildService.executeTasks(task).get(15, java.util.concurrent.TimeUnit.MINUTES)
        }
        activity.runOnUiThread {
            val failure = result.exceptionOrNull()
            if (failure != null || result.getOrNull()?.isSuccessful != true) {
                LOG.error("Gradle assemble task failed: {}", task, failure)
                viewModel.setBuildFailed()
            } else {
                LOG.info("Gradle assemble task succeeded: {}", task)
                viewModel.refreshAfterBuild(activity)
            }
        }
    }
}

private val LOG = LoggerFactory.getLogger("ComposePreviewActivity")
