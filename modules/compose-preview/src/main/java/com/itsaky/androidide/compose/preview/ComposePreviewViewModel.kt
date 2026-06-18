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
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsaky.androidide.compose.preview.compiler.CompileDiagnostic
import com.itsaky.androidide.compose.preview.data.device.DesktopApp
import com.itsaky.androidide.compose.preview.data.device.DeviceCatalog
import com.itsaky.androidide.compose.preview.data.repository.CompilationException
import com.itsaky.androidide.compose.preview.data.repository.ComposePreviewRepository
import com.itsaky.androidide.compose.preview.data.repository.ComposePreviewRepositoryImpl
import com.itsaky.androidide.compose.preview.data.repository.InitializationResult
import com.itsaky.androidide.compose.preview.data.source.ProjectContextSource
import com.itsaky.androidide.compose.preview.domain.PreviewSourceParser
import com.itsaky.androidide.compose.preview.domain.model.ParsedPreviewSource
import com.itsaky.androidide.compose.preview.ui.DeviceProfile
import com.itsaky.androidide.compose.preview.ui.SystemBarsTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 预览 UI 状态 (v2 + v2.1).
 *
 * 状态机:
 * - [Idle]          初始 / 完成
 * - [Initializing]  ViewModel 初始化
 * - [Compiling]     正在编译
 * - [Empty]         文件无 @Preview
 * - [Building]      触发 gradle build
 * - [Ready]         编译完成, 可渲染
 * - [Error]         失败 (含诊断信息)
 * - [NeedsBuild]    需要先构建项目
 */
@Immutable
sealed class PreviewState {
    data object Idle : PreviewState()
    data object Initializing : PreviewState()
    data object Compiling : PreviewState()
    data object Empty : PreviewState()
    data object Building : PreviewState()

    data class Ready(
        val dexFile: File,
        val className: String,
        val previewConfigs: List<PreviewConfig>,
        val projectDexFiles: List<File> = emptyList(),
        // v2.1 增字段
        val deviceConfig: DeviceConfig = DeviceConfig(),
        val viewport: ViewportState = ViewportState(),
        val theme: PreviewTheme = PreviewTheme.Light,
        val debugEnabled: Boolean = false,
        val renderCount: Int = 0,
    ) : PreviewState()

    data class Error(
        val message: String,
        val diagnostics: List<CompileDiagnostic> = emptyList(),
        val cause: Throwable? = null,
    ) : PreviewState()

    data class NeedsBuild(val modulePath: String, val variantName: String = "debug") : PreviewState()
}

enum class DisplayMode { ALL, SINGLE }

/**
 * 单个 Composable 预览配置.
 */
@Immutable
data class PreviewConfig(
    val functionName: String,
    val heightDp: Int? = null,
    val widthDp: Int? = null,
    // v2.1 增字段
    val fontScale: Float? = null,
    val isLightDark: Boolean = false,
    val parameterProviderName: String? = null,
)

/**
 * 设备 + 系统栏 + 边框 配置 v2.1.
 */
@Immutable
data class DeviceConfig(
    val profile: DeviceProfile = DeviceCatalog.DEFAULT,
    val systemBarsTheme: SystemBarsTheme = SystemBarsTheme.AUTO,
    val showStatusBar: Boolean = true,
    val showNavigationBar: Boolean = true,
    val showCutout: Boolean = true,
    val showChassis: Boolean = true,
    val useGestureNav: Boolean = false,
    // 【PR-A】真实设备模拟开关. true=套壳显示; false=无外壳直接显示 compose UI.
    val deviceSimEnabled: Boolean = false,
)

/**
 * 视口 (缩放 / pan) 状态.
 */
@Immutable
data class ViewportState(
    val zoom: Float = 1.0f,
    val offsetXdp: Float = 0f,
    val offsetYdp: Float = 0f,
    val fitMode: FitMode = FitMode.FIT_WIDTH,
) {
    /**
     * 是否处于"可平移"状态 — 即 zoom 偏离 1.0 (放大或缩小).
     * 放大或缩小时用户都可以触摸拖动被裁掉的区域.
     * zoom == 1.0 时不显示平移状态, 即便 [offsetXdp] / [offsetYdp] 也不变.
     */
    val canPan: Boolean
        get() = zoom > 0.0001f && (zoom > 1.001f || zoom < 0.999f)
}

