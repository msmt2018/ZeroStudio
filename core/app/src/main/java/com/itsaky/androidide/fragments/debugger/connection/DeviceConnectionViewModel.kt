package com.itsaky.androidide.fragments.debugger.connection

import android.zero.studio.shell.common.domain.repository.ShellRepository
import android.zero.studio.shell.otg_adb_shell.domain.model.OtgConnection
import android.zero.studio.shell.otg_adb_shell.domain.model.OtgState
import android.zero.studio.shell.otg_adb_shell.domain.repository.OtgRepository
import android.zero.studio.shell.wifi_adb_shell.data.repository.WifiAdbRepositoryImpl.ConnectionListener
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbConnection
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbDevice
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbState
import android.zero.studio.shell.wifi_adb_shell.domain.repository.WifiAdbRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsaky.androidide.debugger.connection.status.ChannelStatus
import com.itsaky.androidide.debugger.connection.status.ConnectionStatusAggregator
import com.itsaky.androidide.debugger.root.RootAdbBridge
import com.itsaky.androidide.debugger.root.RootAdbDevice
import com.itsaky.androidide.debugger.root.RootManager
import com.itsaky.androidide.debugger.root.RootState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设备连接页 ViewModel。
 *
 * 汇总各通道状态供 UI 消费，并提供刷新 / 探测 / 连接等操作入口。
 *
 * 落实 spec §4.2 / §4.4 / §7.4：
 * - 无线 ADB「启动」按钮：[startWifiAdb] 复用 [WifiAdbRepository.connect]
 * - OTG「等待设备 / 管理设备」：[startOtgScan] / [disconnectOtg] 复用 [OtgRepository]
 * - 状态刷新：[refreshAll] 并行触发各通道刷新
 */
