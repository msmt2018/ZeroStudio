/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.editor.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import com.itsaky.androidide.fragments.editor.components.FrostedGlass
import com.itsaky.androidide.fragments.editor.components.FrostedIconButton
import com.itsaky.androidide.fragments.editor.components.FrostedText
import com.itsaky.androidide.fragments.editor.components.FrostedToggleIconButton
import com.itsaky.androidide.ui.compose.ProvideDarkMode
import com.zerostudio.webpreview.backend.BackendRuntime
import com.zerostudio.webpreview.backend.RuntimeSession
import com.zerostudio.webpreview.backend.TermuxBackendRuntime
import com.zerostudio.webpreview.device.DevicePresets
import com.zerostudio.webpreview.device.DeviceProfile
import com.zerostudio.webpreview.devtools.DevToolsBridge
import com.zerostudio.webpreview.devtools.DevToolsEndpoint
import com.zerostudio.webpreview.engine.WebContent
import com.zerostudio.webpreview.engine.WebPreviewEngine
import com.zerostudio.webpreview.engine.WebViewState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Web 预览 fragment —— 基于 androidx.webkit + Compose 磨砂玻璃控件 + Chrome DevTools。
 *
 * ## 预览场景
 * - 静态 HTML/CSS/JS (本地文件)
 * - 远程 URL (用户在地址栏输入)
 * - 本地 dev server (Termux 启动 node/python/php 等运行时)
 *
 * ## 功能
 * - 顶部磨砂工具栏: 后退/前进/刷新/地址栏/设备切换/DevTools 切换/更多菜单
 * - 设备切换: UA + viewport 真实重渲染 (11 档预置设备)
 * - Chrome DevTools: 三段式桥接 (CDP socket → LocalSocket forwarder → DevTools 前端 WebView)
 *   显示完整 Chrome DevTools UI (Elements / Console / Sources / Network)
 * - 后端控制栏 (底部可隐藏): 启动/停止 Termux 中的后端服务, 自动加载本地 URL
 * - 主题暗色适配 (WebSettingsCompat.setForceDark + LocalDarkMode)
 *
 * ## 主题感知
 * 所有 UI 控件全部用磨砂玻璃效果 (FrostedGlass), 图标颜色随主题自适应。
 *
 * @author ZeroStudio
 */
class WebPreviewFragment : Fragment() {

