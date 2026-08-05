package com.itsaky.androidide.fragments.debugger

import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.zero.studio.core.utils.askUserToEnableWifi
import android.zero.studio.core.utils.createAppNotificationSettingsIntent
import android.zero.studio.core.utils.createMiUiNotificationStylesSettingsIntent
import android.zero.studio.core.utils.isConnectedToWifi
import android.zero.studio.core.utils.isNotificationPermissionGranted
import android.zero.studio.core.utils.MiUiCheck
import android.zero.studio.core.utils.registerNetworkCallback
import android.zero.studio.core.utils.showToast
import android.zero.studio.core.utils.unregisterNetworkCallback
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbDevice
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbState
import android.zero.studio.shell.wifi_adb_shell.presentation.viewmodel.WifiAdbViewModel
import android.zero.studio.shell.wifi_adb_shell.service.SelfPairingService
import android.zero.studio.shell.wifi_adb_shell.utils.WirelessDebuggingUtils
import com.itsaky.androidide.fragments.debugger.console.AdbConsoleFragment
import com.itsaky.androidide.ui.theme.deviceconnection.DeviceConnectionTheme

/**
 * 配对此设备页面（全屏）。
 *
 * 直接照搬 debugger/adb-connection/connection 里 PairingOwnDeviceScreen 的 UI 设计：
 * - WiFi 未连接时显示 WiFi 开启卡片
 * - 已保存的本机设备卡片（重连/忘记/断开/前往终端）
 * - 通知权限申请卡片 / 通知配对提示卡片
 * - 通知样式提示卡片（MIUI）
 * - 3 步操作指引（含「开发者选项」跳转按钮）
 *
 * 点击「开发者选项」按钮：检查通知权限 → 检查 WiFi → 启动 SelfPairingService → 跳转无线调试设置。
 */
