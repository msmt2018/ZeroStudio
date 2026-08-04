package com.itsaky.androidide.fragments.debugger.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.itsaky.androidide.debugger.root.RootAdbDevice
import com.itsaky.androidide.debugger.root.RootAdbDeviceType
import com.itsaky.androidide.ui.theme.deviceconnection.DcPrimaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcSecondaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * Root ADB 设备管理弹窗。
 *
 * - 列出本机 / 无线 / USB 三组设备
 * - 当前活动设备标星
 * - 「+ 连接无线设备」展开 ip:port 输入
 * - 「↻ 刷新」触发 [onRefresh]
 */
@Composable
fun RootAdbDeviceSheet(
    devices: List<RootAdbDevice>,
    onConnectWifi: (ip: String, port: Int) -> Unit,
    onDisconnectWifi: (address: String) -> Unit,
    onSetActive: (serial: String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = deviceConnectionColors
    var showWifiInput by remember { mutableStateOf(false) }
    var ipText by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("5555") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = c.surfacePanel,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Root ADB 设备", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                    Row {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, "刷新", tint = c.primary)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "关闭", tint = c.textSecondary)
                        }
                    }
                }

                val active = devices.firstOrNull { it.isActive }
                Text(
                    "活动设备: ${active?.serial ?: "(无)"}",
                    color = c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )

                // 设备列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val grouped = devices.groupBy { it.type }
                    RootAdbDeviceType.values().forEach { type ->
                        val group = grouped[type] ?: emptyList()
                        if (group.isNotEmpty()) {
                            item {
                                Text(
                                    "${type.displayName} (${group.size})",
                                    color = c.textSecondary,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                )
                            }
                            items(group) { device ->
                                DeviceRow(
                                    device = device,
                                    onSetActive = { onSetActive(device.serial) },
                                    onDisconnect = { onDisconnectWifi(device.serial) },
                                )
                            }
                        }
                    }
                }

                // 连接无线设备
                if (showWifiInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = ipText,
                            onValueChange = { ipText = it },
                            label = { Text("IP 地址") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                            label = { Text("端口") },
                            modifier = Modifier.width(96.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DcSecondaryButton(
                            text = "取消",
                            onClick = { showWifiInput = false },
                            modifier = Modifier.weight(1f),
                        )
                        DcPrimaryButton(
                            text = "连接",
                            icon = Icons.Default.Add,
                            enabled = ipText.isNotBlank() && portText.toIntOrNull() in 1..65535,
                            onClick = {
                                onConnectWifi(ipText.trim(), portText.toInt())
                                showWifiInput = false
                                ipText = ""
                                portText = "5555"
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    DcSecondaryButton(
                        text = "+ 连接无线设备",
                        icon = Icons.Default.Add,
                        onClick = { showWifiInput = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: RootAdbDevice,
    onSetActive: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val c = deviceConnectionColors
    val icon = when (device.type) {
        RootAdbDeviceType.LOCAL -> Icons.Default.Computer
        RootAdbDeviceType.WIFI -> Icons.Default.PhoneAndroid
        RootAdbDeviceType.USB -> Icons.Default.Usb
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = c.surfaceHighlight,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(c.background, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = c.primary, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(device.serial, color = c.textPrimary, fontWeight = FontWeight.Medium)
                Text(
                    device.model ?: device.type.displayName,
                    color = c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onSetActive) {
                Icon(
                    if (device.isActive) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "设为活动",
                    tint = if (device.isActive) c.statusYellow else c.textSecondary,
                )
            }
            if (device.type == RootAdbDeviceType.WIFI) {
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.Default.LinkOff, contentDescription = "断开", tint = c.statusRed)
                }
            }
        }
    }
}