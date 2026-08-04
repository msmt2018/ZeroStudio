package com.itsaky.androidide.fragments.debugger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.zero.studio.shell.wifi_adb_shell.data.repository.WifiAdbRepositoryImpl.MdnsDiscoveryCallback
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbConnection
import android.zero.studio.shell.wifi_adb_shell.domain.model.WifiAdbState
import android.zero.studio.shell.wifi_adb_shell.domain.repository.WifiAdbRepository
import com.itsaky.androidide.fragments.debugger.console.AdbConsoleFragment
import com.itsaky.androidide.ui.theme.deviceconnection.DeviceConnectionTheme
import com.itsaky.androidide.ui.theme.deviceconnection.DcPrimaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcSecondaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 配对其它设备页面（全屏）。手动输入 IP / 配对端口 / 6 位配对码，配对成功后自动连接。
 *
 * 复用 connection 模块的 [WifiAdbRepository.pairAndConnect]（配对 + 自动连接）。
 * 连接成功后显示「前往 ADB 命令」按钮。
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

/** 配对状态。区分成功 / 失败 / 加载，便于 UI 着色。 */
sealed class PairStatus {
    data object Idle : PairStatus()
    data class Loading(val msg: String) : PairStatus()
    data class Success(val msg: String) : PairStatus()
    data class Failed(val msg: String) : PairStatus()
}

@HiltViewModel
class PairingOtherDeviceViewModel @Inject constructor(
    private val wifiAdbRepository: WifiAdbRepository,
) : ViewModel() {

    private val _status = MutableStateFlow<PairStatus>(PairStatus.Idle)
    val status: StateFlow<PairStatus> = _status.asStateFlow()

    /**
     * 配对并连接。落实 spec §5.2：使用 [WifiAdbRepository.pairAndConnect] 一次完成配对+连接。
     */
    fun pairAndConnect(ip: String, port: Int, code: String) {
        if (ip.isBlank() || code.isBlank()) {
            _status.value = PairStatus.Failed("IP 与配对码不能为空")
            return
        }
        if (!isValidIp(ip)) {
            _status.value = PairStatus.Failed("IP 格式不正确")
            return
        }
        _status.value = PairStatus.Loading("配对中 $ip:$port ...")
        wifiAdbRepository.pairAndConnect(
            ip,
            port,
            code,
            object : MdnsDiscoveryCallback {
                override fun onServiceFound(name: String, ip: String, port: Int) {}
                override fun onServiceLost(name: String) {}
                override fun onPairingSuccess(ip: String, port: Int) {
                    _status.value = PairStatus.Loading("配对成功，正在连接 $ip:$port ...")
                }
                override fun onPairingFailed(ip: String, port: Int) {
                    _status.value = PairStatus.Failed("配对失败：$ip:$port")
                }
                override fun onError(e: Throwable) {
                    _status.value = PairStatus.Failed("错误：${e.message ?: e.javaClass.simpleName}")
                }
            },
        )
    }

    private fun isValidIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } ?: false }
    }
}

@Composable
private fun PairingOtherDeviceScreen(
    onBack: () -> Unit,
    onNavigateToConsole: () -> Unit,
    viewModel: PairingOtherDeviceViewModel = hiltViewModel(),
) {
    val c = deviceConnectionColors
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val status by viewModel.status.collectAsState()
    val wifiState by WifiAdbConnection.state.collectAsState()
    val currentDevice by WifiAdbConnection.currentDevice.collectAsState()

    val isLoading = status is PairStatus.Loading
    val isConnected = wifiState is WifiAdbState.Connected

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text("配对其它设备", color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
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
            Text(
                "手动输入目标设备的 IP 地址、配对端口与 6 位配对码，配对成功后会自动连接。",
                color = c.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP 地址", color = c.textSecondary) },
                placeholder = { Text("如 192.168.1.100", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("配对端口", color = c.textSecondary) },
                    placeholder = { Text("如 37123", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("6 位配对码", color = c.textSecondary) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DcSecondaryButton(
                    text = "返回",
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                )
                DcPrimaryButton(
                    text = if (isLoading) "配对中..." else "配对并连接",
                    icon = Icons.Default.Link,
                    enabled = !isLoading && ip.isNotBlank() && port.toIntOrNull() in 1..65535 && code.length == 6,
                    onClick = { viewModel.pairAndConnect(ip.trim(), port.toInt(), code) },
                    modifier = Modifier.weight(1f),
                )
            }

            // 状态反馈（区分颜色）
            val (statusText, statusColor) = when (val s = status) {
                is PairStatus.Idle -> "准备就绪" to c.textSecondary
                is PairStatus.Loading -> s.msg to c.statusYellow
                is PairStatus.Success -> s.msg to c.statusGreen
                is PairStatus.Failed -> s.msg to c.statusRed
            }
            Text(statusText, color = statusColor, fontWeight = FontWeight.Medium)

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
