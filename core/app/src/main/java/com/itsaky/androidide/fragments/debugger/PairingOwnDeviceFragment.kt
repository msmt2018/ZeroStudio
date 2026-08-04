package com.itsaky.androidide.fragments.debugger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import android.zero.studio.core.utils.createAppNotificationSettingsIntent
import android.zero.studio.core.utils.isNotificationPermissionGranted
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbConnection
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbEvent
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbState
import android.zero.studio.shell.wifi_adb_shell.service.SelfPairingService
import android.zero.studio.shell.wifi_adb_shell.utils.WirelessDebuggingUtils
import com.itsaky.androidide.fragments.debugger.console.AdbConsoleFragment
import com.itsaky.androidide.ui.theme.deviceconnection.DeviceConnectionTheme
import com.itsaky.androidide.ui.theme.deviceconnection.DcPrimaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcSecondaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * 配对此设备页面（全屏）。本机 Android 11+ 无线调试自配对。
 *
 * 参考 debugger/adb-connection/connection 的 PairingOwnDeviceScreen 设计：
 * - 通知权限检查与申请
 * - 「打开无线调试设置」按钮：检查权限 → 启动 SelfPairingService → 跳转系统无线调试
 * - collect [WifiAdbConnection.state]，连接成功后显示「前往 ADB 命令」按钮
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
) {
    val c = deviceConnectionColors
    val context = LocalContext.current
    val wifiState by WifiAdbConnection.state.collectAsState()
    val currentDevice by WifiAdbConnection.currentDevice.collectAsState()

    var serviceRunning by remember { mutableStateOf(false) }
    var hasNotificationAccess by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    var pairingCode by remember { mutableStateOf("") }
    var localAddress by remember { mutableStateOf<String?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }

    // 页面恢复时刷新通知权限状态
    LaunchedEffect(Unit) {
        hasNotificationAccess = isNotificationPermissionGranted(context)
    }

    // 收集一次性事件（错误提示等）
    LaunchedEffect(Unit) {
        WifiAdbConnection.events.collect { event ->
            when (event) {
                is WifiAdbEvent.PairConnectFailed -> toastMsg = "配对成功但连接失败：${event.error}"
                is WifiAdbEvent.ReconnectFailed -> toastMsg = "重连失败，可能需重新配对"
                is WifiAdbEvent.WirelessDebuggingOff -> toastMsg = "无线调试已关闭"
                else -> {}
            }
        }
    }

    // 服务运行状态跟随连接状态
    LaunchedEffect(wifiState) {
        when (wifiState) {
            is WifiAdbState.Connected -> serviceRunning = false
            is WifiAdbState.Disconnected, WifiAdbState.Idle -> {
                // 保持当前 serviceRunning 状态
            }
            else -> {}
        }
    }

    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            toastMsg = null
        }
    }

    val isConnected = wifiState is WifiAdbState.Connected

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text("配对此设备", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = c.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surfacePanel),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 通知权限申请卡片（未授权时显示）
            if (!hasNotificationAccess) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.statusYellow.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Notifications, null, tint = c.statusYellow, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("需要通知权限", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                "自配对需通过通知栏输入配对码，请先授权通知",
                                color = c.textSecondary,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                        DcSecondaryButton(
                            text = "去授权",
                            onClick = {
                                context.startActivity(createAppNotificationSettingsIntent(context))
                            },
                        )
                    }
                }
            }

            // 说明
            Text(
                "本机 Android 11+ 无线调试自配对",
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "点击「打开无线调试设置」会启动后台配对服务并跳转到系统开发者选项，" +
                    "在系统页面操作后，返回此页面即可看到连接状态。",
                color = c.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )

            val steps = listOf(
                "1. 授权通知权限（上方卡片）",
                "2. 确保手机连接到 WiFi 网络",
                "3. 点击「打开无线调试设置」",
                "4. 在系统页面打开「无线调试」开关",
                "5. 点击「使用配对码配对设备」，记录 6 位码",
                "6. 在通知栏输入 6 位配对码完成配对",
            )
            steps.forEach { step ->
                Text(step, color = c.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.width(0.dp))

            // 核心：打开无线调试设置按钮
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (serviceRunning) {
                    DcPrimaryButton(
                        text = "停止配对服务",
                        icon = Icons.Default.Stop,
                        onClick = {
                            SelfPairingService.stop(context)
                            serviceRunning = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DcPrimaryButton(
                        text = "打开无线调试设置",
                        icon = Icons.Default.DeveloperMode,
                        onClick = {
                            // 1. 检查通知权限
                            if (!isNotificationPermissionGranted(context)) {
                                toastMsg = "请先授权通知权限"
                                return@DcPrimaryButton
                            }
                            // 2. 检查 WiFi
                            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                            if (wifiManager?.connectionInfo?.ipAddress == 0) {
                                toastMsg = "请先连接到 WiFi 网络"
                                return@DcPrimaryButton
                            }
                            // 3. 启动自配对服务
                            SelfPairingService.start(context)
                            serviceRunning = true
                            // 4. 跳转无线调试设置
                            WirelessDebuggingUtils.openWirelessDebuggingSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.statusRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "无线调试需要 Android 11 (R) 及以上版本",
                        color = c.statusRed,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // 服务运行中状态提示
            if (serviceRunning) {
                val stateLabel = when (wifiState) {
                    is WifiAdbState.Discovering -> "正在发现 pairing 服务..."
                    is WifiAdbState.Pairing -> "配对中..."
                    is WifiAdbState.Connecting -> "连接中..."
                    else -> "等待在通知栏输入配对码"
                }
                Text(
                    "状态: $stateLabel",
                    color = c.statusYellow,
                    fontWeight = FontWeight.Medium,
                )
            }

            // 6 位配对码输入框（可选手动输入，正常由通知栏完成）
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pairingCode = it },
                label = { Text("6 位配对码（通知栏输入为主，此处可选）", color = c.textSecondary) },
                singleLine = true,
                enabled = serviceRunning,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // 复制本机地址
            DcSecondaryButton(
                text = "复制本机地址",
                icon = Icons.Default.ContentCopy,
                onClick = {
                    val address = getLocalIpAddress(context)
                    localAddress = address
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("本机地址", address))
                    toastMsg = "已复制: $address"
                },
                modifier = Modifier.fillMaxWidth(),
            )

            localAddress?.let {
                Text(
                    "本机地址: $it",
                    color = c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }

            // 连接成功：显示前往 ADB 命令按钮
            if (isConnected) {
                Spacer(Modifier.size(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.statusGreen.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "已连接：${currentDevice?.deviceName ?: currentDevice?.ip ?: "设备"}",
                            color = c.statusGreen,
                            fontWeight = FontWeight.SemiBold,
                        )
                        DcPrimaryButton(
                            text = "前往 ADB 命令",
                            icon = Icons.Default.Terminal,
                            onClick = onNavigateToConsole,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** 获取本机 WiFi IP 地址，格式化为 `xxx.xxx.xxx.xxx:5555`。 */
private fun getLocalIpAddress(context: Context): String {
    return runCatching {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifiManager.connectionInfo.ipAddress
        if (ip == 0) "127.0.0.1:5555"
        else "${(ip and 0xff)}.${(ip shr 8 and 0xff)}.${(ip shr 16 and 0xff)}.${(ip shr 24 and 0xff)}:5555"
    }.getOrDefault("127.0.0.1:5555")
}
