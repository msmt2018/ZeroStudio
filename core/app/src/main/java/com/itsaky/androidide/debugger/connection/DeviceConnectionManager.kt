/*
 * ZeroStudio IDE - 设备连接管理
 *
 * DeviceConnectionManager: 统一管理 Shizuku 和 Root 两种 ADB 连接方式。
 *
 * 设计参考 debugger/android-adb-shell 的 ShellCommandExecutor + ShizukuPermissionHandler
 * + ShellRepositoryImpl 架构,但源码自主编写,API 设计贴合项目已有的 ConnectionType /
 * DebugConnectionPreferences 连接层。
 *
 * - Shizuku: 通过 rikka.shizuku.Shizuku 静态 API 检测服务状态和权限
 * - Root:    通过 Runtime.exec("su") 检测 root 可用性
 *
 * 两种方式都暴露为 StateFlow,供 UI 响应式观察。
 */

package com.itsaky.androidide.debugger.connection

import android.content.pm.PackageManager
import androidx.compose.runtime.Stable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Shizuku 服务的运行状态。 */
sealed interface ShizukuServiceState {
    /** Shizuku 服务未安装或未启动 (pingBinder 返回 false)。 */
    object NotRunning : ShizukuServiceState

    /** Shizuku 服务已启动但应用尚未被授权。 */
    object RunningUnauthorized : ShizukuServiceState

    /** Shizuku 服务已启动且应用已被授权,可以使用。 */
    object RunningAuthorized : ShizukuServiceState
}

/** Root 检测结果。 */
sealed interface RootProbeState {
    /** 尚未检测。 */
    object NotProbed : RootProbeState

    /** 正在检测中。 */
    object Probing : RootProbeState

    /** 设备已 root 且 su 可用。 */
    object Available : RootProbeState

    /** 设备未 root 或 su 不可用。 */
    object Unavailable : RootProbeState
}

/**
 * 单个连接通道的聚合状态,供 UI 直接渲染。
 */
@Stable
data class ConnectionChannelStatus(
    val type: ConnectionType,
    val available: Boolean,
    val statusText: String,
    val detailText: String,
)

/**
 * 设备连接管理器。
 *
 * 使用方式:
 *   val manager = DeviceConnectionManager
 *   manager.refreshShizukuState()
 *   manager.probeRoot()
 *   manager.activeType  // 当前选中的连接类型
 *
 * 线程安全: Shizuku 的 StateFlow 在主线程更新; Root 探测在 IO 线程执行后回主线程。
 */
object DeviceConnectionManager {

    // ---- Shizuku ----

    private val _shizukuState = MutableStateFlow<ShizukuServiceState>(ShizukuServiceState.NotRunning)
    val shizukuState: StateFlow<ShizukuServiceState> = _shizukuState.asStateFlow()

    private val shizukuPermissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                _shizukuState.value = ShizukuServiceState.RunningAuthorized
            } else {
                _shizukuState.value = ShizukuServiceState.RunningUnauthorized
            }
            Shizuku.removeRequestPermissionResultListener(this)
        }
    }

    /**
     * 刷新 Shizuku 服务状态。
     *
     * 三态判定:
     *   - pingBinder() == false              -> NotRunning
     *   - pingBinder() == true && 未授权      -> RunningUnauthorized
     *   - pingBinder() == true && 已授权      -> RunningAuthorized
     */
    fun refreshShizukuState() {
        _shizukuState.value = if (!Shizuku.pingBinder()) {
            ShizukuServiceState.NotRunning
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuServiceState.RunningAuthorized
        } else {
            ShizukuServiceState.RunningUnauthorized
        }
    }

    /**
     * 请求 Shizuku 授权。
     *
     * 仅当服务已运行但未授权时调用。注册一次性监听器,
     * 用户在 Shizuku app 弹窗里选择后,结果会回写到 [shizukuState]。
     */
    fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) return
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.requestPermission(0)
    }

    /** Shizuku 是否可用 (服务运行 + 已授权)。 */
    val isShizukuAvailable: Boolean
        get() = _shizukuState.value == ShizukuServiceState.RunningAuthorized

    // ---- Root ----

    private val _rootState = MutableStateFlow<RootProbeState>(RootProbeState.NotProbed)
    val rootState: StateFlow<RootProbeState> = _rootState.asStateFlow()

    /**
     * 探测设备是否已 root。
     *
     * 原理: 启动 su 进程, 写入 `id\nexit\n`, 若 exit code == 0 则 root 可用。
     * 在 IO 线程执行, 避免阻塞主线程。
     */
    suspend fun probeRoot() {
        _rootState.value = RootProbeState.Probing
        val available = withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = process.outputStream
                os.write("id\n".toByteArray())
                os.flush()
                os.write("exit\n".toByteArray())
                os.flush()
                os.close()
                val exitCode = process.waitFor()
                process.destroy()
                exitCode == 0
            } catch (e: Exception) {
                false
            }
        }
        _rootState.value = if (available) RootProbeState.Available else RootProbeState.Unavailable
    }

    /** Root 是否可用。 */
    val isRootAvailable: Boolean
        get() = _rootState.value == RootProbeState.Available

    // ---- 活跃连接类型 ----

    /**
     * 当前选中的连接类型,持久化到 [DebugConnectionPreferences]。
     */
    var activeType: ConnectionType
        get() = DebugConnectionPreferences.activeType
        set(value) {
            DebugConnectionPreferences.activeType = value
        }

    // ---- 聚合状态 (供 UI 渲染) ----

    /**
     * 获取 Shizuku 通道的聚合状态。
     */
    fun shizukuChannelStatus(): ConnectionChannelStatus {
        val state = _shizukuState.value
        return when (state) {
            ShizukuServiceState.NotRunning -> ConnectionChannelStatus(
                type = ConnectionType.Shizuku,
                available = false,
                statusText = "未运行",
                detailText = "Shizuku 服务未启动,请先打开 Shizuku app 并启动服务",
            )
            ShizukuServiceState.RunningUnauthorized -> ConnectionChannelStatus(
                type = ConnectionType.Shizuku,
                available = false,
                statusText = "未授权",
                detailText = "Shizuku 服务已运行,但本应用尚未获得授权",
            )
            ShizukuServiceState.RunningAuthorized -> ConnectionChannelStatus(
                type = ConnectionType.Shizuku,
                available = true,
                statusText = "就绪",
                detailText = "Shizuku 服务已运行且已授权,可以使用",
            )
        }
    }

    /**
     * 获取 Root 通道的聚合状态。
     */
    fun rootChannelStatus(): ConnectionChannelStatus {
        val state = _rootState.value
        return when (state) {
            RootProbeState.NotProbed -> ConnectionChannelStatus(
                type = ConnectionType.Root,
                available = false,
                statusText = "未检测",
                detailText = "尚未检测 Root 状态,点击「检测」按钮",
            )
            RootProbeState.Probing -> ConnectionChannelStatus(
                type = ConnectionType.Root,
                available = false,
                statusText = "检测中",
                detailText = "正在尝试执行 su...",
            )
            RootProbeState.Available -> ConnectionChannelStatus(
                type = ConnectionType.Root,
                available = true,
                statusText = "就绪",
                detailText = "设备已 Root,su 可用",
            )
            RootProbeState.Unavailable -> ConnectionChannelStatus(
                type = ConnectionType.Root,
                available = false,
                statusText = "不可用",
                detailText = "设备未 Root 或 su 被拒绝",
            )
        }
    }
}
