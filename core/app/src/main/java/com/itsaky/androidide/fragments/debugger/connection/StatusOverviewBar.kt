package com.itsaky.androidide.fragments.debugger.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.debugger.connection.status.ChannelStatus
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusDot
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * 状态通道总览条：横向列出各通道 [DcStatusDot] + label，右侧刷新按钮。
 */
@Composable
fun StatusOverviewBar(
    statuses: List<ChannelStatus>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = deviceConnectionColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = c.surfacePanel,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            statuses.forEach { status ->
                DcStatusDot(level = status.level, sizeDp = 10)
                Text(
                    text = status.channel.name + "·" + status.label,
                    color = c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新状态",
                    tint = c.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .run { if (refreshing) rotate(180f) else this },
                )
            }
        }
    }
}