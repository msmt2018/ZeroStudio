/*
 * ZeroStudio IDE - 设备连接管理 BottomSheet (重新设计)
 *
 * DeviceConnectionBottomSheet: 支持 4 种 ADB 连接方式的设备连接管理底部弹窗。
 *
 * 4 种连接方式 (跟 debugger/android-adb-shell 参考工程一致):
 *   - 本地 (Local):   Shizuku 桥接 + Root 直连
 *   - WiFi:           WiFi ADB mDNS 发现 + QR 配对 + 配对码 (WifiAdbViewModel)
 *   - OTG:            USB OTG ADB 设备枚举 (OtgViewModel)
 *   - Fastboot:       Fastboot 设备管理 (FastbootViewModel)
 *
 * UI/UX 设计 (跟 android-adb-shell 参考工程不同):
 *   - TabRow 顶部 4 个 Tab, 切换不同连接方式
 *   - 每个 Tab 内部用 Card 列表展示可用设备/通道
 *   - 状态指示灯 (彩色圆点) + 状态文字
 *   - 当前活跃连接用边框高亮标记
 *   - 操作按钮内联在每张卡片底部
 *
 * WiFi/OTG/Fastboot 三个 Tab 使用 connection 模块中从 android-adb-shell 复刻的
 * Hilt ViewModels (WifiAdbViewModel / OtgViewModel / FastbootViewModel),
 * 直接复用参考工程的 AdbConnectionManager / Repositories / mDNS / USB 监听等完整逻辑。
 *
 * 使用方式:
 *   DeviceConnectionBottomSheet().show(supportFragmentManager, "device_connection")
 */

package com.itsaky.androidide.fragments.debugger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DeviceConnectionManager
import com.itsaky.androidide.debugger.connection.RootProbeState
import com.itsaky.androidide.debugger.connection.ShizukuServiceState
import com.itsaky.androidide.fragments.sheets.BaseBottomSheetFragment
import android.zero.studio.shell.fastboot.domain.model.FastbootDeviceInfo
import android.zero.studio.shell.fastboot.domain.model.FastbootState
import android.zero.studio.shell.fastboot.presentation.viewmodel.FastbootViewModel
import android.zero.studio.shell.otg_adb_shell.domain.model.OtgState
import android.zero.studio.shell.otg_adb_shell.presentation.viewmodel.OtgViewModel
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbDevice
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbState
import android.zero.studio.shell.wifi_adb_shell.presentation.viewmodel.WifiAdbViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 设备连接管理底部弹窗 (重新设计版, 支持 4 种连接方式)。
 *
 * WiFi/OTG/Fastboot 三个 Tab 使用 connection 模块中复刻自 android-adb-shell 的 Hilt
 * ViewModels, 直接复用参考工程的 AdbConnectionManager / Repositories / mDNS 发现 /
 * USB 监听 / Fastboot 协议等完整连接逻辑, 仅 UI 层重新设计。
 *
 * 使用方式:
 *   DeviceConnectionBottomSheet().show(supportFragmentManager, "device_connection")
 */
@AndroidEntryPoint
class DeviceConnectionBottomSheet : BaseBottomSheetFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    DeviceConnectionScreen(onDismiss = { dismiss() })
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 进入时立即刷新 Shizuku 状态 (Local Tab)
        DeviceConnectionManager.refreshShizukuState()
    }
}

// ---- 4 个 Tab 定义 ----

private enum class ConnectionTab(val label: String, val icon: ImageVector) {
    Local("本地", Icons.Default.Router),
    Wifi("WiFi", Icons.Default.Wifi),
    Otg("OTG", Icons.Default.Usb),
    Fastboot("Fastboot", Icons.Default.Build),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceConnectionScreen(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf(ConnectionTab.Local) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            // ---- 标题栏 ----
            HeaderRow(onDismiss = onDismiss)

            // ---- Tab 选择栏 ----
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                ConnectionTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- Tab 内容区 ----
            when (selectedTab) {
                ConnectionTab.Local -> LocalTabContent()
                ConnectionTab.Wifi -> WifiTabContent()
                ConnectionTab.Otg -> OtgTabContent()
                ConnectionTab.Fastboot -> FastbootTabContent()
            }
        }
    }
}

// ---- 标题栏 ----