class PairingOwnDeviceFragment : DialogFragment() {

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
                PairingOwnDeviceScreen(
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

@Composable
private fun PairingOwnDeviceScreen(
    onBack: () -> Unit,
    onNavigateToConsole: () -> Unit,
    viewModel: WifiAdbViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isWifiConnected by remember { mutableStateOf(context.isConnectedToWifi()) }
    var hasNotificationAccess by remember { mutableStateOf(isNotificationPermissionGranted(context)) }

    val savedDevices by viewModel.savedDevices.collectAsState()
    val wifiAdbState by viewModel.state.collectAsState()
    val currentDevice by viewModel.currentDevice.collectAsState()
    val ownDevice = savedDevices.filter { it.isOwnDevice }.getOrNull(0)

    // 监听 WiFi 连接状态变化
    DisposableEffect(Unit) {
        val callback = registerNetworkCallback(context) { isConnected ->
            isWifiConnected = isConnected
        }
        onDispose { unregisterNetworkCallback(context, callback) }
    }

    // 页面恢复时刷新权限状态
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasNotificationAccess = isNotificationPermissionGranted(context)
                    isWifiConnected = context.isConnectedToWifi()
                }
            },
        )
    }

    // WiFi 断开时标记设备已断开
    LaunchedEffect(isWifiConnected, ownDevice) {
        if (!isWifiConnected && ownDevice != null) {
            viewModel.setDeviceDisconnected(ownDevice.id)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val onClickNotificationButton: () -> Unit = {
        context.startActivity(createAppNotificationSettingsIntent(context))
    }

    val onClickNotificationStylesButton: () -> Unit = {
        try {
            context.startActivity(createMiUiNotificationStylesSettingsIntent(context))
        } catch (e: Exception) {
            showToast(context, "未找到通知样式设置页面")
        }
    }

    val onClickWifiEnableButton: () -> Unit = {
        context.askUserToEnableWifi()
    }

    val onClickDevOptionsButton: () -> Unit = {
        if (!hasNotificationAccess) {
            showToast(context, "请先授权通知权限")
            return
        }
        if (!isWifiConnected) {
            showToast(context, "请先连接到 WiFi 网络")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        SelfPairingService.start(context)
        WirelessDebuggingUtils.openWirelessDebuggingSettings(context)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LargeTopAppBar(
                title = {
                    val collapsedFraction = scrollBehavior.state.collapsedFraction
                    val fontSize = androidx.compose.ui.unit.lerp(28.sp, 20.sp, collapsedFraction)
                    Text(
                        text = "配对此设备",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(start = 15.dp, end = 15.dp, top = 15.dp),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            // WiFi 未连接时显示 WiFi 开启卡片
            if (!isWifiConnected) {
                item {
                    HintCard(
                        icon = Icons.Default.SignalWifiOff,
                        iconTint = MaterialTheme.colorScheme.error,
                        text = "需要连接 WiFi 网络才能进行无线调试配对",
                        buttonText = "开启 WiFi",
                        buttonIcon = Icons.Default.WifiSettings,
                        onButtonClick = onClickWifiEnableButton,
                    )
                }
            }

            // 已保存的本机设备卡片
            ownDevice?.let { device ->
                item {
                    val isCurrentDevice = currentDevice?.id == device.id
                    val isReconnecting = wifiAdbState is WifiAdbState.Reconnecting &&
                        (wifiAdbState as WifiAdbState.Reconnecting).deviceId == device.id
                    val isConnected = isCurrentDevice &&
                        (wifiAdbState is WifiAdbState.Connected || viewModel.isConnected()) &&
                        isWifiConnected

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SavedDeviceCard(
                            device = device,
                            isConnected = isConnected,
                            isReconnecting = isReconnecting,
                            onReconnect = {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@SavedDeviceCard
                                if (!isWifiConnected) {
                                    showToast(context, "请先连接到 WiFi 网络")
                                    return@SavedDeviceCard
                                }
                                viewModel.reconnectToDevice(device)
                            },
                            onForget = { viewModel.forgetDevice(device) },
                            onDisconnect = { viewModel.disconnect() },
                        )

                        if (isReconnecting) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                onClick = { viewModel.cancelReconnect() },
                            ) {
                                Text("取消重连")
                            }
                        }

                        if (isConnected) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                onClick = onNavigateToConsole,
                            ) {
                                Icon(Icons.Default.Terminal, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("前往 ADB 命令")
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            // 提示标题
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 5.dp),
                    text = "提示",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 通知权限卡片（未授权时显示） / 通知配对提示卡片（已授权时显示）
            if (!hasNotificationAccess) {
                item {
                    HintCard(
                        icon = Icons.Default.Notifications,
                        iconTint = MaterialTheme.colorScheme.error,
                        text = "自配对需要通过通知栏输入配对码，请先授权通知访问权限",
                        buttonText = "通知设置",
                        buttonIcon = Icons.Default.OpenInNew,
                        onButtonClick = onClickNotificationButton,
                    )
                }
            } else {
                item {
                    InfoCard(
                        icon = Icons.Default.Notifications,
                        text = "配对过程中，6 位配对码会通过通知栏输入，请保持通知权限开启",
                    )
                }
            }

            // 通知样式提示卡片（MIUI 专属）
            item {
                NotificationStylesCard(
                    onClickButton = onClickNotificationStylesButton,
                )
            }

            // 3 步操作指引
            item {
                InstructionsSection(
                    onClickDevOptionsButton = onClickDevOptionsButton,
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ── 子组件 ──

/** 带图标 + 文字 + 操作按钮的提示卡片（照搬源码 IconWithTextCard 结构）。 */
@Composable
private fun HintCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    text: String,
    buttonText: String,
    buttonIcon: ImageVector? = null,
    onButtonClick: () -> Unit,
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
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Button(
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                onClick = onButtonClick,
            ) {
                if (buttonIcon != null) {
                    Icon(buttonIcon, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(buttonText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 仅图标 + 文字的信息卡片。 */
@Composable
private fun InfoCard(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 通知样式提示卡片（MIUI 专属，非 MIUI 仅显示提示文字）。 */
@Composable
private fun NotificationStylesCard(
    onClickButton: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                "MIUI 设备需在通知样式中允许「配对服务」通知，否则配对码通知可能被隐藏",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (MiUiCheck.isMiui(excludeHyperOS = false)) {
                Button(
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                    onClick = onClickButton,
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("通知样式", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 已保存设备卡片（照搬源码 SavedDeviceItem 结构）。 */
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
        color = if (isConnected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
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
                // 状态标签
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
                    ) {
                        Text(if (isReconnecting) "重连中..." else "重连")
                    }
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

/** 3 步操作指引（照搬源码 Instructions 结构）。 */
@Composable
private fun InstructionsSection(
    onClickDevOptionsButton: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            modifier = Modifier.padding(vertical = 15.dp, horizontal = 5.dp),
            text = "操作步骤",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        // Step 1: 打开无线调试 + 开发者选项按钮
        InstructionCard(
            stepNumber = 1,
            text = "在手机「设置 → 开发者选项」中打开「无线调试」开关",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "重要：必须先打开无线调试，否则配对将失败",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Button(
                    shape = RoundedCornerShape(14.dp),
                    onClick = onClickDevOptionsButton,
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("开发者选项")
                }
            }
        }

        // Step 2: 使用配对码配对设备
        InstructionCard(
            stepNumber = 2,
            text = "在无线调试页面点击「使用配对码配对设备」，系统会显示 6 位配对码",
        )

        // Step 3: 在通知栏输入配对码
        InstructionCard(
            stepNumber = 3,
            text = "回到本应用，在通知栏中输入 6 位配对码即可完成配对",
        )
    }
}

/** 单步指引卡片（带序号圆标）。 */
@Composable
private fun InstructionCard(
    stepNumber: Int,
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = 1.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 序号圆标
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    stepNumber.toString(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                content()
            }
        }
    }
}
