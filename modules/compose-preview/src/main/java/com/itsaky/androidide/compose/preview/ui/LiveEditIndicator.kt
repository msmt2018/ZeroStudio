/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itsaky.androidide.compose.preview.runtime.LiveEditState

/**
 * v2.2 P3 Live Edit 状态指示器.
 *
 * 用颜色 + 图标 + 文案传达当前 hot reload 状态:
 * - **Live (绿)**: 空闲, 监听中, 等 source change
 * - **Reloading (蓝 + 旋转)**: 正在重编译, 显示阶段文案 (Compiling / Dexing / Swapping / Rendering)
 * - **Error (红)**: 上次 reload 失败, 显示错误摘要 (单击展开详情)
 * - **Paused (灰)**: 用户暂停, 任何 source change 都被忽略
 *
 * 集成方式: 嵌入 [PreviewToolbar] 右侧, 紧凑布局 (高 32dp).
 */
@Composable
fun LiveEditIndicator(
    state: LiveEditState,
    paused: Boolean,
    lastReloadMs: Long,
    errorCount: Long,
    modifier: Modifier = Modifier,
) {
    val (bgColor, icon, label) = indicatorVisuals(state, paused, lastReloadMs, errorCount)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        IndicatorIcon(icon = icon, tint = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun IndicatorIcon(icon: ImageVector, tint: Color) {
    when (icon) {
        Icons.Filled.Bolt -> {
            // 旋转动画 — 表示正在 Reloading
            val infiniteTransition = rememberInfiniteTransition(label = "live-edit-spin")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "live-edit-spin-value",
            )
            Icon(
                imageVector = icon,
                contentDescription = "Reloading",
                tint = tint,
                modifier = Modifier.size(14.dp).rotate(rotation),
            )
        }
        else -> {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * 把 (state, paused, lastMs, errorCount) 映射到 (背景色, 图标, 文案).
 */
private data class IndicatorVisuals(
    val bg: Color,
    val icon: ImageVector,
    val label: String,
)

private fun indicatorVisuals(
    state: LiveEditState,
    paused: Boolean,
    lastReloadMs: Long,
    errorCount: Long,
): IndicatorVisuals {
    if (paused) {
        return IndicatorVisuals(
            bg = Color(0xFF9E9E9E),
            icon = Icons.Filled.PauseCircle,
            label = "Paused",
        )
    }
    return when (state) {
        is LiveEditState.Idle -> IndicatorVisuals(
            bg = if (errorCount > 0) Color(0xFFFFA000) else Color(0xFF2E7D32),
            icon = Icons.Filled.CheckCircle,
            label = if (errorCount > 0) "Live · $errorCount err" else "Live",
        )
        is LiveEditState.Debouncing -> IndicatorVisuals(
            bg = Color(0xFF1976D2),
            icon = Icons.Filled.Bolt,
            label = "Debounce",
        )
        is LiveEditState.Compiling -> IndicatorVisuals(
            bg = Color(0xFF1976D2),
            icon = Icons.Filled.Bolt,
            label = "Compiling",
        )
        is LiveEditState.Dexing -> IndicatorVisuals(
            bg = Color(0xFF1976D2),
            icon = Icons.Filled.Bolt,
            label = "Dexing",
        )
        is LiveEditState.Swapping -> IndicatorVisuals(
            bg = Color(0xFF1976D2),
            icon = Icons.Filled.Bolt,
            label = "Swapping",
        )
        is LiveEditState.Rendering -> IndicatorVisuals(
            bg = Color(0xFF1976D2),
            icon = Icons.Filled.Bolt,
            label = "Rendering",
        )
        is LiveEditState.Error -> IndicatorVisuals(
            bg = Color(0xFFD32F2F),
            icon = Icons.Filled.Error,
            label = "Error",
        )
    }
}

/**
 * 完整的 Live Edit 状态卡 (用于 DebugDrawer 顶部, 比 Indicator 更详细).
 */
@Composable
fun LiveEditStatusCard(
    state: LiveEditState,
    paused: Boolean,
    reloadCount: Long,
    errorCount: Long,
    lastReloadMs: Long,
    avgReloadMs: Double,
    lastError: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveEditIndicator(
                state = state,
                paused = paused,
                lastReloadMs = lastReloadMs,
                errorCount = errorCount,
            )
        }
        StatRow("Reloads", reloadCount.toString())
        StatRow("Errors", errorCount.toString())
        StatRow("Last", "${lastReloadMs}ms")
        StatRow("Avg", "${"%.0f".format(avgReloadMs)}ms")
        AnimatedVisibility(visible = lastError != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = lastError.orEmpty(),
                color = Color(0xFFD32F2F),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