@HiltViewModel
class DeviceConnectionViewModel @Inject constructor(
    private val aggregator: ConnectionStatusAggregator,
    private val rootManager: RootManager,
    private val rootAdbBridge: RootAdbBridge,
    private val shellRepository: ShellRepository,
    private val wifiAdbRepository: WifiAdbRepository,
    private val otgRepository: OtgRepository,
) : ViewModel() {

    val allStatuses: StateFlow<List<ChannelStatus>> = aggregator.allStatuses
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val rootState: StateFlow<RootState> = rootManager.rootState

    val rootDevices: StateFlow<List<RootAdbDevice>> = rootAdbBridge.deviceList

    /** WiFi ADB 当前已保存的设备列表（用于「启动」按钮选择目标）。 */
    val savedWifiDevices: StateFlow<List<WifiAdbDevice>> = wifiAdbRepository.getSavedDevicesFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** OTG 通道当前状态（供 OTG 卡片显示设备名 / 连接状态）。 */
    val otgState: StateFlow<OtgState> = OtgConnection.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, OtgState.Idle)

    /** 是否正在刷新所有通道。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 最近一次操作的提示信息（供 UI snackbar 显示）。 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** 消费 toast。 */
    fun consumeToast() {
        _toast.value = null
    }

    /**
     * 刷新所有通道状态。落实 spec §7.4：并行触发 4 个刷新操作。
     *
     * - [shellRepository.refreshShizukuPermission]（ShizukuPermissionHandler.refreshPermissionState）
     * - [rootManager.probe]（Root 探测）
     * - [otgRepository.searchDevices]（OTG 重新扫描 USB 设备）
     * - [wifiAdbRepository.startHeartbeat]（WiFi ADB 重启心跳触发状态检查）
     *
     * 4 个操作通过 `async` 并行触发，`awaitAll` 等待全部完成；
     * 任一通道异常不影响其他通道刷新。
     */
    fun refreshAll() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            coroutineScope {
                awaitAll(
                    async {
                        runCatching { shellRepository.refreshShizukuPermission() }
                    },
                    async {
                        runCatching { rootManager.probe() }
                    },
                    async {
                        runCatching { otgRepository.searchDevices() }
                    },
                    async {
                        // WiFi ADB 心跳实时推送状态，重启心跳以重新检查当前设备
                        runCatching { wifiAdbRepository.startHeartbeat() }
                    },
                )
            }
            // Root ADB 设备列表依赖 Root 授权，放在 Root 探测之后串行触发，
            // 避免在未授权时反复 adb start-server。
            runCatching { rootAdbBridge.refreshDevices() }
            _refreshing.value = false
        }
    }

    /** 触发 Root 探测。 */
    fun probeRoot() {
        viewModelScope.launch { rootManager.probe() }
    }

    /**
     * 按指定管理器类型执行差异化授权探测。落实 spec §4.3.1 / §4.3.4。
     *
     * 用户在 [RootManagerPickerSheet] 选中某个管理器后调用。
     */
    fun probeRootAs(type: com.itsaky.androidide.debugger.root.RootManagerType) {
        viewModelScope.launch { rootManager.probeAs(type) }
    }

    /** 已检测到的 Root 管理器集合（供 RootManagerPickerSheet 显示可用性）。 */
    private val _availableManagers = MutableStateFlow<Set<com.itsaky.androidide.debugger.root.RootManagerType>>(emptySet())
    val availableManagers: StateFlow<Set<com.itsaky.androidide.debugger.root.RootManagerType>> =
        _availableManagers.asStateFlow()

    /** 探测已安装的管理器列表。 */
    fun detectManagers() {
        viewModelScope.launch {
            _availableManagers.value = rootManager.detectAvailableManagers()
        }
    }

    /** Root 授权后连接本机。 */
    fun connectLocal() {
        if (rootManager.isGranted) rootAdbBridge.connectLocal()
    }

    /** Root 授权后连接无线设备。 */
    fun connectWifiDevice(ip: String, port: Int, onResult: (Result<RootAdbDevice>) -> Unit) {
        viewModelScope.launch {
            val result = rootAdbBridge.connectWifi(ip, port)
            onResult(result)
        }
    }

    /** 断开无线设备。 */
    fun disconnectWifiDevice(address: String) {
        rootAdbBridge.disconnectWifi(address)
    }

    /** 设为活动设备。 */
    fun setActiveDevice(serial: String) {
        rootAdbBridge.setActive(serial)
    }

    /** 刷新 Root ADB 设备列表。 */
    fun refreshRootDevices() {
        viewModelScope.launch {
            rootAdbBridge.refreshDevices()
        }
    }

    /**
     * 无线 ADB「启动」按钮。落实 spec §4.2：
     * - 已有保存设备 → 连接最近一台 ([WifiAdbRepository.getCurrentDevice] 优先)
     * - 无保存设备 → 提示先配对
     */
    fun startWifiAdb() {
        val state = WifiAdbConnection.currentState
        if (state is WifiAdbState.Connected) {
            _toast.value = "已连接到 ${state.address}"
            return
        }
        val target = wifiAdbRepository.getCurrentDevice() ?: savedWifiDevices.value.firstOrNull()
        if (target == null) {
            _toast.value = "请先配对设备"
            return
        }
        wifiAdbRepository.connect(target.ip, target.port, object : ConnectionListener {
            override fun onConnectionSuccess() {
                _toast.value = "已连接到 ${target.ip}:${target.port}"
            }

            override fun onConnectionFailed() {
                _toast.value = "连接失败：${target.ip}:${target.port}"
            }
        })
    }

    /**
     * OTG「等待设备」按钮。落实 spec §4.4：触发 USB 设备扫描。
     */
    fun startOtgScan() {
        if (otgRepository.isConnected()) {
            _toast.value = "OTG 已连接"
            return
        }
        otgRepository.searchDevices()
    }

    /**
     * OTG 断开当前设备。落实 spec §4.4「管理设备」。
     */
    fun disconnectOtg() {
        otgRepository.disconnect()
    }

    /**
     * OTG 注销 BroadcastReceiver。彻底释放 USB 监听。
     */
    fun unregisterOtg() {
        otgRepository.unRegister()
    }

    /**
     * 将当前 OTG 连接的 USB 设备镜像到 Root ADB 设备列表。落实 spec §4.3.3 / §4.3.5。
     *
     * 当 OTG 连接成功且 Root 已授权时调用，使 USB 设备在 RootAdbDeviceSheet 中可见，
     * 便于「设为活动」后用 Root ADB 通道执行命令。
     *
     * @param serial USB 设备序列号
     * @param deviceName USB 设备名（用于显示）
     */
    fun mirrorUsbDevice(serial: String, deviceName: String) {
        if (rootManager.isGranted) {
            rootAdbBridge.mirrorUsbDevice(serial, deviceName)
        }
    }
}