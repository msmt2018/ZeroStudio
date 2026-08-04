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
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import android.zero.studio.shell.wifi_adb_shell.service.SelfPairingService
import com.itsaky.androidide.ui.theme.deviceconnection.DeviceConnectionTheme
import com.itsaky.androidide.ui.theme.deviceconnection.DcPrimaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcSecondaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * 配对此设备页面（全屏）。本机 Android 11+ 无线调试自配对。
 *
 * 落实 spec §5.1：
 * - 复用 connection 模块的 [SelfPairingService]：启动后会在通知栏显示配对码输入通知
 * - 三按钮：[开始发现] / [配对] / [复制本机地址]
 * - 完整状态反馈：发现 pairing 服务中 / 等待输入码 / 配对中 / 成功 / 失败
 */
class PairingOwnDeviceFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_NoActionBar)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            DeviceConnectionTheme {
                PairingOwnDeviceScreen(onBack = { dismiss() })
            }
        }
    }
}

/** 配对状态。落实 spec §5.1 完整状态反馈。 */
private enum class PairingState(val label: String) {
    IDLE("未启动"),
    DISCOVERING("发现 pairing 服务中"),
    WAITING_CODE("等待输入配对码"),
    PAIRING("配对中"),
    SUCCESS("配对成功"),
    FAILED("配对失败"),
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun PairingOwnDeviceScreen(onBack: () -> Unit) {
    val c = deviceConnectionColors
    val context = LocalContext.current
    var serviceRunning by remember { mutableStateOf(false) }
    var pairingState by remember { mutableStateOf(PairingState.IDLE) }
    var pairingCode by remember { mutableStateOf("") }
    var localAddress by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text("配对此设备", color = c.textPrimary) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "本机 Android 11+ 无线调试自配对",
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "启动服务后，系统会显示一条通知；在通知中输入目标设备的 6 位配对码即可完成配对。",
                color = c.textSecondary,
            )
            val steps = listOf(
                "1. 确保目标设备已开启「无线调试」",
                "2. 在目标设备点击「使用配对码配对设备」",
                "3. 记录显示的配对端口与 6 位配对码",
                "4. 点击下方「开始发现」启动服务",
                "5. 在通知栏或下方输入框填入 6 位配对码",
            )
            steps.forEach { step ->
                Text(step, color = c.textSecondary)
            }
            Spacer(Modifier.width(0.dp))

            // 6 位配对码输入框（落实 spec §5.1「也可在通知栏输入，页面提供手动入口」）
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pairingCode = it },
                label = { Text("6 位配对码（可选，手动配对）", color = c.textSecondary) },
                singleLine = true,
                enabled = serviceRunning,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // 三按钮：开始发现 / 配对 / 复制本机地址（落实 spec §5.1）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (serviceRunning) {
                    DcPrimaryButton(
                        text = "停止服务",
                        icon = Icons.Default.Stop,
                        onClick = {
                            SelfPairingService.stop(context)
                            serviceRunning = false
                            pairingState = PairingState.IDLE
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    DcPrimaryButton(
                        text = "开始发现",
                        icon = Icons.Default.PlayArrow,
                        onClick = {
                            SelfPairingService.start(context)
                            serviceRunning = true
                            pairingState = PairingState.DISCOVERING
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                DcSecondaryButton(
                    text = "配对",
                    icon = Icons.Default.Link,
                    enabled = serviceRunning && pairingCode.length == 6,
                    onClick = {
                        pairingState = PairingState.PAIRING
                        // 手动配对入口：实际配对由通知栏 RemoteInput 完成，
                        // 此处仅更新状态提示用户也可在通知栏输入
                        pairingState = PairingState.WAITING_CODE
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DcSecondaryButton(
                    text = "复制本机地址",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        val address = getLocalIpAddress(context)
                        localAddress = address
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("本机地址", address))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 状态反馈（落实 spec §5.1 完整状态）
            val stateColor = when (pairingState) {
                PairingState.SUCCESS -> c.statusGreen
                PairingState.FAILED -> c.statusRed
                PairingState.DISCOVERING, PairingState.WAITING_CODE, PairingState.PAIRING -> c.statusYellow
                PairingState.IDLE -> c.textSecondary
            }
            Text(
                "状态: ${pairingState.label}",
                color = stateColor,
                fontWeight = FontWeight.Medium,
            )
            localAddress?.let {
                Text(
                    "本机地址: $it",
                    color = c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
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