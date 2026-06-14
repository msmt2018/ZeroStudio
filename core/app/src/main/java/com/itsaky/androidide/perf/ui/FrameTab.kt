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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.perf.proto.PerfEvent

/**
 * Frame Tab (PR #5/5).
 *
 * 显示:
 * - 当前 FPS (大字)
 * - FPS sparkline (最近 60 秒)
 * - ANR / warn 事件列表
 */
@Composable
fun FrameTab(state: PerfUiState, modifier: Modifier = Modifier) {
  LazyColumn(
      modifier = modifier.fillMaxSize().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      FpsCard(
          currentFps = state.recentFps.lastOrNull() ?: 0,
          values = state.recentFps,
      )
    }
    item {
      AnrListCard(anrs = state.anrEvents)
    }
  }
}

@Composable
private fun FpsCard(currentFps: Int, values: List<Int>) {
  Card(
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
      Text(
          text = "FPS (主进程)",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
      )
      Spacer(modifier = Modifier.size(4.dp))
      Text(
          text = if (currentFps == 0) "—" else "$currentFps",
          fontSize = 48.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
      Text(
          text = "最近 ${values.size} 秒 (60 窗口)",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
      )
      Spacer(modifier = Modifier.size(8.dp))
      Sparkline(values = values, height = 80.dp)
    }
  }
}

@Composable
private fun AnrListCard(anrs: List<PerfEvent.Instant>) {
  Card(shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "ANR / 警告 (${anrs.size})",
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.size(8.dp))
      if (anrs.isEmpty()) {
        Text(
            text = "主线程响应良好, 无 ANR / warn",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        // 倒序: 最新在上
        anrs.reversed().forEach { anr -> AnrRow(anr) }
      }
    }
  }
}

@Composable
private fun AnrRow(anr: PerfEvent.Instant) {
  val isWarn = anr.name.startsWith("anr_warn_")
  val latency = anr.name.substringAfter("_").toLongOrNull() ?: 0L
  val color =
      if (isWarn) MaterialTheme.colorScheme.tertiary
      else MaterialTheme.colorScheme.error
  Card(
      shape = RoundedCornerShape(6.dp),
      colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
      modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
      Text(
          text = if (isWarn) "⚠ Warn" else "⛔ ANR",
          fontWeight = FontWeight.SemiBold,
          fontSize = 12.sp,
          color = color,
      )
      Text(
          text = "主线程往返: $latency ms",
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
