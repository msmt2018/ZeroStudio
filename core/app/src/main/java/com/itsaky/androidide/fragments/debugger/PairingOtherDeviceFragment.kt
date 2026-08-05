package com.itsaky.androidide.fragments.debugger

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiSettings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.zero.studio.core.utils.askUserToEnableWifi
import android.zero.studio.core.utils.isConnectedToWifi
import android.zero.studio.core.utils.registerNetworkCallback
import android.zero.studio.core.utils.showToast
import android.zero.studio.core.utils.unregisterNetworkCallback
import android.zero.studio.shell.wifi_adb_shell.domain.model.DiscoveredPairingService
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbDevice
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbState
import android.zero.studio.shell.wifi_adb_shell.presentation.viewmodel.WifiAdbViewModel
import com.itsaky.androidide.fragments.debugger.console.AdbConsoleFragment
import com.itsaky.androidide.ui.theme.deviceconnection.DeviceConnectionTheme
import kotlinx.coroutines.launch
import java.security.SecureRandom

/**
 * 配对其它设备页面（全屏）。
 *
 * 直接照搬 debugger/adb-connection/connection 里 PairingOtherDeviceScreen 的 UI 设计：
 * - WiFi 未连接时显示 WiFi 开启卡片；已连接时显示关闭移动数据提示
 * - SecondaryTabRow 三个 Tab：
 *   1. 已保存设备：列表展示已配对设备，支持重连/忘记/断开/前往终端
 *   2. QR 配对：生成二维码供目标设备扫描
 *   3. 代码配对：mDNS 发现设备列表 + 手动输入配对码
 */
class PairingOtherDeviceFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            DeviceConnectionTheme {
                PairingOtherDeviceScreen(
                    onBack = { dismiss() },
                    onNavigateToConsole = {
                        dismiss()
                        AdbConsoleFragment().show(parentFragmentManager, "adb_console")
                    },
                )
            }
        }
    }
}

private enum class PairingTab(val title: String) {
    SavedDevices("已保存设备"),
    QrPair("扫码配对"),
    CodePair("配对码配对"),
}

@SuppressLint("DefaultLocale")
@Composable
private fun PairingOtherDeviceScreen(
    onBack: () -> Unit,
    onNavigateToConsole: () -> Unit,
    viewModel: WifiAdbViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var isWifiConnected by remember { mutableStateOf(context.isConnectedToWifi()) }
    val wifiAdbState by viewModel.state.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    val currentDevice by viewModel.currentDevice.collectAsState()

    val tabs = PairingTab.entries
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }

    // 6 位随机配对码（用于 QR 配对）
    val pairingCode = remember { String.format("%06d", generatePairingCode()) }

    // 监听 WiFi 连接状态
    DisposableEffect(Unit) {
        val callback = registerNetworkCallback(context) { isConnected ->
            isWifiConnected = isConnected
        }
        onDispose { unregisterNetworkCallback(context, callback) }
    }

    // 页面恢复时刷新 WiFi 状态
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isWifiConnected = context.isConnectedToWifi()
                }
            },
        )
    }

    val onClickWifiEnableButton: () -> Unit = {
        context.askUserToEnableWifi()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LargeTopAppBar(
                title = {
                    val collapsedFraction = scrollBehavior.state.collapsedFraction
                    val fontSize = androidx.compose.ui.unit.lerp(28.sp, 20.sp, collapsedFraction)
                    Text(
                        text = "配对其它设备",
                        maxLines = 1,
                        fontSize = fontSize,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.05.em,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            // WiFi 状态卡片
            if (!isWifiConnected) {
                WifiEnableHintCard(
                    onClickButton = onClickWifiEnableButton,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
                )
            } else {
                // 已连接 WiFi 时提示关闭移动数据
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            "建议关闭移动数据，确保两台设备在同一 WiFi 网络",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            // Tab 栏
            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(tab.title) },
                    )
                }
            }

            // Pager 内容
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (tabs[page]) {
                    PairingTab.SavedDevices -> SavedDevicesTab(
                        savedDevices = savedDevices,
                        currentDevice = currentDevice,
                        wifiAdbState = wifiAdbState,
                        isWifiConnected = isWifiConnected,
                        viewModel = viewModel,
                        onReconnect = { device ->
                            if (!isWifiConnected) {
                                showToast(context, "请先连接到 WiFi 网络")
                                return@SavedDevicesTab
                            }
                            viewModel.reconnectToDevice(device)
                        },
                        onCancelReconnect = { viewModel.cancelReconnect() },
                        onDisconnect = { viewModel.disconnect() },
                        onForget = { device -> viewModel.forgetDevice(device) },
                        onGoToTerminal = onNavigateToConsole,
                    )

                    PairingTab.QrPair -> QrPairTab(
                        isWifiConnected = isWifiConnected,
                        wifiAdbState = wifiAdbState,
                        pairingCode = pairingCode,
                        viewModel = viewModel,
                    )

                    PairingTab.CodePair -> CodePairTab(
                        isWifiConnected = isWifiConnected,
                        viewModel = viewModel,
                        onGoToTerminal = onNavigateToConsole,
                    )
                }
            }
        }
    }
}

