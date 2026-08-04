package com.itsaky.androidide.fragments.debugger

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * 复用 connection 模块的 [SelfPairingService]：启动后会在通知栏显示配对码输入通知，
 * 用户在通知输入 6 位配对码即可完成自配对。
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

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun PairingOwnDeviceScreen(onBack: () -> Unit) {
    val c = deviceConnectionColors
    val context = LocalContext.current
    var serviceRunning by remember { mutableStateOf(false) }

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
                "4. 点击下方「启动服务」",
                "5. 在通知栏输入 6 位配对码",
            )
            steps.forEach { step ->
                Text(step, color = c.textSecondary)
            }
            Spacer(Modifier.width(0.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DcSecondaryButton(
                    text = "返回",
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
                if (serviceRunning) {
                    DcPrimaryButton(
                        text = "停止服务",
                        icon = Icons.Default.Stop,
                        onClick = {
                            SelfPairingService.stop(context)
                            serviceRunning = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    DcPrimaryButton(
                        text = "启动服务",
                        icon = Icons.Default.PlayArrow,
                        onClick = {
                            SelfPairingService.start(context)
                            serviceRunning = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                if (serviceRunning) "服务运行中 · 等待通知栏输入配对码" else "服务未启动",
                color = if (serviceRunning) c.statusGreen else c.textSecondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}