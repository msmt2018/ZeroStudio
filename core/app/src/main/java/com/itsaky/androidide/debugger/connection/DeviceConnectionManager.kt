/*
 * ZeroStudio IDE - 设备连接管理
 *
 * DeviceConnectionManager: 统一管理 Shizuku 和 Root 两种 ADB 连接方式。
 *
 * 设计参考 debugger/android-adb-shell 的 RootExecutor + ShizukuPermissionHandler
 * 架构,但源码自主编写,API 设计贴合项目已有的 ConnectionType /
 * DebugConnectionPreferences 连接层。
 *
 * - Shizuku: 通过 rikka.shizuku.Shizuku 静态 API 检测服务状态和权限
 * - Root:    通过 libsu.core (com.topjohnwu.superuser.Shell) 检测 root 可用性
 *            跟参考工程 debugger/android-adb-shell 保持一致 (不再用 Runtime.exec("su"))
 *
 * 两种方式都暴露为 StateFlow,供 UI 响应式观察。
 */

package com.itsaky.androidide.debugger.connection

import android.content.pm.PackageManager
import androidx.compose.runtime.Stable
import com.topjohnwu.superuser.Shell
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
     * 配置 libsu Shell 的默认 Builder。
     *
     * 必须在首次 [Shell.getShell] 之前调用 —— Shell 实例一旦创建就会被缓存,
     * 后续 getShell() 都返回同一个实例, 所以 Builder 只能生效一次。
     *
     * 消费 [DebugConnectionPreferences.rootSuBin]:
     *   - 默认 "/system/bin/su", 用户可在偏好设置 → Debugger → Root → su binary path 修改
     *   - 通过 [Shell.Builder.setCommands] 传入, 让 libsu 用自定义路径拉起 root shell
     *
     * 参考: modules/shizuku/manager/ShizukuApplication.kt 的 Shell.setDefaultBuilder 用法。
     */
    private fun ensureShellBuilderConfigured() {
        val suBin = DebugConnectionPreferences.rootSuBin
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setCommands(suBin)
        )
    }

    /**
     * 探测设备是否已 root。
     *
     * 使用 libsu.core 的官方推荐方式 (跟参考工程 debugger/android-adb-shell 的
     * RootExecutor 一致):
     *   1. [Shell.getShell] 同步拉起 su 进程 (首次会弹 root 授权框, 必须在 IO 线程)
     *   2. [Shell.isRoot] 直接反映底层 su 是否启动成功
     *   3. 第一次失败时主动 close 缓存的 Shell 再重试一次 (参考 Shizuku StarterActivity)
     *
     * 不用 Shell.cmd("id").exec() —— isRoot 已经足够, 而且不会污染 Shell 的 last result。
     */
    suspend fun probeRoot() {
        _rootState.value = RootProbeState.Probing
        val available = withContext(Dispatchers.IO) {
            try {
                ensureShellBuilderConfigured()
                // 首次 getShell() 会同步拉起 su, 可能弹授权框, 必须在 IO 线程
                var isRoot = Shell.getShell().isRoot
                if (!isRoot) {
                    // 第一次失败: 清缓存重试一次 (参考 Shizuku StarterActivity.startRoot)
                    Shell.getCachedShell()?.close()
                    isRoot = Shell.getShell().isRoot
                }
                isRoot
            } catch (e: Throwable) {
                false
            }
        }
        _rootState.value = if (available) RootProbeState.Available else RootProbeState.Unavailable
    }

    /**
     * 当用户在偏好设置里修改了 su binary 路径后, 需要调用此方法清掉已缓存的 Shell,
     * 否则 [Shell.getShell] 仍返回旧 Shell (用的是旧 suBin 拉起的)。
     */
    fun invalidateCachedShell() {
        Shell.getCachedShell()?.close()
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