@Composable
private fun HeaderRow(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "设备连接管理",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Local · WiFi · OTG · Fastboot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ====================== Local Tab ======================

@Composable
private fun LocalTabContent() {
    val scope = rememberCoroutineScope()
    val shizukuState by DeviceConnectionManager.shizukuState.collectAsState()
    val rootState by DeviceConnectionManager.rootState.collectAsState()
    var activeType by remember { mutableStateOf(DeviceConnectionManager.activeType) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 当前活跃连接卡片
        ActiveConnectionCard(activeType = activeType)

        // Shizuku 通道
        ConnectionChannelCard(
            title = "Shizuku 桥接",
            subtitle = "通过 Shizuku 服务以 ADB/Shell 权限执行",
            statusColor = shizukuStatusColor(shizukuState),
            statusText = shizukuStatusText(shizukuState),
            detailText = shizukuDetailText(shizukuState),
            isActive = activeType == ConnectionType.Shizuku,
            actions = {
                OutlinedButton(onClick = { DeviceConnectionManager.refreshShizukuState() }) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("刷新")
                }
                if (shizukuState == ShizukuServiceState.RunningUnauthorized) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { DeviceConnectionManager.requestShizukuPermission() }) {
                        Text("请求授权")
                    }
                }
                if (shizukuState == ShizukuServiceState.RunningAuthorized) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            DeviceConnectionManager.activeType = ConnectionType.Shizuku
                            activeType = ConnectionType.Shizuku
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("设为活跃")
                    }
                }
            },
        )

        // Root 通道
        ConnectionChannelCard(
            title = "Root 直连",
            subtitle = "通过 su 以 Superuser 权限直接执行",
            statusColor = rootStatusColor(rootState),
            statusText = rootStatusText(rootState),
            detailText = rootDetailText(rootState),
            isActive = activeType == ConnectionType.Root,
            actions = {
                OutlinedButton(
                    onClick = { scope.launch { DeviceConnectionManager.probeRoot() } },
                    enabled = rootState != RootProbeState.Probing,
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (rootState == RootProbeState.Probing) "检测中..." else "检测")
                }
                if (rootState == RootProbeState.Available) {
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            DeviceConnectionManager.activeType = ConnectionType.Root
                            activeType = ConnectionType.Root
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("设为活跃")
                    }
                }
            },
        )

        Text(
            text = "Shizuku 需 Shizuku app 已启动并授权; Root 需设备已 root。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ====================== WiFi Tab ======================
// 使用 connection 模块中的 WifiAdbViewModel, 直接复刻 android-adb-shell 的:
//   - mDNS 发现 (_adb-tls-connect._tcp / _adb-tls-pairing._tcp)
//   - QR 码配对 (由 WifiAdbRepository.generatePairingQR 生成)
//   - 6 位配对码配对 (pairWithCode)
//   - TLS 连接 + 心跳保活 + 自动重连
//   - Room 持久化已保存设备

@Composable
private fun WifiTabContent() {
    val viewModel: WifiAdbViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    val currentDevice by viewModel.currentDevice.collectAsState()
    val discoveredPairingServices by viewModel.discoveredPairingServices.collectAsState()

    // 配对码输入 (6 位)
    var pairingCode by remember { mutableStateOf("") }

    // 进入 Tab 时启动配对服务发现, 离开时停止
    DisposableEffect(Unit) {
        viewModel.startCodePairingDiscovery()
        onDispose {
            viewModel.stopCodePairingDiscovery()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---- 标题 + 当前状态 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "WiFi ADB 无线连接",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusBadge(
                color = wifiStateColor(state),
                text = wifiStateText(state),
            )
        }

        // ---- 当前连接设备卡片 ----
        if (state is WifiAdbState.Connected && currentDevice != null) {
            CurrentWifiDeviceCard(
                device = currentDevice!!,
                onDisconnect = { viewModel.disconnect() },
            )
        }

        // ---- 加载中提示 ----
        if (state.isLoading) {
            LoadingRow(text = wifiLoadingText(state))
        }

        // ---- 已保存设备列表 ----
        if (savedDevices.isNotEmpty()) {
            Text(
                text = "已保存设备 (${savedDevices.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(savedDevices, key = { it.id }) { device ->
                    WifiSavedDeviceCard(
                        device = device,
                        isCurrent = currentDevice?.id == device.id,
                        onReconnect = { viewModel.reconnectToDevice(device) },
                        onForget = { viewModel.forgetDevice(device) },
                    )
                }
            }
        }

        // ---- 配对码配对区 ----
        Text(
            text = "使用 6 位配对码配对新设备",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )

        OutlinedTextField(
            value = pairingCode,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pairingCode = it },
            label = { Text("配对码 (6 位数字)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )

        if (discoveredPairingServices.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Wifi,
                title = "未发现可配对设备",
                subtitle = "请确保目标设备已开启无线调试 > 配对,且与本机处于同一局域网",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(discoveredPairingServices, key = { it.key }) { service ->
                    WifiPairingServiceCard(
                        name = service.serviceName,
                        address = "${service.ip}:${service.port}",
                        canPair = pairingCode.length == 6,
                        onPair = {
                            viewModel.pairWithCode(service, pairingCode)
                            pairingCode = ""
                        },
                    )
                }
            }
        }

        Text(
            text = "已保存设备可一键重连; 新设备需先配对 (6 位码), 配对成功后会自动连接并保存。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrentWifiDeviceCard(
    device: WifiAdbDevice,
    onDisconnect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = Color(0xFF66BB6A), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "${device.ip}:${device.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "已连接",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                )
            }
            OutlinedButton(onClick = onDisconnect) {
                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("断开")
            }
        }
    }
}

