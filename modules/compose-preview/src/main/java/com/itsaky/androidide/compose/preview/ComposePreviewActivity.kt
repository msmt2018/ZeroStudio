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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
                    ),
                    actions = PreviewToolbarActions(
                        onOpenDeviceSheet = { showDeviceSheet = true },
                        onCycleTheme = { viewModel.cycleTheme() },
                        onSetZoom = { viewModel.setZoom(it) },
                        onFitZoom = { viewModel.fitZoom() },
                        onToggleSystemBars = { viewModel.toggleSystemBars() },
                        onToggleDebug = { viewModel.toggleDebug() },
                        onClose = onClose,
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
                            previewState = s,
                            deviceConfig = deviceConfig,
                            viewport = viewport,
                            onBuildFailed = {
                                viewModel.setBuildFailed()
                            }
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
    projectDexFiles: List<java.io.File>,
    previewConfigs: List<PreviewConfig>,
    classLoader: com.itsaky.androidide.compose.preview.runtime.ComposeClassLoader,
) {
    androidx.compose.runtime.LaunchedEffect(dexFile, className) {
        classLoader.setProjectDexFiles(projectDexFiles)
        // v3 移除 runtime dex: assets 已删除, Compose 运行时类从 IDE 进程的
        // PathClassLoader 解析, 无需额外注入.
        classLoader.setRuntimeDex(null)
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

    val activity = context as? android.app.Activity ?: return
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
