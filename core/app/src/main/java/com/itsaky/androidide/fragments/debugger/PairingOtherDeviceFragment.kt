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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.zero.studio.shell.wifi_adb_shell.data.repository.WifiAdbRepositoryImpl.PairingListener
import android.zero.studio.shell.wifi_adb_shell.domain.repository.WifiAdbRepository
import com.itsaky.androidide.ui.theme.deviceconnection.DeviceConnectionTheme
import com.itsaky.androidide.ui.theme.deviceconnection.DcPrimaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcSecondaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 配对其它设备页面（全屏）。手动输入 IP / 端口 / 6 位配对码。
 *
 * 复用 connection 模块的 [WifiAdbRepository.pair]。
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
                PairingOtherDeviceScreen(onBack = { dismiss() })
            }
        }
    }
}

@HiltViewModel
class PairingOtherDeviceViewModel @Inject constructor(
    private val wifiAdbRepository: WifiAdbRepository,
) : ViewModel() {

    private val _status = MutableStateFlow("准备就绪")
    val status: StateFlow<String> = _status.asStateFlow()

    fun pair(ip: String, port: Int, code: String) {
        if (ip.isBlank() || code.isBlank()) {
            _status.value = "IP 与配对码不能为空"
            return
        }
        _status.value = "配对中 $ip:$port ..."
        wifiAdbRepository.pair(
            ip,
            port,
            code,
            object : PairingListener {
                override fun onPairingSuccess() {
                    _status.value = "配对成功：$ip"
                }

                override fun onPairingFailed() {
                    _status.value = "配对失败"
                }
            },
        )
    }
}

@Composable
private fun PairingOtherDeviceScreen(
    onBack: () -> Unit,
    viewModel: PairingOtherDeviceViewModel = hiltViewModel(),
) {
    val c = deviceConnectionColors
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val status by viewModel.status.collectAsState()

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text("配对其它设备", color = c.textPrimary) },
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
                "手动输入目标设备的 IP 地址、配对端口与 6 位配对码。",
                color = c.textSecondary,
            )
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP 地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("配对端口") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("6 位配对码") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DcSecondaryButton(
                    text = "返回",
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
                DcPrimaryButton(
                    text = "配对",
                    enabled = ip.isNotBlank() && port.toIntOrNull() in 1..65535 && code.length == 6,
                    onClick = { viewModel.pair(ip.trim(), port.toInt(), code) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                status,
                color = c.textSecondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}