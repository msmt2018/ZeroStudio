package com.itsaky.androidide.debugger.connection.status

import android.zero.studio.shell.local_adb_shell.data.shizuku.ShizukuPermissionHandler
import android.zero.studio.shell.otg_adb_shell.domain.model.OtgConnection
import android.zero.studio.shell.otg_adb_shell.domain.model.OtgState
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbConnection
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbState
import com.itsaky.androidide.debugger.root.RootAdbBridge
import com.itsaky.androidide.debugger.root.RootManager
import com.itsaky.androidide.debugger.root.RootState
import com.itsaky.androidide.ui.theme.deviceconnection.DcChannel
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 连接状态聚合器。
 *
 * 把 Shizuku / Root / Root ADB 设备 / OTG / WiFi ADB 五个通道的状态归一化为 [ChannelStatus]，
 * 供设备连接页总览条与 adb 命令执行页消费。
 *
 * 落实 spec §7.2 状态来源映射表（5 行）。
 *
 * 复用 connection 模块的状态单例：
 * - [ShizukuPermissionHandler.permissionGranted]
 * - [RootManager.rootState]（新建）
 * - [RootAdbBridge.deviceList]（新建，独立 Root ADB 设备通道）
 * - [OtgConnection.state]
 * - [WifiAdbConnection.state] / [WifiAdbConnection.currentDevice]
 */
@Singleton
class ConnectionStatusAggregator @Inject constructor(
    private val shizukuPermissionHandler: ShizukuPermissionHandler,
    private val rootManager: RootManager,
    private val rootAdbBridge: RootAdbBridge,
) {

    /** Shizuku 通道状态。 */
    val shizukuStatus: Flow<ChannelStatus> =
        shizukuPermissionHandler.permissionGranted.map { granted ->
            if (granted) {
                ChannelStatus(DcChannel.SHIZUKU, DcStatusLevel.GREEN, "已授权")
            } else {
                ChannelStatus(DcChannel.SHIZUKU, DcStatusLevel.RED, "未授权")
            }
        }

    /** Root 通道状态。 */
    val rootStatus: Flow<ChannelStatus> = rootManager.rootState.map { state ->
        when (state) {
            is RootState.Granted -> ChannelStatus(
                DcChannel.ROOT,
                DcStatusLevel.GREEN,
                "已 root",
                deviceName = state.manager.displayName,
            )
            RootState.Probing -> ChannelStatus(DcChannel.ROOT, DcStatusLevel.YELLOW, "探测中")
            RootState.Idle -> ChannelStatus(DcChannel.ROOT, DcStatusLevel.YELLOW, "未探测")
            RootState.Denied -> ChannelStatus(DcChannel.ROOT, DcStatusLevel.RED, "无 root")
            is RootState.Error -> ChannelStatus(DcChannel.ROOT, DcStatusLevel.RED, "探测失败")
        }
    }

    /**
     * Root ADB 设备通道状态。落实 spec §7.2「Root ADB 设备」行。
     *
     * - 有活动设备→GREEN+设备名
     * - 无活动设备→YELLOW「无活动设备」
     */
    val rootAdbDeviceStatus: Flow<ChannelStatus> = rootAdbBridge.deviceList.map { devices ->
        val active = devices.firstOrNull { it.isActive }
        if (active != null) {
            ChannelStatus(
                DcChannel.ROOT_ADB,
                DcStatusLevel.GREEN,
                "已连接",
                deviceName = active.model ?: active.serial,
            )
        } else {
            ChannelStatus(DcChannel.ROOT_ADB, DcStatusLevel.YELLOW, "无活动设备")
        }
    }

    /** OTG 通道状态。 */
    val otgStatus: Flow<ChannelStatus> = OtgConnection.state.map { state ->
        when (state) {
            is OtgState.Connected -> ChannelStatus(
                DcChannel.OTG,
                DcStatusLevel.GREEN,
                "已连接",
                deviceName = state.deviceName,
            )
            is OtgState.DeviceFound -> ChannelStatus(
                DcChannel.OTG,
                DcStatusLevel.YELLOW,
                "设备已发现",
                deviceName = state.deviceName,
            )
            OtgState.Searching, OtgState.Connecting -> ChannelStatus(
                DcChannel.OTG,
                DcStatusLevel.YELLOW,
                "连接中",
            )
            OtgState.Idle, OtgState.Disconnected -> ChannelStatus(
                DcChannel.OTG,
                DcStatusLevel.RED,
                "未连接",
            )
            OtgState.PermissionDenied -> ChannelStatus(
                DcChannel.OTG,
                DcStatusLevel.RED,
                "USB 权限拒绝",
            )
            OtgState.UsbManagerUnavailable -> ChannelStatus(
                DcChannel.OTG,
                DcStatusLevel.RED,
                "USB 不可用",
            )
            is OtgState.Error -> ChannelStatus(
                DcChannel.OTG,
                DcStatusLevel.RED,
                "错误",
            )
        }
    }

    /** WiFi ADB 通道状态。 */
    val wifiAdbStatus: Flow<ChannelStatus> = combine(
        WifiAdbConnection.state,
        WifiAdbConnection.currentDevice,
    ) { state, device ->
        when (state) {
            is WifiAdbState.Connected -> ChannelStatus(
                DcChannel.WIFI_ADB,
                DcStatusLevel.GREEN,
                "已连接",
                deviceName = device?.address ?: state.address,
            )
            is WifiAdbState.Connecting,
            is WifiAdbState.Reconnecting,
            is WifiAdbState.Pairing,
            is WifiAdbState.Discovering -> ChannelStatus(
                DcChannel.WIFI_ADB,
                DcStatusLevel.YELLOW,
                "连接中",
            )
            is WifiAdbState.Disconnected,
            WifiAdbState.Idle -> ChannelStatus(
                DcChannel.WIFI_ADB,
                DcStatusLevel.RED,
                "未连接",
            )
        }
    }

    /**
     * 所有通道状态合并（5 个通道）。落实 spec §7.2。
     *
     * combine 最多支持 5 个 Flow，刚好覆盖 Shizuku / Root / Root ADB 设备 / OTG / WiFi ADB。
     */
    val allStatuses: Flow<List<ChannelStatus>> = combine(
        shizukuStatus,
        rootStatus,
        rootAdbDeviceStatus,
        otgStatus,
        wifiAdbStatus,
    ) { s, r, ra, o, w -> listOf(s, r, ra, o, w) }
}