@Composable
private fun WifiSavedDeviceCard(
    device: WifiAdbDevice,
    isCurrent: Boolean,
    onReconnect: () -> Unit,
    onForget: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isCurrent) 2.dp else 0.dp,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (device.isPaired) Color(0xFF42A5F5) else Color(0xFFFFA726),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${device.ip}:${device.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (device.isPaired) "已配对" else "未配对",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (device.isPaired) Color(0xFF66BB6A) else Color(0xFFFFA726),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (!isCurrent) {
                    Button(
                        onClick = onReconnect,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 6.dp,
                        ),
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重连")
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onForget,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 6.dp,
                    ),
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("忘记")
                }
            }
        }
    }
}

@Composable
private fun WifiPairingServiceCard(
    name: String,
    address: String,
    canPair: Boolean,
    onPair: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = Color(0xFFFFA726), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Cable,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "等待配对",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFA726),
                )
            }
            Button(
                onClick = onPair,
                enabled = canPair,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFA726),
                ),
            ) {
                Text("配对")
            }
        }
    }
}

// ====================== OTG Tab ======================
// 使用 connection 模块中的 OtgViewModel, 直接复刻 android-adb-shell 的:
//   - UsbManager 设备枚举 + ADB 接口识别
//   - USB 权限请求 + UsbDeviceConnection
//   - adblib AdbConnection 建立
//   - reboot:bootloader 服务

@Composable
private fun OtgTabContent() {
    val viewModel: OtgViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // 进入 Tab 时启动 USB 设备扫描
    DisposableEffect(Unit) {
        viewModel.startScan()
        onDispose {
            viewModel.unRegister()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---- 标题 + 当前状态 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "USB OTG ADB 设备",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusBadge(
                color = otgStateColor(state),
                text = otgStateText(state),
            )
        }

        // ---- 状态详情卡片 ----
        OtgStatusCard(
            state = state,
            onScan = { viewModel.startScan() },
            onDisconnect = { viewModel.disconnect() },
            onRebootBootloader = { viewModel.rebootToBootloader() },
        )

        Text(
            text = "通过 OTG 线连接 Android 设备, 目标设备需开启 USB 调试。连接后可执行 ADB 命令, 或重启到 Fastboot 模式。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OtgStatusCard(
    state: OtgState,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
    onRebootBootloader: () -> Unit,
) {
    val isConnected = state is OtgState.Connected
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isConnected) 2.dp else 0.dp,
                color = if (isConnected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = otgStateColor(state),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state is OtgState.Searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state) {
                            is OtgState.Connected -> "已连接: ${state.deviceName}"
                            is OtgState.DeviceFound -> "发现设备: ${state.deviceName}"
                            is OtgState.Searching -> "正在搜索 USB 设备..."
                            is OtgState.Connecting -> "正在建立 ADB 连接..."
                            is OtgState.PermissionDenied -> "USB 权限被拒绝"
                            is OtgState.Disconnected -> "已断开连接"
                            is OtgState.UsbManagerUnavailable -> "UsbManager 不可用"
                            is OtgState.Error -> "错误: ${state.message}"
                            is OtgState.Idle -> "空闲"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = otgDetailText(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isConnected) {
                    OutlinedButton(onClick = onRebootBootloader) {
                        Icon(Icons.Default.Build, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重启到 Bootloader")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("断开")
                    }
                } else {
                    Button(onClick = onScan) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重新扫描")
                    }
                }
            }
        }
    }
}

