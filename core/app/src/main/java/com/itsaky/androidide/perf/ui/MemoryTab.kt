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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Memory Tab (PR #5/5).
 *
 * 显示:
 * - 当前 PSS (大字, KB → MB)
 * - PSS 折线 (total PSS, 最近 60 秒)
 * - GC 频率 (条形, gc_count_delta)
 */
@Composable
fun MemoryTab(state: PerfUiState, modifier: Modifier = Modifier) {
  LazyColumn(
      modifier = modifier.fillMaxSize().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { PssCard(currentKb = state.recentPssKb.lastOrNull() ?: 0L, values = state.recentPssKb) }
    item { GcCard(gcDelta = state.recentGcDelta) }
  }
}

@Composable
private fun PssCard(currentKb: Long, values: List<Long>) {
  val currentMb = currentKb / 1024.0
  Card(
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
      Text(
          text = "PSS (主进程)",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
      )
      Spacer(modifier = Modifier.size(4.dp))
      Text(
          text = if (currentKb == 0L) "—" else String.format("%.1f MB", currentMb),
          fontSize = 36.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
      )
      Text(
          text = "最近 ${values.size} 秒 (60 窗口)",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
      )
      Spacer(modifier = Modifier.size(8.dp))
      Sparkline(values = values, height = 80.dp)
    }
  }
}

@Composable
private fun GcCard(gcDelta: List<Long>) {
  val totalRecent = gcDelta.sum()
  val avgPerSec = if (gcDelta.isEmpty()) 0.0 else totalRecent.toDouble() / gcDelta.size
  Card(shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "GC 频率",
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.size(4.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = String.format("平均 %.2f 次/s", avgPerSec),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "(${gcDelta.size} 样本)",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.size(8.dp))
      Sparkline(values = gcDelta, height = 60.dp)
    }
  }
}
