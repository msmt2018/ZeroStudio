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
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Search
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
 * OTG 卡片。
 *
 * - 图标 + 标题 / 设备名 + 右上状态点
 * - 两按钮：等待设备 / 管理设备
 */
@Composable
fun OtgCard(
    status: ChannelStatus?,
    onWaitDevice: () -> Unit,
    onManageDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = deviceConnectionColors
    DcCard(channel = DcChannel.OTG, modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Usb,
                        contentDescription = null,
                        tint = c.channelOtg,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("OTG", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        status?.let { "${it.label}${it.deviceName?.let { n -> " · $n" } ?: ""}" }
                            ?: "USB Host · adblib",
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
                    text = "等待设备",
                    icon = Icons.Default.Search,
                    onClick = onWaitDevice,
                    modifier = Modifier.weight(1f),
                )
                DcSecondaryButton(
                    text = "管理设备",
                    icon = Icons.Default.DevicesOther,
                    onClick = onManageDevice,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}