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
 * 注意: 本 Fragment 不使用 @AndroidEntryPoint 注解。
 *       宿主 EditorActivityKt 未标注 @AndroidEntryPoint, 若本 Fragment 标注会触发
 *       Hilt "Fragment host component is missing" 异常并导致崩溃。
 *       WiFi/OTG/Fastboot 三个 Tab 通过 hiltViewModel() 复用 connection 模块的
 *       Hilt ViewModels (依赖宿主 Activity 已配置 Hilt 入口)。
 *
 * UI/UX 设计 (跟 android-adb-shell 参考工程不同):
 *   - TabRow 顶部 4 个 Tab, 切换不同连接方式
 *   - 每个 Tab 内部用 Card 列表展示可用设备/通道
 *   - 状态指示灯 (彩色圆点) + 状态文字
 *   - 当前活跃连接用边框高亮标记
 *   - 操作按钮内联在每张卡片底部
 *   - WiFi Tab 内部含 3 个子 Tab (QR 配对 / 配对码配对 / 已保存设备)
 *   - Fastboot Tab 含活跃槽位、解锁状态、快速工具、Flash 操作状态、命令控制台
 *
 * WiFi/OTG/Fastboot 三个 Tab 使用 connection 模块中从 android-adb-shell 复刻的
 * Hilt ViewModels (WifiAdbViewModel / OtgViewModel / FastbootViewModel),
 * 直接复用参考工程的 AdbConnectionManager / Repositories / mDNS / USB 监听等完整逻辑。
 *
 * 使用方式:
 *   DeviceConnectionBottomSheet().show(supportFragmentManager, "device_connection")
 */

package com.itsaky.androidide.fragments.debugger

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DeviceConnectionManager
import com.itsaky.androidide.debugger.connection.RootProbeState
import com.itsaky.androidide.debugger.connection.ShizukuServiceState
import com.itsaky.androidide.fragments.sheets.BaseBottomSheetFragment
import android.zero.studio.shell.fastboot.domain.model.FastbootCommandResult
import android.zero.studio.shell.fastboot.domain.model.FastbootDeviceInfo
import android.zero.studio.shell.fastboot.domain.model.FastbootState
import android.zero.studio.shell.fastboot.domain.model.FlashOperation
import android.zero.studio.shell.fastboot.domain.model.FlashStatus
import android.zero.studio.shell.fastboot.domain.model.RebootMode
import android.zero.studio.shell.fastboot.presentation.viewmodel.FastbootViewModel
import android.zero.studio.shell.otg_adb_shell.domain.model.OtgState
import android.zero.studio.shell.otg_adb_shell.presentation.viewmodel.OtgViewModel
import android.zero.studio.shell.wifi_adb_shell.domain.model.DiscoveredPairingService
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbDevice
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbState
import android.zero.studio.shell.wifi_adb_shell.presentation.viewmodel.WifiAdbViewModel
import android.zero.studio.fastboot.ResponseStatus
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * 设备连接管理底部弹窗 (重新设计版, 支持 4 种连接方式)。
 *
 * WiFi/OTG/Fastboot 三个 Tab 使用 connection 模块中复刻自 android-adb-shell 的 Hilt
 * ViewModels, 直接复用参考工程的 AdbConnectionManager / Repositories / mDNS 发现 /
 * USB 监听 / Fastboot 协议等完整连接逻辑, 仅 UI 层重新设计。
 *
 * 注意: 本 Fragment 不标注 @AndroidEntryPoint (宿主 EditorActivityKt 未标注,
 * 标注会导致 Hilt 崩溃), 三个 Tab 通过 hiltViewModel() 复用 Hilt ViewModels。
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

// ---- WiFi Tab 内部子 Tab 定义 (跟 android-adb-shell PairingOtherDeviceScreen 一致) ----

