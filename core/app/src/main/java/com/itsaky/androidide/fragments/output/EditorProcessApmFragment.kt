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

package com.itsaky.androidide.fragments.output

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.itsaky.androidide.activities.editor.ProjectHandlerActivity
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.lsp.IDELanguageClientImpl
import com.itsaky.androidide.monitor.EditorHotClassStat
import com.itsaky.androidide.monitor.EditorProcessApmMonitor
import com.itsaky.androidide.monitor.EditorProcessApmSnapshot
import com.itsaky.androidide.monitor.EditorPssBreakdownStat
import com.itsaky.androidide.monitor.EditorSubsystemStat
import com.itsaky.androidide.monitor.ProcessMemMetric
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.resources.R as ResString
import com.itsaky.androidide.utils.executioncommand.TermuxCommand
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 彻底重构后的 APM 监控控制台：
 * 1. 采用纯 Jetpack Compose 与 Material 3 渲染。
 * 2. 独立生命周期接管：停止监控时完全取消一切后台轮询、反射与 I/O，不影响 IDE 主界面帧率。
 * 3. 监控 Linux / Termux 容器内的 JDK / Java / Gradle 进程与 Tooling 进程内存。
 * 4. 彻底解决杀容器内 Gradle/Java 进程无效的痛点。
 */
class EditorProcessApmFragment : Fragment() {