enum class FitMode { FIT_WIDTH, FIT_HEIGHT, ACTUAL_SIZE }

/**
 * 主题 (Light / Dark / Custom).
 */
enum class PreviewTheme { Light, Dark, Custom }

@OptIn(FlowPreview::class)
class ComposePreviewViewModel(
    private val repository: ComposePreviewRepository = ComposePreviewRepositoryImpl(),
    private val sourceParser: PreviewSourceParser = PreviewSourceParser(),
    // 【PR-C】桌面 launcher 用 — 解析 manifest android:icon + 拿到桌面 app 信息.
    private val projectContextSource: ProjectContextSource = ProjectContextSource(),
) : ViewModel() {

    private val _previewState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    private val _displayMode = MutableStateFlow(DisplayMode.ALL)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val _selectedPreview = MutableStateFlow<String?>(null)
    val selectedPreview: StateFlow<String?> = _selectedPreview.asStateFlow()

    private val _availablePreviews = MutableStateFlow<List<String>>(emptyList())
    val availablePreviews: StateFlow<List<String>> = _availablePreviews.asStateFlow()

    // === v2.1 新增 StateFlow ===

    private val _deviceConfig = MutableStateFlow(DeviceConfig())
    val deviceConfig: StateFlow<DeviceConfig> = _deviceConfig.asStateFlow()

    private val _viewport = MutableStateFlow(ViewportState())
    val viewport: StateFlow<ViewportState> = _viewport.asStateFlow()

    private val _theme = MutableStateFlow(PreviewTheme.Light)
    val theme: StateFlow<PreviewTheme> = _theme.asStateFlow()

    private val _debugEnabled = MutableStateFlow(false)
    val debugEnabled: StateFlow<Boolean> = _debugEnabled.asStateFlow()
    /**
     * 【PR-B】全屏模式. 默认为 [FullscreenMode.OFF] (非全屏).
     *
     * 单击全屏按钮: 切 [FullscreenMode.WITH_SYSTEM_BARS] (带系统状态栏的全屏).
     * 长按全屏按钮: 弹菜单, 用户选 [FullscreenMode.WITH_SYSTEM_BARS] 或
     * [FullscreenMode.WITHOUT_SYSTEM_BARS] (隐藏系统状态栏的纯全屏).
     * 退出全屏 (右上角 X 按钮): 设回 [FullscreenMode.OFF].
     */
    private val _fullscreenMode = MutableStateFlow(FullscreenMode.OFF)
    val fullscreenMode: StateFlow<FullscreenMode> = _fullscreenMode.asStateFlow()

    /**
     * 【PR-B】true=处于全屏 (无论是否隐藏系统状态栏). 由 [fullscreenMode] 派生,
     * 不需要单独维护. UI 用这个判断 toolbar 显示 / 隐藏 + 退出按钮.
     */
    val isFullscreen: StateFlow<Boolean> = _fullscreenMode
        .map { it != FullscreenMode.OFF }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // === PR-C 桌面 launcher 模拟 ===

    /**
     * 【PR-C】桌面上的应用列表 (系统应用占位 + 用户 app). 包含 [DesktopApp.DEFAULT_SYSTEM_APPS]
     * + 通过 [addUserApp] 加入的当前 project 的 launcher icon. 用于渲染桌面网格 + Dock 栏.
     */
    private val _desktopApps = MutableStateFlow(DesktopApp.DEFAULT_SYSTEM_APPS)
    val desktopApps: StateFlow<List<DesktopApp>> = _desktopApps.asStateFlow()

    /**
     * 【PR-C】当前在桌面看到的"前台 app". null=显示 launcher 桌面;
     * 非 null=用户点击了某个 app, "后台"运行 (这里只模拟 launch 状态, 实际仍渲染 preview).
     *
     * 用户要求 #1.1: 物理键返回桌面 — `goToHome()` 把这个设为 null.
     */
    private val _foregroundApp = MutableStateFlow<DesktopApp?>(null)
    val foregroundApp: StateFlow<DesktopApp?> = _foregroundApp.asStateFlow()

    private val sourceChanges = MutableSharedFlow<SourceUpdate>()

    private var currentSource: String = ""
    private var cachedFilePath: String = ""
    private var modulePath: String? = null
    private var variantName: String = "debug"
    private val isInitialized = AtomicBoolean(false)
    private var initializationDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val initMutex = Mutex()

    private data class SourceUpdate(
        val source: String,
        val parsedSource: ParsedPreviewSource
    )

    init {
        viewModelScope.launch {
            sourceChanges
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged { old, new -> old.source == new.source }
                .collect { update ->
                    compilePreview(update.source, update.parsedSource)
                }
        }
    }

    fun initialize(context: Context, filePath: String) {
        if (!isInitialized.compareAndSet(false, true)) return

        cachedFilePath = filePath

        viewModelScope.launch {
            _previewState.value = PreviewState.Initializing

            repository.initialize(context, filePath)
                .onSuccess { result ->
                    when (result) {
                        is InitializationResult.Ready -> {
                            modulePath = result.projectContext.modulePath
                            variantName = result.projectContext.variantName
                            initializationDeferred.complete(Unit)
                            _previewState.value = PreviewState.Idle
                            LOG.info("ViewModel initialized, modulePath={}, variant={}",
                                modulePath, variantName)
                        }
                        is InitializationResult.NeedsBuild -> {
                            modulePath = result.modulePath
                            variantName = result.variantName
                            initializationDeferred.complete(Unit)
                            _previewState.value = PreviewState.NeedsBuild(
                                result.modulePath,
                                result.variantName
                            )
                        }
                        is InitializationResult.Failed -> {
                            isInitialized.set(false)
                            initializationDeferred.complete(Unit)
                            _previewState.value = PreviewState.Error(result.message)
                        }
                    }
                }
                .onFailure { error ->
                    LOG.error("Initialization failed", error)
                    isInitialized.set(false)
                    initializationDeferred.complete(Unit)
                    _previewState.value = PreviewState.Error(
                        error.message ?: "Initialization failed"
                    )
                }
        }
    }

    fun onSourceChanged(source: String) {
        currentSource = source
        val parsed = parseAndValidateSource(source) ?: return

        viewModelScope.launch {
            sourceChanges.emit(SourceUpdate(source, parsed))
        }
    }

    fun compileNow(source: String) {
        currentSource = source
        val parsed = parseAndValidateSource(source) ?: return

        viewModelScope.launch {
            compilePreview(source, parsed)
        }
    }

    private fun parseAndValidateSource(source: String): ParsedPreviewSource? {
        if (_previewState.value is PreviewState.NeedsBuild) {
            LOG.debug("Skipping source processing - build required")
            return null
        }

        val parsed = sourceParser.parse(source)
        if (parsed == null) {
            _previewState.value = PreviewState.Error("Missing package declaration in source")
            return null
        }

        if (parsed.previewConfigs.isEmpty()) {
            _previewState.value = PreviewState.Empty
            return null
        }

        updateAvailablePreviews(parsed.previewConfigs)
        return parsed
    }

    private fun updateAvailablePreviews(configs: List<PreviewConfig>) {
        val functionNames = configs.map { it.functionName }
        _availablePreviews.value = functionNames
        if (_selectedPreview.value == null || !functionNames.contains(_selectedPreview.value)) {
            _selectedPreview.value = functionNames.first()
        }
    }

    private suspend fun compilePreview(source: String, parsed: ParsedPreviewSource) {
        initializationDeferred.await()

        if (!isInitialized.get()) {
            LOG.debug("Skipping compilePreview - initialization failed")
            return
        }

        if (_previewState.value is PreviewState.NeedsBuild) {
            LOG.debug("Skipping compilePreview - build required")
            return
        }

        _previewState.value = PreviewState.Compiling

        repository.compilePreview(source, parsed)
                .onSuccess { result ->
                    _previewState.value = PreviewState.Ready(
                        dexFile = result.dexFile,
                        className = result.className,
                        previewConfigs = parsed.previewConfigs,
                        projectDexFiles = result.projectDexFiles,
                        // v2.1 注入新状态
                        deviceConfig = _deviceConfig.value,
                        viewport = _viewport.value,
                        theme = _theme.value,
                        debugEnabled = _debugEnabled.value,
                    )
                }
            .onFailure { error ->
                val diagnostics = if (error is CompilationException) error.diagnostics else emptyList()
                _previewState.value = PreviewState.Error(
                    message = error.message ?: "Compilation failed",
                    diagnostics = diagnostics,
                    cause = error,
                )
            }
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
    }

    fun toggleDisplayMode() {
        _displayMode.value = when (_displayMode.value) {
            DisplayMode.ALL -> DisplayMode.SINGLE
            DisplayMode.SINGLE -> DisplayMode.ALL
        }
    }

    fun selectPreview(functionName: String) {
        if (_availablePreviews.value.contains(functionName)) {
            _selectedPreview.value = functionName
        }
    }

    fun getModulePath(): String = modulePath ?: ""
    fun getVariantName(): String = variantName
    fun canTriggerBuild(): Boolean = !modulePath.isNullOrEmpty()

    fun setBuildingState() {
        _previewState.value = PreviewState.Building
    }

    fun setBuildFailed() {
        _previewState.value = PreviewState.Error("Build failed. Check build output for details.")
    }

    fun refreshAfterBuild(context: Context) {
        viewModelScope.launch {
            initMutex.withLock {
                LOG.debug("refreshAfterBuild: starting, currentSource length={}", currentSource.length)

                repository.reset()
                isInitialized.set(false)
                initializationDeferred = kotlinx.coroutines.CompletableDeferred()

                _previewState.value = PreviewState.Initializing

                repository.initialize(context, cachedFilePath)
                    .onSuccess { result ->
                        when (result) {
                            is InitializationResult.Ready -> {
                                modulePath = result.projectContext.modulePath
                                variantName = result.projectContext.variantName
                                isInitialized.set(true)
                                initializationDeferred.complete(Unit)
                                LOG.debug("refreshAfterBuild: initialization complete, state=Ready")
                                if (currentSource.isNotBlank()) {
                                    compileNow(currentSource)
                                } else {
                                    _previewState.value = PreviewState.Idle
                                }
                            }
                            is InitializationResult.NeedsBuild -> {
                                // 【关键修复】build 刚完成, initialize 仍判定 NeedsBuild
                                // (例如 gradle.properties 启用了 useGradleDexing, 或
                                //  intermediateClasspaths 临时为空). 此时强制走 compileNow
                                // 兜底, 让 preview 真正进入编译流程, 而不是停在
                                // Build Project 按钮页.
                                modulePath = result.modulePath
                                variantName = result.variantName
                                isInitialized.set(true)
                                initializationDeferred.complete(Unit)
                                LOG.warn(
                                    "refreshAfterBuild: initialize returned NeedsBuild " +
                                        "after build success (forceGradleDexing or " +
                                        "intermediate empty), forcing compileNow bypass",
                                )
                                _previewState.value = PreviewState.Initializing
                                if (currentSource.isNotBlank()) {
                                    compileNow(currentSource)
                                } else {
                                    _previewState.value = PreviewState.Idle
                                }
                            }
                            is InitializationResult.Failed -> {
                                initializationDeferred.complete(Unit)
                                LOG.error("refreshAfterBuild: initialization failed - {}", result.message)
                                _previewState.value = PreviewState.Error(result.message)
                            }
                        }
                    }
                    .onFailure { error ->
                        initializationDeferred.complete(Unit)
                        LOG.error("refreshAfterBuild: initialization failed", error)
                        _previewState.value = PreviewState.Error(
                            error.message ?: "Initialization failed"
                        )
                    }
            }
        }
    }

    // === v2.1 新增方法 ===

    fun selectDevice(profile: DeviceProfile) {
        _deviceConfig.value = _deviceConfig.value.copy(profile = profile, deviceSimEnabled = true)
        // 【PR-D】换设备时重置 pan, 不同 profile 的宽高 / 宽高比不同, 旧的
        // offset 在新设备下可能对应完全不同的位置.
        resetPan()
        LOG.info("Device selected: {}", profile.displayName)
    }

    fun setSystemBarsTheme(theme: SystemBarsTheme) {
        _deviceConfig.value = _deviceConfig.value.copy(systemBarsTheme = theme)
    }

    /**
     * 【v3.2】切换"真实设备模拟"开关. true=套壳显示, false=无外壳直接显示.
     * 切到 false 时, profile 仍保留, 方便用户切回去.
     */
    fun toggleDeviceSim() {
        _deviceConfig.value = _deviceConfig.value.copy(
            deviceSimEnabled = !_deviceConfig.value.deviceSimEnabled
        )
        // 【PR-D】设备/无设备切换: 父容器尺寸会变, 重置 pan
        resetPan()
        LOG.info("Device sim toggled: {}", _deviceConfig.value.deviceSimEnabled)
    }

    /**
     * 【PR-B】切换全屏. 默认切到带系统状态栏的全屏. 长按全屏按钮调 [setFullscreenMode].
     * - OFF -> WITH_SYSTEM_BARS
     * - WITH_SYSTEM_BARS / WITHOUT_SYSTEM_BARS -> OFF
     */
    fun toggleFullscreen() {
        _fullscreenMode.value = if (_fullscreenMode.value == FullscreenMode.OFF) {
            FullscreenMode.WITH_SYSTEM_BARS
        } else {
            FullscreenMode.OFF
        }
        LOG.info("Fullscreen toggled: {}", _fullscreenMode.value)
    }

    /**
     * 【PR-B】设置全屏模式 (长按全屏按钮菜单调用).
     */
    fun setFullscreenMode(mode: FullscreenMode) {
        _fullscreenMode.value = mode
        LOG.info("Fullscreen mode set: {}", mode)
    }

    fun toggleSystemBars() {
        _deviceConfig.value = _deviceConfig.value.copy(
            showStatusBar = !_deviceConfig.value.showStatusBar,
            showNavigationBar = !_deviceConfig.value.showNavigationBar,
        )
    }

    fun setZoom(zoom: Float) {
        val coerced = zoom.coerceIn(0.1f, 5.0f)
        // 【PR-D】zoom 回到 1.0 时清空 pan 偏移, 否则下次放大时会"跳"到之前
        // 拖到的位置, 体验不自然. zoom 变化 (不是回到 1.0) 也保留 offset, 让用户
        // 在缩放过程中能维持位置.
        val newOffsetX = if (coerced in 0.999f..1.001f) 0f else _viewport.value.offsetXdp
        val newOffsetY = if (coerced in 0.999f..1.001f) 0f else _viewport.value.offsetYdp
        _viewport.value = _viewport.value.copy(
            zoom = coerced,
            offsetXdp = newOffsetX,
            offsetYdp = newOffsetY,
        )
    }

    fun fitZoom() {
        // fitZoom 强制重置, 包括 offset
        _viewport.value = ViewportState()
    }

    /**
     * 【PR-D】触摸拖动平移. 上层 (ReadyPanel) 在 zoom 偏离 1.0 时给 zoomed 内容加
     * `pointerInput + detectDragGestures`, 把 drag delta 转成 dp 后调这个方法.
     *
     * 重要: `deltaXDp` / `deltaYDp` 是**手指拖动**的位移 (不是绝对位置), 累加到
     * 当前 offset 上. ReadyPanel 内需要根据"父容器宽 × zoom"算出 max pan 范围
     * 做边界 clamp, 防止用户把内容完全拖出屏幕.
     *
     * @param maxXDp 父容器可平移的最大 X 范围 (单位 dp). 通常是 (parentWidthDp * (zoom - 1) / 2).
     * @param maxYDp 父容器可平移的最大 Y 范围 (单位 dp).
     */
    fun panBy(deltaXDp: Float, deltaYDp: Float, maxXDp: Float, maxYDp: Float) {
        val current = _viewport.value
        if (!current.canPan) return
        val newX = (current.offsetXdp + deltaXDp).coerceIn(-maxXDp, maxXDp)
        val newY = (current.offsetYdp + deltaYDp).coerceIn(-maxYDp, maxYDp)
        if (newX == current.offsetXdp && newY == current.offsetYdp) return
        _viewport.value = current.copy(offsetXdp = newX, offsetYdp = newY)
    }

    /**
     * 【PR-D】重置 pan 到中心 (offsetX=0, offsetY=0). 在以下时机调:
     * - 切换 deviceSimEnabled (设备/无设备模式布局差异)
     * - 切换 device profile
     * - 重新编译完成
     */
    fun resetPan() {
        if (_viewport.value.offsetXdp == 0f && _viewport.value.offsetYdp == 0f) return
        _viewport.value = _viewport.value.copy(offsetXdp = 0f, offsetYdp = 0f)
    }

    fun cycleTheme() {
        _theme.value = when (_theme.value) {
            PreviewTheme.Light -> PreviewTheme.Dark
            PreviewTheme.Dark -> PreviewTheme.Custom
            PreviewTheme.Custom -> PreviewTheme.Light
        }
    }

    fun toggleDebug() {
        _debugEnabled.value = !_debugEnabled.value
    }

    // === PR-C 桌面 launcher 方法 ===

    /**
     * 【PR-C】把当前 project 的 launcher app 加入桌面 (首次启动桌面时调用).
     * 用 [DesktopApp.iconResId] = `0` 时 DesktopLauncher 会去解析 manifest android:icon 拿真实图标.
     */
    fun addUserApp(app: DesktopApp) {
        if (_desktopApps.value.any { it.id == app.id }) return
        _desktopApps.value = _desktopApps.value + app
        LOG.info("Added user app to desktop: {} ({})", app.label, app.id)
    }

    /**
     * 【PR-C】点击桌面应用 — 模拟 launch 流程. isClickable=false 的系统应用占位
     * 不响应, 真实点击 (用户要求 #1.1 "模拟系统 app 不响应").
     */
    fun launchDesktopApp(app: DesktopApp) {
        if (!app.isClickable) {
            LOG.info("App {} is not clickable, ignoring launch", app.id)
            return
        }
        _foregroundApp.value = app
        LOG.info("Launched desktop app: {}", app.label)
    }

    /**
     * 【PR-C】物理键 (Home) 返回桌面. 模拟"按 home 键后台 app" — 把前台 app 置空.
     * PR-C 用户要求 #1.1: "物理键返回桌面".
     */
    fun goToHome() {
        if (_foregroundApp.value != null) {
            LOG.info("Going to home, foreground app was: {}", _foregroundApp.value?.label)
        }
        _foregroundApp.value = null
    }

    /**
     * 【PR-C】从 manifest 解析 application 信息, 把当前 project 的 launcher app
     * 加入桌面. 一般在 ViewModel.initialize 后, Ready 第一次进入时调一次.
     *
     * 用 [modulePath] 找 `src/main/AndroidManifest.xml`, 解析 `<application android:icon>`
     * + `android:label`. 拿不到就用占位.
     */
    fun loadUserApp(modulePath: String?) {
        if (modulePath.isNullOrBlank()) return
        val info = projectContextSource.loadApplicationIcon(modulePath) ?: return

        val app = DesktopApp(
            id = info.packageName ?: "user.app",
            label = info.applicationLabel ?: "My App",
            packageName = info.packageName,
            iconResName = info.iconResName,
            isClickable = true, // 用户 app 可点击
        )
        addUserApp(app)
    }

    override fun onCleared() {
        super.onCleared()
        repository.reset()
        LOG.debug("ComposePreviewViewModel cleared")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ComposePreviewViewModel::class.java)
        private const val DEBOUNCE_MS = 500L
    }
}

/**
 * 【PR-B】全屏模式.
 *
 * - [OFF]: 非全屏, 顶栏显示.
 * - [WITH_SYSTEM_BARS]: 全屏, 但保留手机系统状态栏 (用户在 APK 安装运行后看到的样子).
 * - [WITHOUT_SYSTEM_BARS]: 纯全屏, 隐藏系统状态栏, 适合查看实际应用沉浸式全屏效果.
 */
@Immutable
enum class FullscreenMode {
    OFF, WITH_SYSTEM_BARS, WITHOUT_SYSTEM_BARS;

    val isFullscreen: Boolean get() = this != OFF
}
