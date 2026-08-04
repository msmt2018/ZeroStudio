package com.itsaky.androidide.fragments.debugger.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.debugger.connection.status.ChannelStatus
import com.itsaky.androidide.ui.theme.deviceconnection.DcCard
import com.itsaky.androidide.ui.theme.deviceconnection.DcChannel
import com.itsaky.androidide.ui.theme.deviceconnection.DcPrimaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcSecondaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusDot
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * 无线 ADB 卡片。
 *
 * - 图标 + 标题 + 右上状态点
 * - 三按钮：指南 / 配对设备▾ / 启动
 */
@Composable
fun WirelessAdbCard(
    status: ChannelStatus?,
    onGuide: () -> Unit,
    onPairMenu: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = deviceConnectionColors
    DcCard(channel = DcChannel.WIFI_ADB, modifier = modifier) {
        Column {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = c.channelWifiAdb,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("无线 ADB", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        status?.let { "${it.label}${it.deviceName?.let { n -> " · $n" } ?: ""}" }
                            ?: "Shizuku 服务 / 无线调试",
                        color = c.textSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
                status?.let { DcStatusDot(level = it.level) }
            }
            Spacer(Modifier.size(12.dp))
            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DcSecondaryButton(
                    text = "指南",
                    icon = Icons.Default.HelpOutline,
                    onClick = onGuide,
                    modifier = Modifier.weight(1f),
                )
                DcSecondaryButton(
                    text = "配对设备",
                    icon = Icons.Default.Bluetooth,
                    onClick = onPairMenu,
                    modifier = Modifier.weight(1f),
                )
                DcPrimaryButton(
                    text = "启动",
                    icon = Icons.Default.PlayArrow,
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}