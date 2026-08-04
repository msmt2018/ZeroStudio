package com.itsaky.androidide.fragments.debugger.connection

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.debugger.connection.status.ChannelStatus
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusDot
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusLevel
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * 状态通道总览条：横向列出各通道 [DcStatusDot] + label，右侧刷新按钮。
 *
 * 落实 spec §7.4：刷新中所有通道状态点强制变 YELLOW 并闪烁，刷新完成后归位。
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
                // 刷新中：所有点强制 YELLOW（DcStatusDot 内置脉冲呼吸动画）
                val level = if (refreshing) DcStatusLevel.YELLOW else status.level
                DcStatusDot(level = level, sizeDp = 10)
                Text(
                    text = if (refreshing) "${status.channel.name}·刷新中" else "${status.channel.name}·${status.label}",
                    color = c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(4.dp))
            // 刷新中：刷新按钮旋转动画
            val rotationTransition = rememberInfiniteTransition(label = "dc_refresh_rotation")
            val rotation by rotationTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Restart),
                label = "dc_refresh_rotation_value",
            )
            IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新状态",
                    tint = c.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .run { if (refreshing) rotate(rotation) else this },
                )
            }
        }
    }
}