// ── Tab 1: 已保存设备 ──

@Composable
private fun SavedDevicesTab(
    savedDevices: List<WifiAdbDevice>,
    currentDevice: WifiAdbDevice?,
    wifiAdbState: WifiAdbState,
    isWifiConnected: Boolean,
    viewModel: WifiAdbViewModel,
    onReconnect: (WifiAdbDevice) -> Unit,
    onCancelReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: (WifiAdbDevice) -> Unit,
    onGoToTerminal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (savedDevices.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Wifi,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "暂无已保存设备",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                "请通过扫码或配对码配对设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(16.dp)) }
            items(savedDevices, key = { it.id }) { device ->
                val isReconnecting = wifiAdbState is WifiAdbState.Reconnecting &&
                    wifiAdbState.deviceId == device.id
                val isConnected = currentDevice?.id == device.id &&
                    (wifiAdbState is WifiAdbState.Connected || viewModel.isConnected())

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SavedDeviceCard(
                        device = device,
                        isConnected = isConnected,
                        isReconnecting = isReconnecting,
                        onReconnect = { onReconnect(device) },
                        onForget = { onForget(device) },
                        onDisconnect = onDisconnect,
                    )

                    if (isReconnecting) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            onClick = onCancelReconnect,
                        ) { Text("取消重连") }
                    }

                    if (isConnected) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            onClick = onGoToTerminal,
                        ) {
                            Icon(Icons.Default.Terminal, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("前往 ADB 命令")
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** 已保存设备卡片。 */
@Composable
private fun SavedDeviceCard(
    device: WifiAdbDevice,
    isConnected: Boolean,
    isReconnecting: Boolean,
    onReconnect: () -> Unit,
    onForget: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isConnected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Wifi,
                    null,
                    tint = if (isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        device.deviceName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${device.ip}:${device.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val statusText = when {
                    isConnected -> "已连接"
                    isReconnecting -> "重连中"
                    else -> "未连接"
                }
                val statusColor = when {
                    isConnected -> MaterialTheme.colorScheme.primary
                    isReconnecting -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isConnected) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        onClick = onDisconnect,
                    ) { Text("断开") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        onClick = onForget,
                    ) { Text("忘记") }
                } else {
                    Button(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isReconnecting,
                        onClick = onReconnect,
                    ) { Text(if (isReconnecting) "重连中..." else "重连") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        onClick = onForget,
                    ) { Text("忘记") }
                }
            }
        }
    }
}

// ── Tab 2: QR 配对 ──