  private var monitorJob: Job? = null
  private val isMonitoringState = mutableStateOf(false)
  private val snapshotState = mutableStateOf<EditorProcessApmSnapshot?>(null)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setContent {
        MaterialTheme {
          EditorApmMonitorScreen(
              snapshot = snapshotState.value,
              isMonitoring = isMonitoringState.value,
              onToggleMonitoring = ::toggleMonitoring,
              onCleanProcesses = ::runCleanGradleJavaInContainer,
              onInAppSelfCleanup = ::runInAppSelfCleanup,
          )
        }
      }
    }
  }

  override fun onPause() {
    super.onPause()
    // 切出或关闭抽屉时停止监控，确保后台 0 资源占用
    stopMonitoring(clearData = false)
  }

  private fun toggleMonitoring() {
    if (isMonitoringState.value) {
      stopMonitoring(clearData = true)
    } else {
      startMonitoring()
    }
  }

  private fun startMonitoring() {
    if (isMonitoringState.value) return
    isMonitoringState.value = true

    val monitor = EditorProcessApmMonitor(requireContext().applicationContext)
    monitorJob?.cancel()
    monitorJob = viewLifecycleOwner.lifecycleScope.launch {
      // 启动实时数据流收集
      monitor.stream(intervalMs = 1000L).collect { snap ->
        snapshotState.value = snap
      }
    }
  }

  private fun stopMonitoring(clearData: Boolean) {
    monitorJob?.cancel()
    monitorJob = null
    isMonitoringState.value = false
    if (clearData) {
      snapshotState.value = null
    }
  }

  /**
   * 使用你建议的标准 Bash 脚本强杀 Termux/Linux 容器内的所有 Gradle 及 Java 进程
   */
  private fun runCleanGradleJavaInContainer() {
    val context = requireContext().applicationContext
    viewLifecycleOwner.lifecycleScope.launch {
      Toast.makeText(context, "正在停止容器 Gradle 并清理 Java 进程...", Toast.LENGTH_SHORT).show()

      val killScript = """
        #!/bin/bash
        echo "🔍 Stopping the Gradle daemon..."
        gradle --stop 2>/dev/null || true

        echo "🔪 Force kill all Gradle/Java related processes..."
        pkill -9 -f 'gradle.*daemon' 2>/dev/null || true
        pkill -9 -f 'java.*gradle' 2>/dev/null || true
        pkill -9 -f 'gradle' 2>/dev/null || true
        pkill -9 -f 'java' 2>/dev/null || true

        echo "✅ Gradle is all cleaned up! Memory is freed."
      """.trimIndent()

      val result = TermuxCommand.run(context) {
        label("APM-Kill-Gradle-Java")
        executable("sh")
        args("-c", killScript)
      }

      val feedback = if (result.isSuccess) {
        "Gradle/Java 容器进程已全部强杀清理完毕！"
      } else {
        "已执行清理命令，输出信息：${result.stdout.ifBlank { result.stderr }}"
      }
      Toast.makeText(context, feedback, Toast.LENGTH_LONG).show()
    }
  }

  private fun runInAppSelfCleanup() {
    viewLifecycleOwner.lifecycleScope.launch {
      (activity as? ProjectHandlerActivity)?.stopLanguageServers()
      IDELanguageClientImpl.shutdown()
      (Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE) as? BuildService)
          ?.cleanupIdleResources("apm-menu-self-clean")
      Runtime.getRuntime().gc()
      System.gc()
      Toast.makeText(requireContext(), "IDE 本地内存已完成 GC 自清理", Toast.LENGTH_SHORT).show()
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditorApmMonitorScreen(
    snapshot: EditorProcessApmSnapshot?,
    isMonitoring: Boolean,
    onToggleMonitoring: () -> Unit,
    onCleanProcesses: () -> Unit,
    onInAppSelfCleanup: () -> Unit,
) {
  val cpuHistory = remember { mutableStateListOf<Float>() }
  val idePssHistory = remember { mutableStateListOf<Float>() }
  val containerPssHistory = remember { mutableStateListOf<Float>() }
  var menuExpanded by remember { mutableStateOf(false) }

  // 监控启停联动折线图历史
  LaunchedEffect(snapshot?.timestampMs, isMonitoring) {
    if (!isMonitoring) {
      cpuHistory.clear()
      idePssHistory.clear()
      containerPssHistory.clear()
    } else {
      snapshot?.let {
        cpuHistory.add(it.cpuUsagePercent.toFloat())
        idePssHistory.add(it.ideMainProcess.pssMb.toFloat())
        val toolingPss = it.gradleToolingProcess?.pssMb ?: 0.0
        containerPssHistory.add((it.containerTotalPssMb + toolingPss).toFloat())

        if (cpuHistory.size > MAX_HISTORY_POINTS) cpuHistory.removeAt(0)
        if (idePssHistory.size > MAX_HISTORY_POINTS) idePssHistory.removeAt(0)
        if (containerPssHistory.size > MAX_HISTORY_POINTS) containerPssHistory.removeAt(0)
      }
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(ResString.string.apm_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isMonitoring) Color(0x334CAF50) else Color(0x339E9E9E),
                ) {
                  Text(
                      text = if (isMonitoring) "RUNNING" else "STOPPED",
                      color = if (isMonitoring) Color(0xFF4CAF50) else Color.Gray,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
              }
            },
            actions = {
              // 顶部快捷开始/停止按钮
              IconButton(onClick = onToggleMonitoring) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = "Toggle Monitoring",
                    tint = if (isMonitoring) Color(0xFFFF5252) else Color(0xFF4CAF50),
                )
              }
              // 下拉菜单
              IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
              }
              DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (isMonitoring) "停止实时监控" else "启动实时监控") },
                    onClick = {
                      menuExpanded = false
                      onToggleMonitoring()
                    },
                )
                DropdownMenuItem(
                    text = { Text("强杀容器内所有 Gradle/Java 进程") },
                    onClick = {
                      menuExpanded = false
                      onCleanProcesses()
                    },
                )
                DropdownMenuItem(
                    text = { Text("IDE 进程自清理 (GC/LSP 内存释放)") },
                    onClick = {
                      menuExpanded = false
                      onInAppSelfCleanup()
                    },
                )
              }
            },
        )
      },
  ) { contentPadding ->
    if (!isMonitoring) {
      Box(
          modifier = Modifier
              .fillMaxSize()
              .padding(contentPadding),
          contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
              text = "APM 监控当前已停止",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(4.dp))
          Text(
              text = "后台处于 0 线程、0 反射与 0 资源占用状态",
              fontSize = 12.sp,
              color = Color.Gray,
          )
          Spacer(Modifier.height(16.dp))
          Button(onClick = onToggleMonitoring) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("立即启动 APM 监控")
          }
        }
      }
    } else {
      LazyColumn(
          modifier = Modifier
              .fillMaxSize()
              .padding(contentPadding)
              .padding(horizontal = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        item {
          HealthAlertsCard(alerts = snapshot?.healthAlerts.orEmpty())
        }

        item {
          MetricChartCard(
              title = "IDE CPU 使用率",
              value = format(snapshot?.cpuUsagePercent, "%"),
              values = cpuHistory,
              lineColor = Color(0xFF7E57C2),
          )
        }

        item {
          MetricChartCard(
              title = "IDE 主进程 PSS 内存",
              value = format(snapshot?.ideMainProcess?.pssMb, "MB"),
              values = idePssHistory,
              lineColor = Color(0xFF26A69A),
          )
        }

        // 新增：Linux 容器及 Java/Gradle 进程总内存图表
        item {
          val toolingPss = snapshot?.gradleToolingProcess?.pssMb ?: 0.0
          val totalContainerPss = (snapshot?.containerTotalPssMb ?: 0.0) + toolingPss
          MetricChartCard(
              title = "Linux 容器 & 构建 Java 进程总内存",
              value = format(totalContainerPss, "MB"),
              values = containerPssHistory,
              lineColor = Color(0xFFFFB300),
          )
        }

        // 新增：Linux 容器内部运行的 JDK / Gradle 进程明细
        item {
          ContainerProcessesCard(
              toolingProcess = snapshot?.gradleToolingProcess,
              containerProcesses = snapshot?.containerJavaProcesses.orEmpty(),
          )
        }

        item {
          TemperatureStatusCard(snapshot = snapshot)
        }

        item {
          MetricGrid(
              listOf(
                  stringResource(ResString.string.apm_metric_rss) to format(snapshot?.processRssMb, "MB"),
                  stringResource(ResString.string.apm_metric_uss) to format(snapshot?.processUssMb, "MB"),
                  stringResource(ResString.string.apm_metric_vss) to format(snapshot?.processVssMb, "MB"),
                  stringResource(ResString.string.apm_metric_java_heap) to
                      "${format(snapshot?.javaHeapUsedMb, "MB")} / ${format(snapshot?.javaHeapMaxMb, "MB")}",
                  stringResource(ResString.string.apm_metric_native_heap) to format(snapshot?.nativeHeapMb, "MB"),
                  stringResource(ResString.string.apm_metric_thread_count) to "${snapshot?.threadCount ?: 0}",
                  stringResource(ResString.string.apm_metric_open_fd) to "${snapshot?.openFdCount ?: 0}",
                  stringResource(ResString.string.apm_metric_gc_count) to "${snapshot?.gcCount ?: 0}",
                  stringResource(ResString.string.apm_metric_gc_time) to "${snapshot?.gcTimeMs ?: 0} ms",
                  stringResource(ResString.string.apm_metric_uptime) to "${(snapshot?.appUptimeMs ?: 0L) / 1000}s",
              )
          )
        }

        item {
          PssBreakdownCard(stats = snapshot?.pssBreakdownStats.orEmpty())
        }

        item {
          TermuxSubsystemCard(stats = snapshot?.termuxSubsystemStats.orEmpty())
        }

        item {
          Spacer(Modifier.height(16.dp))
        }
      }
    }
  }
}

