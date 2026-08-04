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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
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
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusLevel
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * 无线 ADB 卡片。
 *
 * - 图标 + 标题 + 右上状态点
 * - 三按钮：指南 / 配对设备▾ / 启动（连接成功后变为断开）
 * - 连接中显示 loading 指示器
 *
 * @param status 当前通道状态
 * @param connecting 是否正在连接中
 * @param connected 是否已连接
 * @param onGuide 指南回调
 * @param onPairMenu 配对菜单回调
 * @param onStart 启动回调
 * @param onDisconnect 断开回调（已连接时显示）
 */
@Composable
fun WirelessAdbCard(
    status: ChannelStatus?,
    connecting: Boolean,
    connected: Boolean,
    onGuide: () -> Unit,
    onPairMenu: () -> Unit,
    onStart: () -> Unit,
    onDisconnect: () -> Unit,
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
                    icon = androidx.compose.material.icons.Icons.Default.Cast,
                    onClick = onPairMenu,
                    modifier = Modifier.weight(1f),
                )
                if (connected) {
                    DcSecondaryButton(
                        text = "断开",
                        icon = Icons.Default.Stop,
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    DcPrimaryButton(
                        text = if (connecting) "连接中" else "启动",
                        icon = if (connecting) null else Icons.Default.PlayArrow,
                        enabled = !connecting,
                        onClick = onStart,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // 连接中 loading 指示器
            if (connecting) {
                Spacer(Modifier.size(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color = c.channelWifiAdb,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "正在连接...",
                        color = c.textSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
