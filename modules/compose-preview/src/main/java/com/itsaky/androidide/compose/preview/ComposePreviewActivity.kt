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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.itsaky.androidide.compose.preview.runtime.PreviewRenderEngine
import com.itsaky.androidide.compose.preview.ui.DeviceFrame
import com.itsaky.androidide.compose.preview.ui.DeviceProfileSheet
import com.itsaky.androidide.compose.preview.ui.PreviewToolbar
import com.itsaky.androidide.compose.preview.ui.PreviewToolbarActions
import com.itsaky.androidide.compose.preview.ui.PreviewToolbarState
import com.itsaky.androidide.compose.preview.ui.ResolutionEditor
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

    private val viewModel: ComposePreviewViewModel by viewModels()

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

        // 订阅 previewState, 切到 Ready 时调 engine.render(...)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.previewState.collect { state ->
                    if (state is PreviewState.Ready) {
                            renderEngine?.let { engine ->
                                val config = state.previewConfigs.firstOrNull() ?: return@collect
                                engine.render(
                                    previewDex = state.dexFile,
                                    projectDex = state.projectDexFiles,
                                    className = state.className,
                                    functionName = config.functionName,
                                )
                            }
                        }
                }
            }
        }
    }

    private var renderEngine: PreviewRenderEngine? = null

    /**
     * 由 ComposePreviewScreen 内 AndroidView 调用, 注入预览容器.
     * 引擎 attach 到此 container 后, container 会显示 Compose 渲染.
     *
     * 【v3.2】如果上次 attach 的 container 与当前不同 (切 deviceSim / 切 profile
     * 触发 AndroidView 重建时), 强制 detach 旧引擎 + 新建引擎. 否则旧 ComposeView
     * 仍然 addView 在旧 FrameLayout 上, 用户看到的是"切回后黑屏".
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
            existing.detach()
            renderEngine = PreviewRenderEngine(this, container).also { it.attach() }
        }
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showResolutionEditor by remember { mutableStateOf(false) }
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
                // 顶栏
                PreviewToolbar(
                    state = PreviewToolbarState(
                        deviceName = deviceConfig.profile.displayName,
                        themeLabel = theme.name,
                        zoom = viewport.zoom,
                        showSystemBars = deviceConfig.showStatusBar,
                        debugEnabled = debugEnabled,
                        deviceSimEnabled = deviceConfig.deviceSimEnabled,
                    ),
                    actions = PreviewToolbarActions(
                        onOpenDeviceSheet = { showDeviceSheet = true },
                        onCycleTheme = { viewModel.cycleTheme() },
                        onSetZoom = { viewModel.setZoom(it) },
                        onFitZoom = { viewModel.fitZoom() },
                        onToggleSystemBars = { viewModel.toggleSystemBars() },
                        onToggleDebug = { viewModel.toggleDebug() },
                        onClose = onClose,
                        onToggleDeviceSim = { viewModel.toggleDeviceSim() },
                        onToggleFullscreen = { viewModel.toggleFullscreen() },
                    ),
                )

                HorizontalDivider()

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
                            activity = activity,
                            deviceConfig = deviceConfig,
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
) {
    // 设备框 + 内容
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (deviceConfig.deviceSimEnabled) 16.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 【v3.2】用 key() 强制 deviceSimEnabled / profile 变化时整体重组.
        // 原因: AndroidView 的 factory 缓存 (默认按位置 key), 切 deviceSim 时
        // 如果不重置, 旧 PreviewRenderEngine 引用的 ComposeView 还在新 container
        // 之外, 切回时显示黑屏. key 强制整个子树重组, 触发 AndroidView 重建.
        androidx.compose.runtime.key(
            deviceConfig.deviceSimEnabled,
            deviceConfig.profile.id
        ) {
            if (deviceConfig.deviceSimEnabled) {
                DeviceFrame(
                    profile = deviceConfig.profile,
                    systemBarsTheme = deviceConfig.systemBarsTheme,
                    showStatusBar = deviceConfig.showStatusBar,
                    showNavigationBar = deviceConfig.showNavigationBar,
                    showCutout = deviceConfig.showCutout,
                    showChassis = deviceConfig.showChassis,
                    useGestureNav = deviceConfig.useGestureNav,
                ) {
                    PreviewContainer(activity)
                }
            } else {
                PreviewContainer(activity)
            }
        }
    }
}

@Composable
private fun PreviewContainer(activity: ComposePreviewActivity) {
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
