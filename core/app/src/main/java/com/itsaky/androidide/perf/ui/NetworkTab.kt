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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsaky.androidide.perf.proto.PerfEvent

/**
 * Network Tab (PR #9/N).
 *
 * 显示 OkHttp interceptor 上报的 HTTP 请求:
 * - **请求总数** (大字) + 错误数
 * - **平均延迟** (P50/P90/P99 if 有足够样本)
 * - **Top hosts** 按请求数排序
 * - **状态码分布** (2xx / 3xx / 4xx / 5xx)
 * - **最近 30 个请求** 列表 (method + host + status + latency)
 *
 * Event name 解析:
 * - `net_<METHOD>_<host>_<code>_<latencyMs>ms` → 成功
 * - `net_err_<METHOD>_<host>_<Exception>` → 失败
 */
@Composable
fun NetworkTab(state: PerfUiState, modifier: Modifier = Modifier) {
  val events = state.networkEvents
  // 仅展示最近 N 个, 避免 LazyColumn 一次渲染太多
  val recent = events.takeLast(30)

  LazyColumn(
      modifier = modifier.fillMaxSize().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { NetworkSummaryCard(events = events) }
    item { TopHostsCard(events = events) }
    item { StatusDistributionCard(events = events) }
    item { RecentRequestsCard(events = recent) }
  }
}

/**
 * 解析 event name → (method, host, status, latencyMs, isError)
 *
 * 例: `net_GET_api.rikka-ai.com_200_342ms`
 * 例: `net_err_POST_api.x.com_ConnectException`
 */
private data class ParsedNetEvent(
    val method: String,
    val host: String,
    val statusOrError: String,
    val latencyMs: Long,
    val isError: Boolean,
)

private fun parseNetEvent(name: String): ParsedNetEvent? {
  if (!name.startsWith("net_")) return null
  val rest = name.substring(4)
  val isError = rest.startsWith("err_")
  val rest2 = if (isError) rest.substring(4) else rest

  // 切分: method_host_codeOrEx_latency
  // method: 首段 (无 _)
  // host: 中间段 (可含 .)
  // code/ex: 倒数第二段
  // latency: 最后段 (ms / 异常类)
  val parts = rest2.split("_")
  if (parts.size < 3) return null
  val method = parts[0]
  val codeOrEx = parts[parts.size - 2]
  val latencyOrType = parts[parts.size - 1]
  val host = parts.subList(1, parts.size - 2).joinToString("_")

  val latencyMs =
      when {
        isError -> 0L
        latencyOrType.endsWith("ms") ->
            latencyOrType.removeSuffix("ms").toLongOrNull() ?: 0L
        else -> 0L
      }

  return ParsedNetEvent(
      method = method,
      host = host.ifEmpty { "?" },
      statusOrError = codeOrEx,
      latencyMs = latencyMs,
      isError = isError,
  )
}

@Composable
private fun NetworkSummaryCard(events: List<PerfEvent.Instant>) {
  val parsed = events.mapNotNull { parseNetEvent(it.name) }
  val total = parsed.size
  val errors = parsed.count { it.isError }
  val successful = parsed.filter { !it.isError }
  val avgLatency =
      if (successful.isEmpty()) 0L else successful.sumOf { it.latencyMs } / successful.size
  val maxLatency = successful.maxOfOrNull { it.latencyMs } ?: 0L

  Card(
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
      Text(
          text = "网络请求 (主进程)",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
      )
      Spacer(modifier = Modifier.size(4.dp))
      Text(
          text = if (total == 0) "—" else "$total",
          fontSize = 48.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Text(
          text = "总请求  ·  $errors 错误  ·  平均 ${avgLatency}ms  ·  最慢 ${maxLatency}ms",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
      )
    }
  }
}

@Composable
private fun TopHostsCard(events: List<PerfEvent.Instant>) {
  val parsed = events.mapNotNull { parseNetEvent(it.name) }
  val byHost = parsed.groupingBy { it.host }.eachCount()
  val top = byHost.entries.sortedByDescending { it.value }.take(5)

  Card(shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "Top Hosts",
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.size(8.dp))
      if (top.isEmpty()) {
        Text(
            text = "暂无网络请求",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        top.forEach { (host, count) ->
          Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                text = host,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(0.75f),
            )
            Text(
                text = "×$count",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StatusDistributionCard(events: List<PerfEvent.Instant>) {
  val parsed = events.mapNotNull { parseNetEvent(it.name) }
  val byStatus = parsed.filter { !it.isError }.groupingBy { it.statusOrError.firstOrNull() ?: '?' }
  val twoxx = byStatus.eachCount()['2'] ?: 0
  val threexx = byStatus.eachCount()['3'] ?: 0
  val fourxx = byStatus.eachCount()['4'] ?: 0
  val fivexx = byStatus.eachCount()['5'] ?: 0
  val errors = parsed.count { it.isError }

  Card(shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "状态码分布",
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.size(8.dp))
      StatusRow("2xx", twoxx, MaterialTheme.colorScheme.primary)
      StatusRow("3xx", threexx, MaterialTheme.colorScheme.tertiary)
      StatusRow("4xx", fourxx, MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
      StatusRow("5xx", fivexx, MaterialTheme.colorScheme.error)
      StatusRow("ERR", errors, MaterialTheme.colorScheme.error)
    }
  }
}

@Composable
private fun StatusRow(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = color,
    )
    Text(
        text = "×$count",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun RecentRequestsCard(events: List<PerfEvent.Instant>) {
  Card(shape = RoundedCornerShape(12.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text(
          text = "最近 ${events.size} 个请求",
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.size(8.dp))
      if (events.isEmpty()) {
        Text(
            text = "暂无网络请求",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        // 倒序: 最新在上
        events.reversed().forEach { NetEventRow(it) }
      }
    }
  }
}

@Composable
private fun NetEventRow(event: PerfEvent.Instant) {
  val parsed = parseNetEvent(event.name)
  val bgColor =
      when {
          parsed == null -> MaterialTheme.colorScheme.surfaceVariant
          parsed.isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
          else -> MaterialTheme.colorScheme.surfaceVariant
      }
  Card(
      shape = RoundedCornerShape(6.dp),
      colors = CardDefaults.cardColors(containerColor = bgColor),
      modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.fillMaxWidth(0.7f)) {
        Text(
            text = "${parsed?.method ?: "?"} ${parsed?.host ?: event.name}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = parsed?.statusOrError ?: "?",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
          text = if (parsed != null && !parsed.isError) "${parsed.latencyMs}ms" else "—",
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color =
              if (parsed != null && !parsed.isError && parsed.latencyMs > 1000)
                  MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
