package com.itsaky.androidide.fragments.debugger.connection

import android.zero.studio.shell.common.domain.repository.ShellRepository
import android.zero.studio.shell.otg_adb_shell.domain.repository.OtgRepository
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

    val rootState: StateFlow<RootState> = rootManager.rootState.asStateFlow()

    val rootDevices: StateFlow<List<RootAdbDevice>> = rootAdbBridge.deviceList.asStateFlow()

    /** 是否正在刷新所有通道。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 刷新所有通道状态。 */
    fun refreshAll() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            runCatching {
                shellRepository.refreshShizukuPermission()
                rootManager.probe()
                // WiFi ADB / OTG 状态由各自 StateFlow 实时推送，无需显式 refresh
            }
            _refreshing.value = false
        }
    }

    /** 触发 Root 探测。 */
    fun probeRoot() {
        viewModelScope.launch { rootManager.probe() }
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
}