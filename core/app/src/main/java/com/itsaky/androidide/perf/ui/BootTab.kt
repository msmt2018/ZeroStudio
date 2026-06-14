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
package com.itsaky.androidide.perf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.perf.proto.PerfEvent
import com.itsaky.androidide.perf.store.BootHistoryStore

/**
 * Boot Tab (PR #5/5).
 *
 * 显示:
 * - 启动总耗时 (大字, e.g. "2.3s")
 * - 18 段 phase 时间线 (横向条形图, 按 phase 名字纵向排列)
 * - 最近 10 次启动对比 (条形对比)
 *
 * @author android_zero
 */
@Composable
fun BootTab(state: PerfUiState, modifier: Modifier = Modifier) {
  LazyColumn(
      modifier = modifier.fillMaxSize().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      TotalBootCard(totalBootMs = state.totalBootMs, isEnded = state.isBootEnded)
    }
    item {
      BootTimelineCard(bootEvents = state.bootEvents)
    }
    item {
      BootHistoryCard(history = state.bootHistory)
    }
  }
}

@Composable
private fun TotalBootCard(totalBootMs: Long, isEnded: Boolean) {
  Card(
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
      Text(
          text = "启动总耗时",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
      )
      Spacer(modifier = Modifier.size(4.dp))
      Text(
          text = formatMs(totalBootMs),
          fontSize = 36.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Text(
          text = if (isEnded) "启动完成" else "启动中…",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
      )
    }
  }
}

@Composable
private fun BootTimelineCard(bootEvents: List<PerfEvent>) {
  Card(shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "启动 phase 时间线",
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.size(8.dp))

      if (bootEvents.isEmpty()) {
        Text(
            text = "等待 IDEApplication.onCreate 上报…",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return@Column
      }

      // 计算 phase 最长耗时用于比例尺
      val phases = bootEvents.filterIsInstance<PerfEvent.Phase>()
      val maxMs = phases.maxOfOrNull { it.elapsedMs }?.coerceAtLeast(1L) ?: 1L
      val color = MaterialTheme.colorScheme.primary

      phases.forEach { phase ->
        BootTimelineRow(name = phase.name, ms = phase.elapsedMs, maxMs = maxMs, color = color)
        Spacer(modifier = Modifier.size(4.dp))
      }
    }
  }
}

@Composable
private fun BootTimelineRow(name: String, ms: Long, maxMs: Long, color: androidx.compose.ui.graphics.Color) {
  val ratio = (ms.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = name,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(0.42f),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .height(14.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(7.dp),
            ),
    ) {
      Box(
          modifier = Modifier
              .fillMaxWidth(ratio)
              .height(14.dp)
              .background(color = color, shape = RoundedCornerShape(7.dp)),
      )
    }
    Spacer(modifier = Modifier.size(8.dp))
    Text(
        text = "${ms}ms",
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun BootHistoryCard(history: List<BootHistoryStore.BootSession>) {
  Card(shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "启动历史 (最近 ${history.size} 次)",
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.size(8.dp))
      if (history.isEmpty()) {
        Text(
            text = "首次启动 — 暂无历史",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return@Column
      }
      val maxMs = history.maxOf { it.startElapsedMs }.coerceAtLeast(1L)
      history.forEachIndexed { i, session ->
        BootHistoryRow(index = history.size - i, ms = session.startElapsedMs, maxMs = maxMs)
        Spacer(modifier = Modifier.size(4.dp))
      }
    }
  }
}

@Composable
private fun BootHistoryRow(index: Int, ms: Long, maxMs: Long) {
  val ratio = (ms.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = "#${index}",
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth(0.18f),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .height(12.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
            ),
    ) {
      Box(
          modifier = Modifier
              .fillMaxWidth(ratio)
              .height(12.dp)
              .background(
                  color = MaterialTheme.colorScheme.tertiary,
                  shape = RoundedCornerShape(6.dp),
              ),
      )
    }
    Spacer(modifier = Modifier.size(8.dp))
    Text(
        text = formatMs(ms),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

internal fun formatMs(ms: Long): String =
    when {
      ms < 1000 -> "${ms}ms"
      ms < 60_000 -> String.format("%.2fs", ms / 1000.0)
      else -> String.format("%dm %ds", ms / 60_000, (ms % 60_000) / 1000)
    }