    companion object {
        const val TAG = "WebPreviewFragment"
        const val TAB_TITLE = "Web Preview"

        /**
         * 支持的文件扩展名 (小写, 不含 `.`)。
         * 主要入口是 HTML/HTM; 其他 web 场景 (Vue/React 构建产物 / dev server / 后端服务)
         * 通过工具栏地址栏输入或后端控制栏启动进入。
         */
        val SUPPORTED_EXTENSIONS: Set<String> = setOf("html", "htm")

        @JvmStatic
        fun newInstance(filePath: String): WebPreviewFragment {
            return WebPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(EditorFragmentTabManager.ARG_FILE_PATH, filePath)
                }
            }
        }
    }

    private var filePath: String? = null
    private var webViewState: WebViewState? = null
    private var devToolsBridge: DevToolsBridge? = null
    private var backendRuntime: BackendRuntime? = null
    private var backendSession: RuntimeSession? = null
    private val LOG = LoggerFactory.getLogger(TAG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            filePath = args.getString(EditorFragmentTabManager.ARG_FILE_PATH)
        }
        LOG.info("WebPreviewFragment.onCreate: filePath={}", filePath)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val path = filePath
        val ctx = requireContext()

        // 创建 WebViewState, 加载初始内容 (本地 HTML 文件或空白)
        val initialContent = if (path != null) WebContent.File(File(path)) else WebContent.Url("about:blank")
        val state = WebViewState(initialContent).also { webViewState = it }

        // 创建 DevToolsBridge 与 BackendRuntime
        val bridge = DevToolsBridge(ctx).also { devToolsBridge = it }
        val runtime = TermuxBackendRuntime(ctx).also { backendRuntime = it }

        return ComposeView(ctx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    ProvideDarkMode {
                        WebPreviewScreen(
                            state = state,
                            filePath = path,
                            devToolsBridge = bridge,
                            backendRuntime = runtime,
                            onBackendSessionChange = { session -> backendSession = session },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        devToolsBridge?.stop()
        backendSession?.stop()
        backendSession = null
        devToolsBridge = null
        backendRuntime = null
        webViewState = null
    }
}

// ════════════════════════════════════════════════════════════════════
// 顶层 Screen
// ════════════════════════════════════════════════════════════════════

@Composable
private fun WebPreviewScreen(
    state: WebViewState,
    filePath: String?,
    devToolsBridge: DevToolsBridge,
    backendRuntime: BackendRuntime,
    onBackendSessionChange: (RuntimeSession?) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var showDevicePicker by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var devToolsPanelVisible by remember { mutableStateOf(false) }
    var devToolsFrontendUrl by remember { mutableStateOf<String?>(null) }
    var devToolsStatus by remember { mutableStateOf<String?>(null) }
    var devToolsLoading by remember { mutableStateOf(false) }

    var backendControlsVisible by remember { mutableStateOf(false) }
    var backendRunning by remember { mutableStateOf(false) }
    var backendStatus by remember { mutableStateOf<String?>(null) }
    var backendCommand by remember { mutableStateOf("node server.js") }
    var backendPort by remember { mutableStateOf("3000") }

    var urlInput by remember { mutableStateOf(state.currentUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 同步 state.currentUrl → urlInput
    LaunchedEffect(state.currentUrl) {
        if (urlInput != state.currentUrl) urlInput = state.currentUrl
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 1. 顶部磨砂工具栏
            WebTopBar(
                state = state,
                urlInput = urlInput,
                onUrlInputChange = { urlInput = it },
                onUrlSubmit = {
                    val trimmed = urlInput.trim()
                    if (trimmed.isNotEmpty()) {
                        state.content = WebContent.Url(trimmed)
                    }
                },
                onBack = { webViewRef?.goBack() },
                onForward = { webViewRef?.goForward() },
                onRefresh = { webViewRef?.reload() },
                onDevicePickerClick = { showDevicePicker = !showDevicePicker },
                onDevToolsToggle = {
                    devToolsPanelVisible = !devToolsPanelVisible
                    if (devToolsPanelVisible) {
                        // 启用调试 + 异步启动 DevToolsBridge
                        state.devToolsEnabled = true
                        devToolsLoading = true
                        devToolsStatus = "正在启动 DevTools 桥接..."
                        devToolsFrontendUrl = null
                        devToolsBridge.startAsync { endpoint ->
                            devToolsLoading = false
                            when (endpoint) {
                                is DevToolsEndpoint.Ready -> {
                                    devToolsFrontendUrl = endpoint.frontendUrl
                                    devToolsStatus = null
                                }
                                is DevToolsEndpoint.Failed -> {
                                    devToolsFrontendUrl = null
                                    devToolsStatus = endpoint.reason +
                                        "\n\n降级方案: 用电脑 Chrome 访问 chrome://inspect 远程调试本机 WebView。"
                                }
                                DevToolsEndpoint.Idle -> {}
                            }
                        }
                    } else {
                        devToolsBridge.stop()
                        devToolsFrontendUrl = null
                        devToolsStatus = null
                    }
                },
                devToolsActive = devToolsPanelVisible,
                onMoreMenuClick = { showMoreMenu = !showMoreMenu },
            )

            // 2. WebView 渲染区 (占满中间剩余空间)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                WebPreviewEngine(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onUrlChange = { /* state.currentUrl 已由 engine 更新 */ },
                    onWebViewCreated = { webViewRef = it },
                )

                // 加载进度条 (顶部)
                if (state.loading && state.progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }

                // 错误提示 (底部浮层)
                state.lastError?.let { err ->
                    FrostedGlass(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        cornerRadius = 12.dp,
                    ) {
                        FrostedText(text = "⚠ $err", fontSize = 11.sp)
                    }
                }
            }

            // 3. 底部磨砂后端控制栏 (可隐藏)
            AnimatedVisibility(
                visible = backendControlsVisible,
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                BackendControlBar(
                    command = backendCommand,
                    onCommandChange = { backendCommand = it },
                    port = backendPort,
                    onPortChange = { backendPort = it },
                    running = backendRunning,
                    status = backendStatus,
                    onStart = {
                        scope.launch {
                            val port = backendPort.toIntOrNull() ?: 3000
                            val workDir = filePath?.let { File(it).parentFile }
                                ?: File(android.os.Environment.getExternalStorageDirectory(), "ZeroStudio")
                            try {
                                backendRunning = true
                                backendStatus = "启动中..."
                                val session = withContext(Dispatchers.IO) {
                                    backendRuntime.startService(workDir, backendCommand, port)
                                }
                                onBackendSessionChange(session)
                                val url = session.toLocalUrl()
                                backendStatus = "Running → $url"
                                // 自动加载本地 URL
                                state.content = WebContent.Url(url)
                            } catch (e: Exception) {
                                backendRunning = false
                                backendStatus = "启动失败: ${e.message}"
                            }
                        }
                    },
                    onStop = {
                        backendSession?.stop()
                        onBackendSessionChange(null)
                        backendRunning = false
                        backendStatus = "已停止"
                    },
                )
            }
        }

        // 4. 设备选择浮层 (右上)
        if (showDevicePicker) {
            DevicePickerPanel(
                currentDevice = state.deviceProfile,
                onSelect = { profile ->
                    state.deviceProfile = profile
                    showDevicePicker = false
                    // 重新加载以应用新 UA
                    webViewRef?.reload()
                },
                onDismiss = { showDevicePicker = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 12.dp),
            )
        }

        // 5. 更多菜单
        if (showMoreMenu) {
            MoreMenu(
                darkMode = state.darkMode,
                backendControlsVisible = backendControlsVisible,
                onToggleDarkMode = {
                    state.darkMode = !state.darkMode
                    showMoreMenu = false
                },
                onToggleBackendControls = {
                    backendControlsVisible = !backendControlsVisible
                    showMoreMenu = false
                },
                onClearConsole = {
                    state.clearConsole()
                    showMoreMenu = false
                },
                onClearNetwork = {
                    state.clearNetwork()
                    showMoreMenu = false
                },
                onDismiss = { showMoreMenu = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 12.dp),
            )
        }

        // 6. DevTools 面板 (从底部滑出, 半屏高度)
        AnimatedVisibility(
            visible = devToolsPanelVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            DevToolsPanel(
                frontendUrl = devToolsFrontendUrl,
                loading = devToolsLoading,
                statusMessage = devToolsStatus,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// 顶部磨砂工具栏
// ════════════════════════════════════════════════════════════════════

@Composable
private fun WebTopBar(
    state: WebViewState,
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    onUrlSubmit: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onDevicePickerClick: () -> Unit,
    onDevToolsToggle: () -> Unit,
    devToolsActive: Boolean,
    onMoreMenuClick: () -> Unit,
) {
    FrostedGlass(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 0.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrostedIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                enabled = state.canGoBack,
                size = 36.dp,
            )
            FrostedIconButton(
                icon = Icons.Filled.ArrowForward,
                contentDescription = "Forward",
                onClick = onForward,
                enabled = state.canGoForward,
                size = 36.dp,
            )
            FrostedIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = "Refresh",
                onClick = onRefresh,
                size = 36.dp,
            )

            // 地址栏 (磨砂玻璃包裹的 OutlinedTextField)
            OutlinedTextField(
                value = urlInput,
                onValueChange = onUrlInputChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onUrlSubmit() }),
                placeholder = { Text("输入 URL 或搜索", fontSize = 12.sp) },
            )

            // 设备切换按钮
            FrostedIconButton(
                icon = Icons.Filled.Devices,
                contentDescription = "Device",
                onClick = onDevicePickerClick,
                size = 36.dp,
            )
            // DevTools 切换按钮 (带激活态)
            FrostedToggleIconButton(
                icon = Icons.Filled.BugReport,
                contentDescription = "DevTools",
                active = devToolsActive,
                onClick = onDevToolsToggle,
                size = 36.dp,
            )
            // 更多菜单
            FrostedIconButton(
                icon = Icons.Filled.MoreVert,
                contentDescription = "More",
                onClick = onMoreMenuClick,
                size = 36.dp,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// 设备选择面板
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DevicePickerPanel(
    currentDevice: DeviceProfile,
    onSelect: (DeviceProfile) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostedGlass(
        modifier = modifier.width(280.dp),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(8.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrostedText(text = "选择设备", fontSize = 13.sp)
                FrostedIconButton(
                    icon = Icons.Filled.Clear,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    size = 28.dp,
                )
            }
            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(DevicePresets.ALL) { profile ->
                    val selected = profile.name == currentDevice.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(profile) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FrostedText(
                            text = if (selected) "● ${profile.name}" else "  ${profile.name}",
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// 更多菜单
// ════════════════════════════════════════════════════════════════════

@Composable
private fun MoreMenu(
    darkMode: Boolean,
    backendControlsVisible: Boolean,
    onToggleDarkMode: () -> Unit,
    onToggleBackendControls: () -> Unit,
    onClearConsole: () -> Unit,
    onClearNetwork: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostedGlass(
        modifier = modifier.width(220.dp),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(4.dp),
    ) {
        Column {
            MoreMenuItem(text = if (darkMode) "切换到亮色模式" else "切换到暗色模式", onClick = onToggleDarkMode)
            MoreMenuItem(
                text = if (backendControlsVisible) "隐藏后端控制栏" else "显示后端控制栏",
                onClick = onToggleBackendControls,
            )
            MoreMenuItem(text = "清空 Console", onClick = onClearConsole)
            MoreMenuItem(text = "清空网络请求记录", onClick = onClearNetwork)
            MoreMenuItem(text = "关闭菜单", onClick = onDismiss)
        }
    }
}

@Composable
private fun MoreMenuItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        FrostedText(text = text, fontSize = 12.sp)
    }
}

// ════════════════════════════════════════════════════════════════════
// 底部磨砂后端控制栏
// ════════════════════════════════════════════════════════════════════

@Composable
private fun BackendControlBar(
    command: String,
    onCommandChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    running: Boolean,
    status: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    FrostedGlass(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 0.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column {
            // 命令行输入 + 端口 + 启停按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = command,
                    onValueChange = onCommandChange,
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = { Text("node server.js", fontSize = 11.sp) },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { s -> if (s.all { it.isDigit() } && s.length <= 5) onPortChange(s) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("端口", fontSize = 11.sp) },
                )
                Spacer(Modifier.width(8.dp))
                if (running) {
                    FrostedIconButton(
                        icon = Icons.Filled.Stop,
                        contentDescription = "Stop",
                        onClick = onStop,
                        size = 40.dp,
                    )
                } else {
                    FrostedIconButton(
                        icon = Icons.Filled.PlayArrow,
                        contentDescription = "Start",
                        onClick = onStart,
                        size = 40.dp,
                    )
                }
            }
            // 状态显示
            status?.let {
                Spacer(Modifier.height(4.dp))
                FrostedText(text = it, fontSize = 10.sp)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// DevTools 面板 (从底部滑出, 半屏高度)
// ════════════════════════════════════════════════════════════════════

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DevToolsPanel(
    frontendUrl: String?,
    loading: Boolean,
    statusMessage: String?,
    modifier: Modifier = Modifier,
) {
    FrostedGlass(
        modifier = modifier,
        cornerRadius = 0.dp,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FrostedText(text = "正在启动 Chrome DevTools...", fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                    }
                }
                frontendUrl != null -> {
                    // DevTools 前端 WebView (完整 Chrome DevTools UI)
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true
                                settings.allowContentAccess = true
                                @Suppress("DEPRECATION")
                                settings.mixedContentMode =
                                    android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                // DevTools 前端自身也需要开调试 (调试嵌套)
                                WebView.setWebContentsDebuggingEnabled(true)
                                loadUrl(frontendUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                statusMessage != null -> {
                    // 失败提示
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FrostedText(
                            text = "DevTools 启动失败",
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        FrostedText(text = statusMessage, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