/**
 * 专门展示 Termux / proot 容器内部的 Java JDK 进程列表
 */
@Composable
private fun ContainerProcessesCard(
    toolingProcess: ProcessMemMetric?,
    containerProcesses: List<ProcessMemMetric>,
) {
  Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
          text = "Linux 容器 & Gradle/Java 进程明细",
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
      )

      if (toolingProcess != null) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(toolingProcess.name, fontWeight = FontWeight.Medium)
          Text(
              "${format(toolingProcess.pssMb, "MB PSS")} / ${format(toolingProcess.rssMb, "MB RSS")}",
              color = Color(0xFFFFB300),
              fontWeight = FontWeight.SemiBold,
          )
        }
      }

      if (containerProcesses.isEmpty() && toolingProcess == null) {
        Text(
            text = "当前未检测到活跃的 Java / Gradle 容器进程",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
      } else {
        containerProcesses.forEach { proc ->
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                text = proc.name,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${format(proc.pssMb, "MB PSS")} (RSS: ${format(proc.rssMb, "MB")})",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TemperatureStatusCard(snapshot: EditorProcessApmSnapshot?) {
  val temp = snapshot?.deviceThermalStat?.batteryTempCelsius
  val level = snapshot?.deviceThermalStat?.level ?: "unknown"
  val (textColor, label) = when (level) {
    "danger" -> Color(0xFFC62828) to stringResource(ResString.string.apm_temp_state_danger)
    "warning" -> Color(0xFFF9A825) to stringResource(ResString.string.apm_temp_state_warning)
    "safe" -> Color(0xFF2E7D32) to stringResource(ResString.string.apm_temp_state_safe)
    else -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(ResString.string.apm_temp_state_unknown)
  }

  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(stringResource(ResString.string.apm_temp_title), fontWeight = FontWeight.SemiBold)
      Text(
          text = stringResource(
              ResString.string.apm_temp_value,
              temp?.let { formatFloat(it) } ?: "--",
          ),
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = textColor,
      )
      Text(
          text = stringResource(ResString.string.apm_temp_state, label),
          color = textColor,
      )
    }
  }
}