private enum class WifiSubTab(val label: String) {
    QrPair("QR 配对"),
    CodePair("配对码配对"),
    Saved("已保存设备"),
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
//
// WiFi Tab 内部含 3 个子 Tab (跟 android-adb-shell PairingOtherDeviceScreen 一致):
//   - QR 配对:    生成 6 位随机配对码 -> generateQr -> startQrPairDiscovery -> 显示 Bitmap
//   - 配对码配对: 6 位码输入 + discoveredPairingServices 列表
//   - 已保存设备: savedDevices 列表 + 重连/忘记

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiTabContent() {
    val viewModel: WifiAdbViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    val currentDevice by viewModel.currentDevice.collectAsState()
    val discoveredPairingServices by viewModel.discoveredPairingServices.collectAsState()
    val qrBitmap by viewModel.qrBitmap.collectAsState()

    var selectedSubTab by remember { mutableStateOf(WifiSubTab.QrPair) }

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

        // ---- 子 Tab 选择栏 (SecondaryTabRow, 跟 android-adb-shell 一致) ----
        TabRow(selectedTabIndex = selectedSubTab.ordinal) {
            WifiSubTab.values().forEach { subTab ->
                Tab(
                    selected = selectedSubTab == subTab,
                    onClick = { selectedSubTab = subTab },
                    text = { Text(subTab.label) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ---- 子 Tab 内容区 ----
        when (selectedSubTab) {
            WifiSubTab.QrPair -> QrPairSubTab(
                viewModel = viewModel,
                qrBitmap = qrBitmap,
                state = state,
            )
            WifiSubTab.CodePair -> CodePairSubTab(
                viewModel = viewModel,
                discoveredPairingServices = discoveredPairingServices,
            )
            WifiSubTab.Saved -> SavedDevicesSubTab(
                viewModel = viewModel,
                savedDevices = savedDevices,
                currentDevice = currentDevice,
            )
        }
    }
}

// ---- WiFi: QR 配对子 Tab ----
// 跟 android-adb-shell QRPairTab 一致: 生成 6 位随机配对码 -> generateQr 生成 Bitmap ->
// startQrPairDiscovery 启动 mDNS 发现 -> 显示 QR Bitmap -> 离开时 stopQrPairDiscovery

@Composable
private fun QrPairSubTab(
    viewModel: WifiAdbViewModel,
    qrBitmap: Bitmap?,
    state: WifiAdbState,
) {
    val sessionId = remember { UUID.randomUUID().toString() }
    val pairingCode = remember { generatePairingCode() }

    // 进入子 Tab 时生成 QR 并启动 mDNS 发现, 离开时停止发现
    DisposableEffect(Unit) {
        viewModel.generateQr(sessionId, pairingCode)
        viewModel.startQrPairDiscovery(pairingCode)
        onDispose {
            viewModel.stopQrPairDiscovery()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- 提示卡片 ----
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
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "QR 码配对",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "在目标设备上打开「无线调试 > 配对设备」, 选择「使用二维码配对」并扫描下方二维码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- 配对码展示卡片 ----
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
                Text(
                    text = "配对码:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pairingCode,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.weight(1f))
                if (state is WifiAdbState.Pairing || state is WifiAdbState.Connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }

        // ---- QR 码图片 ----
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "配对二维码",
                modifier = Modifier.size(220.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "正在生成二维码...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text = "请确保本机与目标设备处于同一 WiFi 局域网, 且目标设备已开启无线调试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---- WiFi: 配对码配对子 Tab ----
// 6 位码输入 + discoveredPairingServices 列表 + pairWithCode

@Composable
private fun CodePairSubTab(
    viewModel: WifiAdbViewModel,
    discoveredPairingServices: List<DiscoveredPairingService>,
) {
    var pairingCode by remember { mutableStateOf("") }

    // 进入子 Tab 时启动配对服务发现, 离开时停止
    DisposableEffect(Unit) {
        viewModel.startCodePairingDiscovery()
        onDispose {
            viewModel.stopCodePairingDiscovery()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "使用 6 位配对码配对新设备",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
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
            text = "在目标设备「无线调试 > 配对设备」中获取 6 位配对码后输入, 选择下方发现的服务进行配对",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- WiFi: 已保存设备子 Tab ----
// savedDevices 列表 + 重连/忘记

@Composable
private fun SavedDevicesSubTab(
    viewModel: WifiAdbViewModel,
    savedDevices: List<WifiAdbDevice>,
    currentDevice: WifiAdbDevice?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (savedDevices.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Wifi,
                title = "暂无已保存设备",
                subtitle = "配对成功的设备会自动保存, 之后可一键重连",
            )
        } else {
            Text(
                text = "已保存设备 (${savedDevices.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
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

        Text(
            text = "已保存设备可一键重连; 新设备需先在「QR 配对」或「配对码配对」子 Tab 中完成配对。",
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
                        contentPadding = PaddingValues(
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
                    contentPadding = PaddingValues(
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
//   - getvar / reboot / flash / erase / boot 等命令
//   - 设备信息读取 (product/serial/unlocked/slot/battery...)
//   - Flash 操作状态机 (READING_FILE/DOWNLOADING/FLASHING/ERASING/CANCELLING/COMPLETE/ERROR)
//   - 命令控制台 (sendCommand + commandHistory)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FastbootTabContent() {
    val viewModel: FastbootViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val variables by viewModel.variables.collectAsState()
    val isLoadingInfo by viewModel.isLoadingDeviceInfo.collectAsState()
    val isLoadingVars by viewModel.isLoadingVariables.collectAsState()
    val flashOperation by viewModel.flashOperation.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()

    // ---- 对话框状态 ----
    var showFlashDialog by remember { mutableStateOf(false) }
    var showEraseDialog by remember { mutableStateOf(false) }
    var showRebootDialog by remember { mutableStateOf(false) }
    var pendingFlashPartition by remember { mutableStateOf("") }
    var erasePartitionName by remember { mutableStateOf("") }

    // ---- 命令控制台输入 ----
    var commandInput by remember { mutableStateOf("") }

    // ---- 文件选择器 (Flash 分区 / Boot 镜像) ----
    val flashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null && pendingFlashPartition.isNotBlank()) {
            viewModel.flashPartition(pendingFlashPartition, uri)
            pendingFlashPartition = ""
        }
    }
    val bootLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.bootImage(uri)
        }
    }

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

        // ---- 已连接时显示: 设备信息 / 变量 / 系统状态 / 快速工具 / Flash状态 / 命令控制台 ----
        if (state is FastbootState.Connected) {
            // ---- 设备信息卡片 ----
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

            // ---- 活跃槽位 + 解锁状态卡片 ----
            FastbootSystemStatusCard(deviceInfo = deviceInfo)

            // ---- 快速工具区 (Flash / Erase / Boot / Reboot) ----
            FastbootQuickToolsCard(
                onFlash = { showFlashDialog = true },
                onErase = { showEraseDialog = true },
                onBoot = { bootLauncher.launch("*/*") },
                onReboot = { showRebootDialog = true },
            )

            // ---- Flash 操作状态 ----
            if (flashOperation.status != FlashStatus.IDLE) {
                FlashOperationCard(
                    operation = flashOperation,
                    onCancel = { viewModel.cancelFlashOperation() },
                    onReset = { viewModel.resetFlashOperation() },
                )
            }

            // ---- 命令控制台 ----
            FastbootCommandConsoleCard(
                commandHistory = commandHistory,
                commandInput = commandInput,
                onCommandInputChange = { commandInput = it },
                onSend = {
                    if (commandInput.isNotBlank()) {
                        viewModel.sendCommand(commandInput.trim())
                        commandInput = ""
                    }
                },
            )
        }

        Text(
            text = "请将设备重启到 Bootloader/Fastboot 模式后通过 USB 连接。连接后可读取设备信息、执行 Flash/Erase/Boot/Reboot 及任意 Fastboot 命令。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // ---- Flash 分区对话框 (输入分区名 -> 选择文件) ----
    if (showFlashDialog) {
        AlertDialog(
            onDismissRequest = {
                showFlashDialog = false
                pendingFlashPartition = ""
            },
            title = { Text("Flash 分区") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "输入要刷入的分区名 (如 boot, system, vendor), 确认后选择镜像文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = pendingFlashPartition,
                        onValueChange = { pendingFlashPartition = it },
                        label = { Text("分区名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pendingFlashPartition.isNotBlank()) {
                            showFlashDialog = false
                            flashLauncher.launch("*/*")
                        }
                    },
                    enabled = pendingFlashPartition.isNotBlank(),
                ) {
                    Text("选择镜像文件")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFlashDialog = false
                    pendingFlashPartition = ""
                }) {
                    Text("取消")
                }
            },
        )
    }

    // ---- Erase 分区对话框 ----
    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = {
                showEraseDialog = false
                erasePartitionName = ""
            },
            title = { Text("Erase 分区") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "输入要擦除的分区名 (如 cache, userdata)。擦除操作不可逆, 请谨慎操作",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedTextField(
                        value = erasePartitionName,
                        onValueChange = { erasePartitionName = it },
                        label = { Text("分区名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (erasePartitionName.isNotBlank()) {
                            viewModel.erasePartition(erasePartitionName.trim())
                            erasePartitionName = ""
                            showEraseDialog = false
                        }
                    },
                    enabled = erasePartitionName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("擦除")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEraseDialog = false
                    erasePartitionName = ""
                }) {
                    Text("取消")
                }
            },
        )
    }

    // ---- Reboot 模式选择对话框 ----
    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = { Text("选择重启模式") },
            text = {
                Column {
                    RebootMode.values().forEach { mode ->
                        TextButton(
                            onClick = {
                                viewModel.reboot(mode)
                                showRebootDialog = false
                            },
                            contentPadding = PaddingValues(
                                horizontal = 8.dp,
                                vertical = 10.dp,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = mode.displayName,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("取消")
                }
            },
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

// ---- Fastboot: 活跃槽位 + 解锁状态卡片 ----

@Composable
private fun FastbootSystemStatusCard(deviceInfo: FastbootDeviceInfo?) {
    val slot = deviceInfo?.currentSlot
    val unlocked = deviceInfo?.isUnlocked
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- 活跃槽位 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (slot ?: "_").uppercase().take(1),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "活跃槽位 (A/B)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (slot.isNullOrBlank()) "未知" else "Slot ${slot.uppercase()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // ---- 解锁状态 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = when {
                                unlocked == true -> Color(0xFF66BB6A)
                                unlocked == false -> Color(0xFFEF5350)
                                else -> Color(0xFF9E9E9E)
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bootloader 解锁状态",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = when {
                            unlocked == null -> "未知"
                            unlocked -> "已解锁"
                            else -> "未解锁"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            unlocked == true -> Color(0xFF2E7D32)
                            unlocked == false -> Color(0xFFC62828)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

// ---- Fastboot: 快速工具区 (Flash / Erase / Boot / Reboot) ----

@Composable
private fun FastbootQuickToolsCard(
    onFlash: () -> Unit,
    onErase: () -> Unit,
    onBoot: () -> Unit,
    onReboot: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "快速工具",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FastbootToolButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.FlashOn,
                    label = "Flash 分区",
                    containerColor = MaterialTheme.colorScheme.primary,
                    onClick = onFlash,
                )
                FastbootToolButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Delete,
                    label = "Erase 分区",
                    containerColor = MaterialTheme.colorScheme.error,
                    onClick = onErase,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FastbootToolButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.PlayArrow,
                    label = "Boot 镜像",
                    containerColor = Color(0xFF42A5F5),
                    onClick = onBoot,
                )
                FastbootToolButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.PowerSettingsNew,
                    label = "Reboot",
                    containerColor = Color(0xFFFFA726),
                    onClick = onReboot,
                )
            }
        }
    }
}

@Composable
private fun FastbootToolButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    containerColor: Color,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor.copy(alpha = 0.12f),
            contentColor = containerColor,
        ),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---- Fastboot: Flash 操作状态卡片 ----
// 观察 flashOperation, 显示进度和状态:
//   IDLE -> 不显示; READING_FILE/DOWNLOADING/FLASHING/ERASING/CANCELLING -> 进行中;
//   COMPLETE -> 成功; ERROR -> 错误消息。
// 进行中提供取消按钮 (cancelFlashOperation), 完成后提供关闭按钮 (resetFlashOperation)。

@Composable
private fun FlashOperationCard(
    operation: FlashOperation,
    onCancel: () -> Unit,
    onReset: () -> Unit,
) {
    val isActive = operation.status in setOf(
        FlashStatus.READING_FILE,
        FlashStatus.DOWNLOADING,
        FlashStatus.FLASHING,
        FlashStatus.ERASING,
        FlashStatus.CANCELLING,
    )
    val statusColor = flashStatusColor(operation.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (operation.status) {
                FlashStatus.COMPLETE -> MaterialTheme.colorScheme.primaryContainer
                FlashStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
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
                Text(
                    text = "Flash 操作",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = flashStatusText(operation.status),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                )
            }

            if (operation.partition.isNotBlank()) {
                Text(
                    text = "分区: ${operation.partition}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (operation.fileName.isNotBlank()) {
                Text(
                    text = "文件: ${operation.fileName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (operation.message.isNotBlank()) {
                Text(
                    text = operation.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (operation.status == FlashStatus.ERROR)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 进度条 ----
            if (isActive) {
                if (operation.progress > 0f) {
                    LinearProgressIndicator(
                        progress = { operation.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = statusColor,
                        trackColor = statusColor.copy(alpha = 0.15f),
                    )
                    Text(
                        text = "${(operation.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = statusColor,
                        trackColor = statusColor.copy(alpha = 0.15f),
                    )
                }
            }

            // ---- 操作按钮 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (isActive) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("取消操作")
                    }
                } else {
                    if (operation.status == FlashStatus.COMPLETE) {
                        TextButton(onClick = onReset) {
                            Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("完成")
                        }
                    } else {
                        TextButton(onClick = onReset) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

// ---- Fastboot: 命令控制台 ----
// 输入框 + 发送按钮 -> sendCommand(command), 显示 commandHistory 历史记录

@Composable
private fun FastbootCommandConsoleCard(
    commandHistory: List<FastbootCommandResult>,
    commandInput: String,
    onCommandInputChange: (String) -> Unit,
    onSend: () -> Unit,
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
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "命令控制台",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${commandHistory.size} 条记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 历史记录列表 ----
            if (commandHistory.isEmpty()) {
                Text(
                    text = "暂无命令记录, 在下方输入 Fastboot 命令 (如 getvar all)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(commandHistory, key = { it.timestamp }) { result ->
                        CommandHistoryItem(result)
                    }
                }
            }

            // ---- 输入框 + 发送按钮 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = onCommandInputChange,
                    label = { Text("Fastboot 命令") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSend,
                    enabled = commandInput.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Default.Send, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun CommandHistoryItem(result: FastbootCommandResult) {
    val statusColor = responseStatusColor(result.status)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$ ${result.command}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = result.status.prefix,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
            }
            if (result.data.isNotBlank()) {
                Text(
                    text = result.data,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(onClick = onClick, contentPadding = contentPadding, content = content)
}

// ---- 6 位随机配对码 (跟 android-adb-shell generatePairingCode 一致, 返回 String) ----
private fun generatePairingCode(): String {
    val random = SecureRandom()
    val code = StringBuilder()
    repeat(6) { code.append(random.nextInt(10)) }
    return code.toString()
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

// ---- Flash 操作状态映射 ----

private fun flashStatusColor(status: FlashStatus): Color = when (status) {
    FlashStatus.IDLE -> Color(0xFF9E9E9E)
    FlashStatus.READING_FILE, FlashStatus.DOWNLOADING,
    FlashStatus.FLASHING, FlashStatus.ERASING -> Color(0xFFFFA726)
    FlashStatus.CANCELLING -> Color(0xFF9E9E9E)
    FlashStatus.COMPLETE -> Color(0xFF66BB6A)
    FlashStatus.ERROR -> Color(0xFFEF5350)
}

private fun flashStatusText(status: FlashStatus): String = when (status) {
    FlashStatus.IDLE -> "空闲"
    FlashStatus.READING_FILE -> "读取文件中"
    FlashStatus.DOWNLOADING -> "下载中"
    FlashStatus.FLASHING -> "刷写中"
    FlashStatus.ERASING -> "擦除中"
    FlashStatus.CANCELLING -> "取消中"
    FlashStatus.COMPLETE -> "完成"
    FlashStatus.ERROR -> "错误"
}

// ---- Fastboot 命令响应状态映射 ----

private fun responseStatusColor(status: ResponseStatus): Color = when (status) {
    ResponseStatus.OKAY -> Color(0xFF66BB6A)
    ResponseStatus.FAIL -> Color(0xFFEF5350)
    ResponseStatus.DATA -> Color(0xFF42A5F5)
    ResponseStatus.INFO -> Color(0xFF9E9E9E)
}
