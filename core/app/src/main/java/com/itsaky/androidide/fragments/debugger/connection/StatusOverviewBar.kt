package com.itsaky.androidide.fragments.debugger.connection

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.debugger.connection.status.ChannelStatus
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusDot
import com.itsaky.androidide.ui.theme.deviceconnection.DcStatusLevel
import com.itsaky.androidide.ui.theme.deviceconnection.deviceConnectionColors

/**
 * 状态通道总览条：九宫格布局（每行 2 格），列出各通道 [DcStatusDot] + label，右上角刷新按钮。
 *
 * 落实 spec §7.4：刷新中所有通道状态点强制变 YELLOW 并闪烁，刷新完成后归位。
 * 九宫格避免多通道时横向溢出。
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
        Column(modifier = Modifier.padding(12.dp)) {
            // 标题行 + 刷新按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("连接状态", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
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
            Spacer(Modifier.size(10.dp))
            // 九宫格：每行 2 格
            statuses.chunked(2).forEach { rowStatuses ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowStatuses.forEach { status ->
                        StatusGridCell(
                            status = status,
                            refreshing = refreshing,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 不足 2 个时补空位保持对齐
                    if (rowStatuses.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

/** 九宫格单元：状态点 + 通道名 + label。 */
@Composable
private fun StatusGridCell(
    status: ChannelStatus,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = deviceConnectionColors
    val level = if (refreshing) DcStatusLevel.YELLOW else status.level
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = c.surfaceHighlight.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DcStatusDot(level = level, sizeDp = 10)
            Column {
                Text(
                    status.channel.displayName,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (refreshing) "刷新中" else status.label,
                    color = c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
