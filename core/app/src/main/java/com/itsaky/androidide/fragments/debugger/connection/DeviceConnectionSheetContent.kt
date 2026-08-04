package com.itsaky.androidide.fragments.debugger.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itsaky.androidide.debugger.root.RootManagerType
import com.itsaky.androidide.ui.theme.deviceconnection.DcChannel
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * 设备连接页 Sheet 内容。组合状态总览条 + 三张卡片 + ADB 命令执行入口。
 *
 * 由 [com.itsaky.androidide.fragments.debugger.DeviceConnectionBottomSheet] 承载，
 * 通过回调与外部 Fragment 协作跳转全屏子页面。
 */
@Composable
fun DeviceConnectionSheetContent(
    viewModel: DeviceConnectionViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onNavigateToPairOwn: () -> Unit,
    onNavigateToPairOther: () -> Unit,
    onNavigateToAdbConsole: () -> Unit,
) {
    val c = deviceConnectionColors
    val statuses by viewModel.allStatuses.collectAsState()
    val rootState by viewModel.rootState.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val rootDevices by viewModel.rootDevices.collectAsState()

    var showPairMenu by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showRootPicker by remember { mutableStateOf(false) }
    var showRootAdbDevices by remember { mutableStateOf(false) }
    var showOtgWaiting by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = c.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("设备连接", color = c.textPrimary, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭", tint = c.textSecondary)
                    }
                }
            }
            item {
                StatusOverviewBar(
                    statuses = statuses,
                    refreshing = refreshing,
                    onRefresh = viewModel::refreshAll,
                )
            }
            item {
                WirelessAdbCard(
                    status = statuses.firstOrNull { it.channel == DcChannel.WIFI_ADB },
                    onGuide = { showGuide = true },
                    onPairMenu = { showPairMenu = true },
                    onStart = {
                        // TODO: 调用 viewModel 启动 WiFi ADB 连接（连接到 currentDevice）
                    },
                )
            }
            item {
                RootCard(
                    status = statuses.firstOrNull { it.channel == DcChannel.ROOT },
                    rootState = rootState,
                    onRequestPermission = viewModel::probeRoot,
                    onPickManager = { showRootPicker = true },
                    onOpenAdbDevices = {
                        viewModel.connectLocal()
                        showRootAdbDevices = true
                    },
                )
            }
            item {
                OtgCard(
                    status = statuses.firstOrNull { it.channel == DcChannel.OTG },
                    onWaitDevice = { showOtgWaiting = true },
                    onManageDevice = {
                        // TODO: 打开 OTG 设备管理（当前复用 RootAdbDeviceSheet 的 USB 分组即可）
                        showRootAdbDevices = true
                    },
                )
            }
            item {
                // ADB 命令执行入口
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = c.surfaceHighlight,
                    onClick = onNavigateToAdbConsole,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Terminal, null, tint = c.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "ADB 命令执行",
                            color = c.textPrimary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ChevronRight, null, tint = c.textSecondary)
                    }
                }
            }
        }
    }

    // 弹窗
    if (showPairMenu) {
        PairModeChooseSheet(
            onDismiss = { showPairMenu = false },
            onPairOwn = onNavigateToPairOwn,
            onPairOther = onNavigateToPairOther,
        )
    }
    if (showGuide) {
        GuideSheet(onDismiss = { showGuide = false })
    }
    if (showRootPicker) {
        // 简化：所有管理器都标记为「可尝试」
        RootManagerPickerSheet(
            availableManagers = RootManagerType.values().toSet(),
            onPick = { viewModel.probeRoot() },
            onDismiss = { showRootPicker = false },
        )
    }
    if (showRootAdbDevices) {
        RootAdbDeviceSheet(
            devices = rootDevices,
            onConnectWifi = { ip, port -> viewModel.connectWifiDevice(ip, port) {} },
            onDisconnectWifi = viewModel::disconnectWifiDevice,
            onSetActive = viewModel::setActiveDevice,
            onRefresh = { /* TODO: refreshDevices */ },
            onDismiss = { showRootAdbDevices = false },
        )
    }
    if (showOtgWaiting) {
        OtgWaitingSheet(
            message = "请插入 USB 设备...",
            onDismiss = { showOtgWaiting = false },
        )
    }
}