@Composable
private fun HealthAlertsCard(alerts: List<String>) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(stringResource(ResString.string.apm_alerts_title), fontWeight = FontWeight.SemiBold)
      alerts.forEach { alert ->
        Text("• $alert", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
      }
    }
  }
}

@Composable
private fun MetricGrid(items: List<Pair<String, String>>) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items.chunked(2).forEach { rowItems ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        rowItems.forEach { pair ->
          Card(
              modifier = Modifier.weight(1f),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(pair.first, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.height(4.dp))
              Text(pair.second, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MetricChartCard(
    title: String,
    value: String,
    values: List<Float>,
    lineColor: Color,
) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
      }
      Spacer(modifier = Modifier.height(8.dp))
      Sparkline(values = values, lineColor = lineColor)
    }
  }
}

@Composable
private fun Sparkline(values: List<Float>, lineColor: Color) {
  Column(modifier = Modifier.fillMaxWidth()) {
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 0f
    val current = values.lastOrNull() ?: 0f
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
          stringResource(ResString.string.apm_chart_min, formatFloat(min)),
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          stringResource(ResString.string.apm_chart_now, formatFloat(current)),
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          stringResource(ResString.string.apm_chart_max, formatFloat(max)),
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        if (values.size < 2) return@Canvas
        val maxValue = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val minValue = values.minOrNull() ?: 0f
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f

        val stepX = size.width / (values.size - 1)
        val linePath = Path()
        val fillPath = Path()

        values.forEachIndexed { index, value ->
          val normalized = (value - minValue) / range
          val x = stepX * index
          val y = size.height - (normalized * size.height)
          if (index == 0) {
            linePath.moveTo(x, y)
            fillPath.moveTo(x, size.height)
            fillPath.lineTo(x, y)
          } else {
            linePath.lineTo(x, y)
            fillPath.lineTo(x, y)
          }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        val gridStep = size.height / 4f
        repeat(5) { idx ->
          val y = idx * gridStep
          drawLine(
              color = Color.Gray.copy(alpha = 0.2f),
              start = Offset(0f, y),
              end = Offset(size.width, y),
              strokeWidth = 1.dp.toPx(),
          )
        }

        drawPath(path = fillPath, color = lineColor.copy(alpha = 0.15f), style = Fill)
        drawPath(path = linePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))
      }
    }
  }
}

@Composable
private fun TermuxSubsystemCard(stats: List<EditorSubsystemStat>) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(ResString.string.apm_subsystem_title), fontWeight = FontWeight.SemiBold)
      if (stats.isEmpty()) {
        Text(stringResource(ResString.string.apm_no_subsystem_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@Column
      }
      stats.forEach { stat ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(stat.name, fontWeight = FontWeight.Medium)
          Text(
              "proc=${stat.processCount} totalCPU=${formatFloat(stat.totalCpuPercent)}% totalMem=${formatFloat(stat.totalRssMb)}MB",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun PssBreakdownCard(stats: List<EditorPssBreakdownStat>) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(ResString.string.apm_pss_breakdown_title), fontWeight = FontWeight.SemiBold)
      if (stats.isEmpty()) {
        Text(stringResource(ResString.string.apm_waiting_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@Column
      }
      stats.forEach { stat ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(stat.category, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("${formatFloat(stat.pssMb)} MB", fontWeight = FontWeight.Medium)
        }
      }
    }
  }
}

private fun format(value: Double?, suffix: String): String {
  if (value == null) return "--"
  return String.format(Locale.US, "%.2f %s", value, suffix)
}

private fun formatFloat(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun formatFloat(value: Float): String = String.format(Locale.US, "%.2f", value)

private const val MAX_HISTORY_POINTS = 60