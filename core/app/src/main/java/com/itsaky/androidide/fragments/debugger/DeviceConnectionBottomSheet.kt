/*
 * ZeroStudio IDE - 设备连接管理 BottomSheet (重新设计)
 *
 * DeviceConnectionBottomSheet: 支持 4 种 ADB 连接方式的设备连接管理底部弹窗。
 *
 * 4 种连接方式 (跟 debugger/android-adb-shell 参考工程一致):
 *   - 本地 (Local):   Shizuku 桥接 + Root 直连
 *   - WiFi:           WiFi ADB mDNS 发现 (libadb AdbMdns)
 *   - OTG:            USB OTG ADB 设备枚举 (adblib UsbChannel)
 *   - Fastboot:       Fastboot 设备管理 (fastbootlib FastbootDeviceManager)
 *
 * UI/UX 设计 (跟 android-adb-shell 参考工程不同):
 *   - TabRow 顶部 4 个 Tab, 切换不同连接方式
 *   - 每个 Tab 内部用 Card 列表展示可用设备/通道
 *   - 状态指示灯 (彩色圆点) + 状态文字
 *   - 当前活跃连接用边框高亮标记
 *   - 操作按钮内联在每张卡片底部
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DeviceConnectionManager
import com.itsaky.androidide.debugger.connection.RootProbeState
import com.itsaky.androidide.debugger.connection.ShizukuServiceState
import com.itsaky.androidide.debugger.connection.adb.OtgAdbManager
import com.itsaky.androidide.debugger.connection.adb.WifiAdbDiscovery
import com.itsaky.androidide.fragments.sheets.BaseBottomSheetFragment
import android.zero.studio.fastboot.DeviceId
import android.zero.studio.fastboot.FastbootDeviceManager
import kotlinx.coroutines.launch

/**
 * 设备连接管理底部弹窗 (重新设计版, 支持 4 种连接方式)。
 *
 * 使用方式:
 *   DeviceConnectionBottomSheet().show(supportFragmentManager, "device_connection")
 */
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
        // 进入时立即刷新 Shizuku 状态
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

@Composable
private fun WifiTabContent() {
    val context = LocalContext.current
    val discovery = remember { WifiAdbDiscovery(context) }
    val services by discovery.services.collectAsState()
    val isRunning by discovery.isRunning.collectAsState()

    DisposableEffect(Unit) {
        discovery.start()
        onDispose { discovery.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "WiFi ADB mDNS 发现",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusBadge(
                color = if (isRunning) Color(0xFF66BB6A) else Color(0xFF9E9E9E),
                text = if (isRunning) "搜索中" else "已停止",
            )
        }

        if (services.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Wifi,
                title = "未发现 WiFi ADB 服务",
                subtitle = "请确保目标设备已开启无线调试,且与本机处于同一局域网",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((services.size) * 110).coerceAtMost(330).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(services, key = { it.serviceName }) { service ->
                    WifiServiceCard(service)
                }
            }
        }
    }
}

@Composable
private fun WifiServiceCard(service: com.itsaky.androidide.debugger.connection.adb.DiscoveredAdbService) {
    val isPairing = service.serviceType == com.itsaky.androidide.debugger.connection.adb.DiscoveredAdbService.ServiceType.PAIRING
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
                    .background(
                        color = if (isPairing) Color(0xFFFFA726) else Color(0xFF42A5F5),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPairing) Icons.Default.Cable else Icons.Default.Wifi,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = service.serviceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${service.host}:${service.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (isPairing) "需要配对" else "可直接连接",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPairing) Color(0xFFFFA726) else Color(0xFF66BB6A),
                )
            }
            Button(
                onClick = { /* 实际连接逻辑由后续 commit 实现 */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPairing) Color(0xFFFFA726) else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (isPairing) "配对" else "连接")
            }
        }
    }
}

// ====================== OTG Tab ======================

@Composable
private fun OtgTabContent() {
    val context = LocalContext.current
    val manager = remember { OtgAdbManager(context) }
    val devices by manager.devices.collectAsState()
    val isRunning by manager.isRunning.collectAsState()

    DisposableEffect(Unit) {
        manager.start()
        onDispose { manager.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
            OutlinedButton(onClick = { manager.refreshDeviceList() }) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("刷新")
            }
        }

        if (devices.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Usb,
                title = "未发现 USB 设备",
                subtitle = "请通过 OTG 线连接 Android 设备,并确保设备已开启 USB 调试",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((devices.size * 110).coerceAtMost(330).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(devices, key = { it.deviceName }) { device ->
                    OtgDeviceCard(
                        device = device,
                        hasPermission = manager.hasPermission(device.deviceName),
                        onRequestPermission = { manager.requestPermission(device.deviceName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OtgDeviceCard(
    device: com.itsaky.androidide.debugger.connection.adb.OtgAdbDevice,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
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
                    .background(
                        color = if (device.hasAdbInterface) Color(0xFF66BB6A) else Color(0xFF9E9E9E),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "USB #${device.vendorId}:${device.productId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (device.hasAdbInterface) "支持 ADB 协议" else "非 ADB 设备",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (device.hasAdbInterface) Color(0xFF66BB6A) else Color(0xFFEF5350),
                )
            }
            if (device.hasAdbInterface) {
                if (hasPermission) {
                    Button(
                        onClick = { /* 实际连接逻辑由后续 commit 实现 */ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Default.Cable, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("连接")
                    }
                } else {
                    OutlinedButton(onClick = onRequestPermission) {
                        Text("授权")
                    }
                }
            }
        }
    }
}

// ====================== Fastboot Tab ======================

@Composable
private fun FastbootTabContent() {
    val context = LocalContext.current
    val fastbootManager = remember { FastbootDeviceManager }
    var attachedDevices by remember { mutableStateOf<List<DeviceId>>(emptyList()) }
    var connectedDevices by remember { mutableStateOf<List<DeviceId>>(emptyList()) }

    DisposableEffect(Unit) {
        fastbootManager.registerReceivers(context)
        attachedDevices = fastbootManager.getAttachedDeviceIds(context)
        connectedDevices = fastbootManager.getConnectedDeviceIds()
        onDispose {
            fastbootManager.unregisterReceivers(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
            OutlinedButton(onClick = {
                attachedDevices = fastbootManager.getAttachedDeviceIds(context)
                connectedDevices = fastbootManager.getConnectedDeviceIds()
            }) {
                Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("刷新")
            }
        }

        if (attachedDevices.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Build,
                title = "未发现 Fastboot 设备",
                subtitle = "请将设备重启到 Bootloader/Fastboot 模式后通过 USB 连接",
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((attachedDevices.size * 110).coerceAtMost(330).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(attachedDevices, key = { it.id }) { deviceId ->
                    val isConnected = connectedDevices.any { it.id == deviceId.id }
                    FastbootDeviceCard(
                        deviceId = deviceId,
                        isConnected = isConnected,
                        onConnect = { fastbootManager.connectToDevice(context, deviceId) },
                        onDisconnect = { fastbootManager.disconnectDevice(deviceId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FastbootDeviceCard(
    deviceId: DeviceId,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
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
                    .background(
                        color = if (isConnected) Color(0xFF66BB6A) else Color(0xFFFFA726),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Fastboot 设备",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = deviceId.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (isConnected) "已连接" else "未连接",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isConnected) Color(0xFF66BB6A) else Color(0xFFFFA726),
                )
            }
            if (isConnected) {
                OutlinedButton(onClick = onDisconnect) {
                    Text("断开")
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.Cable, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("连接")
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
