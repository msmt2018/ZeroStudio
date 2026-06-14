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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.compose.preview.runtime.DexMmapPool
import com.itsaky.androidide.compose.preview.runtime.DexMmapPoolEvictor
import com.itsaky.androidide.compose.preview.runtime.DexMmapPoolRegistry
import com.itsaky.androidide.compose.preview.runtime.TimingRegistry
import kotlinx.coroutines.delay

/**
 * v2.5 P0 P3-FE-03: 性能埋点面板.
 *
 * DebugDrawer 的新 tab, 展示 5 个关键阶段 (COMPILE / DEX / CLASSLOAD / RENDER /
 * SERIALIZE) 的统计: 样本数 / 平均 / 中位 / p95 / 最大.
 *
 * 每 500ms 自动刷新 (与 [StatsPanel] 一致).
 */
@Composable
fun PerfPanel(modifier: Modifier = Modifier) {
    var snapshot by remember { mutableStateOf(TimingRegistry.snapshot()) }
    var mmapStats by remember { mutableStateOf(DexMmapPoolRegistry.stats()) }
    var evictTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = TimingRegistry.snapshot()
            mmapStats = DexMmapPoolRegistry.stats()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Performance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${snapshot.phases.values.sumOf { it.count }} samples",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = {
                    TimingRegistry.reset()
                    snapshot = TimingRegistry.snapshot()
                },
                label = { Text("Reset", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(14.dp))
                },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
        Spacer(Modifier.height(12.dp))

        // v2.5 P2: mmap 池子状态卡片
        MmapPoolSection(
            stats = mmapStats,
            onEvict = {
                val n = DexMmapPoolRegistry.evictStale(maxAgeMs = 0L)  // 立即 evict 所有 refCount=0
                evictTrigger += n
            },
        )
        Spacer(Modifier.height(8.dp))

        if (snapshot.phases.values.all { it.count == 0L }) {
            EmptyState()
            return
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(TimingRegistry.Phase.values().toList(), key = { it.name }) { phase ->
                val stats = snapshot.phases[phase] ?: TimingRegistry.PhaseStats.EMPTY
                PhaseCard(phase = phase, stats = stats)
            }
        }
    }
}

/**
 * v2.5 P2: mmap pool 状态卡片.
 *
 * 显示: activeEntries / hitRate / totalAcquires-Releases / 立即 evict 按钮.
 */
@Composable
private fun MmapPoolSection(
    stats: DexMmapPool.PoolStats,
    onEvict: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Dex MMap Pool",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = onEvict,
                    label = { Text("Evict", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(12.dp))
                    },
                )
            }
            Spacer(Modifier.height(6.dp))

            // 4 个指标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricCell("active", stats.activeEntries.toString())
                MetricCell("hit", "${(stats.hitRate * 100).toInt()}%")
                MetricCell("acq", stats.totalAcquires.toString())
                MetricCell("rel", stats.totalReleases.toString())
            }
        }
    }
}

@Composable
private fun PhaseCard(phase: TimingRegistry.Phase, stats: TimingRegistry.PhaseStats) {
    val active = stats.count > 0
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (active) 1f else 0.4f))
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = phase.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${stats.count}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))

            // avg / p50 / p95 / max 四列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricCell("avg", formatMs(stats.avgMs))
                MetricCell("p50", formatMs(stats.p50Ms))
                MetricCell("p95", formatMs(stats.p95Ms))
                MetricCell("max", formatMs(stats.maxMs.toDouble()))
            }

            if (active) {
                Spacer(Modifier.height(6.dp))
                // 进度条: p95 / max 比例 (最大值 200ms 视为满)
                val ratio = (stats.p95Ms / 200.0).coerceIn(0.0, 1.0).toFloat()
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "暂无性能数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "执行编译 / dex / render 后会自动埋点",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

private fun formatMs(value: Double): String = when {
    value <= 0.0 -> "—"
    value < 1.0 -> String.format("%.2fms", value)
    value < 100.0 -> String.format("%.1fms", value)
    else -> String.format("%.0fms", value)
}

private const val REFRESH_INTERVAL_MS = 500L
