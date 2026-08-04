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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.debugger.connection.status.ChannelStatus
import com.itsaky.androidide.debugger.root.RootState
import com.itsaky.androidide.ui.theme.deviceconnection.DcCard
import com.itsaky.androidide.ui.theme.deviceconnection.DcChannel
import com.itsaky.androidide.ui.theme.deviceconnection.DcPrimaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcSecondaryButton
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusDot
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * Root 权限卡片。
 *
 * - 图标 + 标题 / 当前管理器 + 右上状态点
 * - 三按钮：申请权限 / 管理器选择▾ / ADB 设备▾
 *
 * 「ADB 设备」按钮仅当 [rootState] 为 [RootState.Granted] 时启用。
 */
@Composable
fun RootCard(
    status: ChannelStatus?,
    rootState: RootState,
    onRequestPermission: () -> Unit,
    onPickManager: () -> Unit,
    onOpenAdbDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = deviceConnectionColors
    val granted = rootState is RootState.Granted
    DcCard(channel = DcChannel.ROOT, modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = c.channelRoot,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Root 权限", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                    val subtitle = when (rootState) {
                        is RootState.Granted -> "已授权 · ${rootState.manager.displayName}"
                        RootState.Probing -> "探测中..."
                        RootState.Idle -> "标准 su / KernelSU / Magisk / APatch"
                        RootState.Denied -> "无 root 权限"
                        is RootState.Error -> "探测失败"
                    }
                    Text(
                        subtitle,
                        color = c.textSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
                status?.let { DcStatusDot(level = it.level) }
            }
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DcPrimaryButton(
                    text = "申请权限",
                    icon = Icons.Default.Key,
                    onClick = onRequestPermission,
                    modifier = Modifier.weight(1f),
                )
                DcSecondaryButton(
                    text = "管理器",
                    icon = Icons.Default.Tune,
                    onClick = onPickManager,
                    modifier = Modifier.weight(1f),
                )
                DcSecondaryButton(
                    text = "ADB 设备",
                    icon = Icons.Default.Devices,
                    enabled = granted,
                    onClick = onOpenAdbDevices,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}