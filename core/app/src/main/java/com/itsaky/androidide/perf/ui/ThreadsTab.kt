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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Threads Tab (PR #5/5).
 *
 * 显示 :perf 进程和主进程的关键线程状态 (静态, 简单信息卡).
 *
 * 实时线程 stack dump 是 O(n) 操作, 不在 PR #5 范围. 这里只列名称 + 状态.
 */
@Composable
fun ThreadsTab(state: PerfUiState, modifier: Modifier = Modifier) {
  LazyColumn(
      modifier = modifier.fillMaxSize().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { ProcessInfoCard() }
    item { ThreadsSummaryCard() }
  }
}

@Composable
private fun ProcessInfoCard() {
  Card(shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "进程信息",
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.size(8.dp))
      InfoRow(label = "进程名", value = readProcessName())
      InfoRow(label = "PID", value = android.os.Process.myPid().toString())
      InfoRow(
          label = "是否 :perf",
          value = if (readProcessName().endsWith(":perf")) "是" else "否",
      )
    }
  }
}

@Composable
private fun ThreadsSummaryCard() {
  Card(shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "线程 (活跃 ${Thread.activeCount()})",
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.size(8.dp))
      Text(
          text = "实时 stack dump 需要 root 权限, 不在 PR #5 范围",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.size(8.dp))
      val groups = Thread.getAllStackTraces().keys.groupBy { thread ->
        val name = thread.name
        when {
          name.startsWith("perf-") -> "perf"
          name == "main" -> "main"
          name.startsWith("OkHttp") -> "OkHttp"
          name.startsWith("Binder:") -> "Binder"
          else -> "其它"
        }
      }
      groups.forEach { (group, threads) ->
        InfoRow(label = group, value = "${threads.size} 线程")
      }
    }
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  androidx.compose.foundation.layout.Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
        text = label,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

private fun readProcessName(): String =
    runCatching {
      @Suppress("DEPRECATION")
      android.os.Process.myProcessName()
    }.getOrDefault("unknown")