// ====================== Fastboot Tab ======================
// 使用 connection 模块中的 FastbootViewModel, 直接复刻 android-adb-shell 的:
//   - UsbManager Fastboot 设备枚举 (接口 0xFF)
//   - fastbootlib FastbootDevice 协议通信
//   - getvar / reboot 等命令
//   - 设备信息读取 (product/serial/unlocked/slot/battery...)

@Composable
private fun FastbootTabContent() {
    val viewModel: FastbootViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val variables by viewModel.variables.collectAsState()
    val isLoadingInfo by viewModel.isLoadingDeviceInfo.collectAsState()
    val isLoadingVars by viewModel.isLoadingVariables.collectAsState()

    // 进入 Tab 时启动 Fastboot 设备扫描
    DisposableEffect(Unit) {
        viewModel.startScan()
        onDispose {
            viewModel.unRegister()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---- 标题 + 当前状态 ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Fastboot 设备",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusBadge(
                color = fastbootStateColor(state),
                text = fastbootStateText(state),
            )
        }

        // ---- 状态详情卡片 ----
        FastbootStatusCard(
            state = state,
            onScan = { viewModel.startScan() },
            onDisconnect = { viewModel.disconnect() },
        )

        // ---- 设备信息卡片 (已连接时显示) ----
        if (state is FastbootState.Connected) {
            FastbootDeviceInfoCard(
                deviceInfo = deviceInfo,
                isLoading = isLoadingInfo,
                onLoadInfo = { viewModel.loadDeviceInfo() },
            )

            // ---- 变量列表 ----
            FastbootVariablesCard(
                variables = variables,
                isLoading = isLoadingVars,
                onLoadAll = { viewModel.loadAllVariables() },
            )
        }

        Text(
            text = "请将设备重启到 Bootloader/Fastboot 模式后通过 USB 连接。连接后可读取设备信息、执行 Fastboot 命令。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FastbootStatusCard(
    state: FastbootState,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isConnected = state is FastbootState.Connected
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isConnected) 2.dp else 0.dp,
                color = if (isConnected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = fastbootStateColor(state),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state is FastbootState.Searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state) {
                            is FastbootState.Connected -> "已连接: ${state.deviceName}"
                            is FastbootState.DeviceFound -> "发现设备: ${state.deviceName}"
                            is FastbootState.Searching -> "正在搜索 Fastboot 设备..."
                            is FastbootState.Connecting -> "正在建立 Fastboot 连接..."
                            is FastbootState.PermissionDenied -> "USB 权限被拒绝"
                            is FastbootState.Disconnected -> "已断开连接"
                            is FastbootState.UsbManagerUnavailable -> "UsbManager 不可用"
                            is FastbootState.Error -> "错误: ${state.message}"
                            is FastbootState.Idle -> "空闲"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = fastbootDetailText(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isConnected) {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("断开")
                    }
                } else {
                    Button(onClick = onScan) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重新扫描")
                    }
                }
            }
        }
    }
}

@Composable
private fun FastbootDeviceInfoCard(
    deviceInfo: FastbootDeviceInfo?,
    isLoading: Boolean,
    onLoadInfo: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "设备信息",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onLoadInfo,
                    enabled = !isLoading,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("读取")
                }
            }

            if (deviceInfo == null) {
                Text(
                    text = "尚未读取设备信息, 点击「读取」按钮获取",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FastbootInfoRow("Product", deviceInfo.product)
                FastbootInfoRow("Serial No.", deviceInfo.serialNo)
                FastbootInfoRow("Variant", deviceInfo.variant)
                FastbootInfoRow("Bootloader", deviceInfo.bootloaderVersion)
                FastbootInfoRow("Baseband", deviceInfo.basebandVersion)
                FastbootInfoRow("Unlocked", deviceInfo.isUnlocked?.let { if (it) "是" else "否" })
                FastbootInfoRow("Current Slot", deviceInfo.currentSlot)
                FastbootInfoRow("Battery", deviceInfo.batteryLevel?.let { "$it%" })
                FastbootInfoRow("Security Patch", deviceInfo.securityPatchLevel)
                FastbootInfoRow("Max Download", deviceInfo.maxDownloadSize)
            }
        }
    }
}

