package com.itsaky.androidide.debugger.connection.status

import android.zero.studio.shell.local_adb_shell.data.shizuku.ShizukuPermissionHandler
import android.zero.studio.shell.otg_adb_shell.domain.model.OtgConnection
import android.zero.studio.shell.otg_adb_shell.domain.model.OtgState
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbConnection
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbState
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
 * 把 Shizuku / Root / OTG / WiFi ADB 四个通道的状态归一化为 [ChannelStatus]，
 * 供设备连接页总览条与 adb 命令执行页消费。
 *
 * 复用 connection 模块的状态单例：
 * - [ShizukuPermissionHandler.permissionGranted]
 * - [RootManager.rootState]（新建）
 * - [OtgConnection.state]
 * - [WifiAdbConnection.state] / [WifiAdbConnection.currentDevice]
 */
@Singleton
class ConnectionStatusAggregator @Inject constructor(
    private val shizukuPermissionHandler: ShizukuPermissionHandler,
    private val rootManager: RootManager,
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

    /** 所有通道状态合并。 */
    val allStatuses: Flow<List<ChannelStatus>> = combine(
        shizukuStatus,
        rootStatus,
        otgStatus,
        wifiAdbStatus,
    ) { s, r, o, w -> listOf(s, r, o, w) }
}