@Composable
private fun QrPairTab(
    isWifiConnected: Boolean,
    wifiAdbState: WifiAdbState,
    pairingCode: String,
    viewModel: WifiAdbViewModel,
    modifier: Modifier = Modifier,
) {
    val qrBitmap by viewModel.qrBitmap.collectAsState()
    val sessionId = remember { "ashell_you" }

    // WiFi 连接时启动 mDNS 发现
    LaunchedEffect(isWifiConnected) {
        if (isWifiConnected) viewModel.startQrPairDiscovery(pairingCode)
        else viewModel.stopQrPairDiscovery()
    }

    // 生成 QR 码
    LaunchedEffect(pairingCode, isWifiConnected) {
        viewModel.generateQr(sessionId, pairingCode)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        // 提示卡片
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            "在目标设备的无线调试页面选择「扫码配对」，扫描下方二维码",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Search,
                            null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            "配对码：$pairingCode（如需手动输入）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }

        // QR 码图片
        item {
            qrBitmap?.let { bitmap ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.padding(25.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "配对二维码",
                            modifier = Modifier.size(240.dp),
                        )
                    }
                }
            }
        }

        // 状态提示
        item {
            val statusText = when {
                !isWifiConnected -> "请先连接 WiFi"
                wifiAdbState is WifiAdbState.Pairing -> "配对中..."
                wifiAdbState is WifiAdbState.Connecting -> "连接中..."
                wifiAdbState is WifiAdbState.Connected -> "已连接"
                else -> "等待目标设备扫码..."
            }
            Text(
                statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 25.dp),
            )
        }
    }
}

// ── Tab 3: 代码配对 ──

@Composable
private fun CodePairTab(
    isWifiConnected: Boolean,
    viewModel: WifiAdbViewModel,
    onGoToTerminal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wifiAdbState by viewModel.state.collectAsState()
    val discoveredServices by viewModel.discoveredPairingServices.collectAsState()

    var pairingCodeInput by remember { mutableStateOf("") }

    // WiFi 连接时启动 mDNS 发现
    LaunchedEffect(isWifiConnected) {
        if (isWifiConnected) viewModel.startCodePairingDiscovery()
        else viewModel.stopCodePairingDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopCodePairingDiscovery() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 说明卡片
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Icon(
                        Icons.Default.Link,
                        null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        "在目标设备无线调试页面点击「使用配对码配对设备」，" +
                            "下方会自动发现设备，输入 6 位配对码后点击配对",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        // 状态消息
        item {
            val statusText = when {
                discoveredServices.isEmpty() -> "正在搜索设备..."
                else -> "已发现 ${discoveredServices.size} 台设备"
            }
            Text(
                statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 5.dp),
            )
        }

        // 已发现的设备列表
        items(discoveredServices, key = { it.key }) { service ->
            DiscoveredDeviceCard(
                service = service,
                pairingCode = pairingCodeInput,
                isPairing = wifiAdbState is WifiAdbState.Pairing,
                onPair = { code ->
                    viewModel.pairWithCode(service, code)
                },
            )
        }

        // 配对码输入框
        item {
            OutlinedTextField(
                value = pairingCodeInput,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pairingCodeInput = it },
                label = { Text("6 位配对码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
            )
        }

        // 连接中状态
        if (wifiAdbState is WifiAdbState.Connecting) {
            item {
                Text(
                    "连接中...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // 已连接时显示前往终端按钮
        if (wifiAdbState is WifiAdbState.Connected) {
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    onClick = onGoToTerminal,
                ) {
                    Icon(Icons.Default.Terminal, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("前往 ADB 命令")
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

/** 已发现的设备卡片。 */
@Composable
private fun DiscoveredDeviceCard(
    service: DiscoveredPairingService,
    pairingCode: String,
    isPairing: Boolean,
    onPair: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Wifi,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        service.deviceName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${service.ip}:${service.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                enabled = !isPairing && pairingCode.length == 6,
                onClick = { onPair(pairingCode) },
            ) {
                Text(if (isPairing) "配对中..." else "配对")
            }
        }
    }
}

/** WiFi 开启提示卡片。 */
@Composable
private fun WifiEnableHintCard(
    onClickButton: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Default.SignalWifiOff,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
            Text(
                "需要连接 WiFi 网络才能进行配对",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                onClick = onClickButton,
            ) {
                Icon(Icons.Default.WifiSettings, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("开启 WiFi")
            }
        }
    }
}

/** 生成 6 位随机配对码。 */
fun generatePairingCode(): Int {
    val random = SecureRandom()
    return (100000 + random.nextInt(900000))
}