@Composable
private fun FastbootInfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FastbootVariablesCard(
    variables: List<Pair<String, String>>,
    isLoading: Boolean,
    onLoadAll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Fastboot 变量 (${variables.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onLoadAll,
                    enabled = !isLoading,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("读取全部")
                }
            }

            if (variables.isEmpty()) {
                Text(
                    text = "尚未读取变量, 点击「读取全部」按钮获取",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                variables.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ====================== 通用组件 ======================

@Composable
private fun ActiveConnectionCard(activeType: ConnectionType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "当前活跃连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = activeType.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ConnectionChannelCard(
    title: String,
    subtitle: String,
    statusColor: Color,
    statusText: String,
    detailText: String,
    isActive: Boolean,
    actions: @Composable () -> Unit,
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(color = statusColor, text = statusText)
            }
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun StatusBadge(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Compose 中无 TextButton 时使用简化版 ----
@Composable
private fun TextButton(
    onClick: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(onClick = onClick, contentPadding = contentPadding, content = content)
}

// ---- Shizuku 状态映射 ----

private fun shizukuStatusColor(state: ShizukuServiceState): Color = when (state) {
    ShizukuServiceState.NotRunning -> Color(0xFF9E9E9E)
    ShizukuServiceState.RunningUnauthorized -> Color(0xFFFFA726)
    ShizukuServiceState.RunningAuthorized -> Color(0xFF66BB6A)
}

private fun shizukuStatusText(state: ShizukuServiceState): String = when (state) {
    ShizukuServiceState.NotRunning -> "未运行"
    ShizukuServiceState.RunningUnauthorized -> "未授权"
    ShizukuServiceState.RunningAuthorized -> "就绪"
}

private fun shizukuDetailText(state: ShizukuServiceState): String = when (state) {
    ShizukuServiceState.NotRunning -> "Shizuku 服务未启动,请先打开 Shizuku app 并启动服务"
    ShizukuServiceState.RunningUnauthorized -> "Shizuku 服务已运行,但本应用尚未获得授权"
    ShizukuServiceState.RunningAuthorized -> "Shizuku 服务已运行且已授权,可以使用"
}

// ---- Root 状态映射 ----

private fun rootStatusColor(state: RootProbeState): Color = when (state) {
    RootProbeState.NotProbed -> Color(0xFF9E9E9E)
    RootProbeState.Probing -> Color(0xFFFFA726)
    RootProbeState.Available -> Color(0xFF66BB6A)
    RootProbeState.Unavailable -> Color(0xFFEF5350)
}

private fun rootStatusText(state: RootProbeState): String = when (state) {
    RootProbeState.NotProbed -> "未检测"
    RootProbeState.Probing -> "检测中"
    RootProbeState.Available -> "就绪"
    RootProbeState.Unavailable -> "不可用"
}

private fun rootDetailText(state: RootProbeState): String = when (state) {
    RootProbeState.NotProbed -> "尚未检测 Root 状态,点击「检测」按钮"
    RootProbeState.Probing -> "正在尝试执行 su..."
    RootProbeState.Available -> "设备已 Root,su 可用"
    RootProbeState.Unavailable -> "设备未 Root 或 su 被拒绝"
}

// ---- WiFi ADB 状态映射 ----

private fun wifiStateColor(state: WifiAdbState): Color = when (state) {
    is WifiAdbState.Connected -> Color(0xFF66BB6A)
    is WifiAdbState.Connecting, is WifiAdbState.Reconnecting,
    is WifiAdbState.Pairing, is WifiAdbState.Discovering -> Color(0xFFFFA726)
    is WifiAdbState.Disconnected -> Color(0xFF9E9E9E)
    is WifiAdbState.Idle -> Color(0xFF9E9E9E)
}

private fun wifiStateText(state: WifiAdbState): String = when (state) {
    is WifiAdbState.Connected -> "已连接"
    is WifiAdbState.Connecting -> "连接中"
    is WifiAdbState.Reconnecting -> "重连中"
    is WifiAdbState.Pairing -> "配对中"
    is WifiAdbState.Discovering -> "发现中"
    is WifiAdbState.Disconnected -> "已断开"
    is WifiAdbState.Idle -> "空闲"
}

private fun wifiLoadingText(state: WifiAdbState): String = when (state) {
    is WifiAdbState.Connecting -> "正在建立 TLS 连接..."
    is WifiAdbState.Reconnecting -> "正在重新连接..."
    is WifiAdbState.Pairing -> "正在配对 (Spake2)..."
    is WifiAdbState.Discovering -> "正在 mDNS 发现..."
    else -> ""
}

// ---- OTG 状态映射 ----

private fun otgStateColor(state: OtgState): Color = when (state) {
    is OtgState.Connected -> Color(0xFF66BB6A)
    is OtgState.DeviceFound -> Color(0xFF42A5F5)
    is OtgState.Searching, is OtgState.Connecting -> Color(0xFFFFA726)
    is OtgState.PermissionDenied -> Color(0xFFEF5350)
    is OtgState.Disconnected, is OtgState.Idle -> Color(0xFF9E9E9E)
    is OtgState.UsbManagerUnavailable, is OtgState.Error -> Color(0xFFEF5350)
}

private fun otgStateText(state: OtgState): String = when (state) {
    is OtgState.Connected -> "已连接"
    is OtgState.DeviceFound -> "已发现"
    is OtgState.Searching -> "搜索中"
    is OtgState.Connecting -> "连接中"
    is OtgState.PermissionDenied -> "权限拒绝"
    is OtgState.Disconnected -> "已断开"
    is OtgState.Idle -> "空闲"
    is OtgState.UsbManagerUnavailable -> "不可用"
    is OtgState.Error -> "错误"
}

private fun otgDetailText(state: OtgState): String = when (state) {
    is OtgState.Connected -> "ADB 通道已建立, 可执行命令"
    is OtgState.DeviceFound -> "检测到 USB ADB 设备, 准备连接"
    is OtgState.Searching -> "正在监听 USB 设备接入..."
    is OtgState.Connecting -> "正在协商 ADB 协议..."
    is OtgState.PermissionDenied -> "用户拒绝了 USB 权限, 请重新扫描并授权"
    is OtgState.Disconnected -> "设备已断开"
    is OtgState.Idle -> "尚未开始扫描"
    is OtgState.UsbManagerUnavailable -> "系统未提供 UsbManager"
    is OtgState.Error -> state.message
}

// ---- Fastboot 状态映射 ----

private fun fastbootStateColor(state: FastbootState): Color = when (state) {
    is FastbootState.Connected -> Color(0xFF66BB6A)
    is FastbootState.DeviceFound -> Color(0xFF42A5F5)
    is FastbootState.Searching, is FastbootState.Connecting -> Color(0xFFFFA726)
    is FastbootState.PermissionDenied -> Color(0xFFEF5350)
    is FastbootState.Disconnected, is FastbootState.Idle -> Color(0xFF9E9E9E)
    is FastbootState.UsbManagerUnavailable, is FastbootState.Error -> Color(0xFFEF5350)
}

private fun fastbootStateText(state: FastbootState): String = when (state) {
    is FastbootState.Connected -> "已连接"
    is FastbootState.DeviceFound -> "已发现"
    is FastbootState.Searching -> "搜索中"
    is FastbootState.Connecting -> "连接中"
    is FastbootState.PermissionDenied -> "权限拒绝"
    is FastbootState.Disconnected -> "已断开"
    is FastbootState.Idle -> "空闲"
    is FastbootState.UsbManagerUnavailable -> "不可用"
    is FastbootState.Error -> "错误"
}

private fun fastbootDetailText(state: FastbootState): String = when (state) {
    is FastbootState.Connected -> "Fastboot 通道已建立, 可读取设备信息/执行命令"
    is FastbootState.DeviceFound -> "检测到 Fastboot 设备, 准备连接"
    is FastbootState.Searching -> "正在监听 Fastboot 设备接入..."
    is FastbootState.Connecting -> "正在协商 Fastboot 协议..."
    is FastbootState.PermissionDenied -> "用户拒绝了 USB 权限, 请重新扫描并授权"
    is FastbootState.Disconnected -> "设备已断开"
    is FastbootState.Idle -> "尚未开始扫描"
    is FastbootState.UsbManagerUnavailable -> "系统未提供 UsbManager"
    is FastbootState.Error -> state.message
}
