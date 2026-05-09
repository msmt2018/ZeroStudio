package com.itsaky.androidide.fragments.output

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.itsaky.androidide.activities.editor.ProjectHandlerActivity
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.lsp.IDELanguageClientImpl
import com.itsaky.androidide.monitor.EditorHotClassStat
import com.itsaky.androidide.monitor.EditorPssBreakdownStat
import com.itsaky.androidide.monitor.EditorProcessApmMonitor
import com.itsaky.androidide.monitor.EditorProcessApmSnapshot
import com.itsaky.androidide.monitor.EditorSubsystemStat
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.utils.executioncommand.TermuxCommand
import com.itsaky.androidide.resources.R as ResString
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Editor bottom-sheet APM dashboard fragment rendered with Jetpack Compose. */
class EditorProcessApmFragment : Fragment() {

  private var monitorJob: Job? = null
  private var monitor: EditorProcessApmMonitor? = null
  private val snapshotState = mutableStateOf<EditorProcessApmSnapshot?>(null)
  private val isMonitoringState = mutableStateOf(false)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setContent {
        EditorApmMonitorScreen(
            snapshot = snapshotState.value,
            onCleanProcesses = ::runCleanupViaTermux,
            onInAppSelfCleanup = ::runInAppSelfCleanup,
            isMonitoring = isMonitoringState.value,
            onToggleMonitoring = ::toggleMonitoring,
        )
      }
    }
  }

  override fun onStart() {
    super.onStart()
  }

  override fun onStop() {
    stopMonitoring(clearSnapshot = false)
    super.onStop()
  }

  private fun toggleMonitoring() {
    if (isMonitoringState.value) {
      stopMonitoring(clearSnapshot = true)
    } else {
      startMonitoring()
    }
  }

  private fun startMonitoring() {
    if (isMonitoringState.value) return
    val localMonitor = monitor ?: EditorProcessApmMonitor(requireContext().applicationContext).also { monitor = it }
    monitorJob?.cancel()
    isMonitoringState.value = true
    monitorJob =
      viewLifecycleOwner.lifecycleScope.launch {
        localMonitor.stream(intervalMs = 1000L).collect { snapshot -> snapshotState.value = snapshot }
      }
  }

  private fun stopMonitoring(clearSnapshot: Boolean) {
    monitorJob?.cancel()
    monitorJob = null
    monitor = null
    isMonitoringState.value = false
    if (clearSnapshot) snapshotState.value = null
  }

  private fun runCleanupViaTermux() {
    viewLifecycleOwner.lifecycleScope.launch {
      TermuxCommand.run(requireContext().applicationContext) {
        label("APM Cleanup Gradle/JVM")
        executable("sh")
        args(
            "-c",
            "gradle --stop 2>/dev/null; " +
                "pkill -f 'gradle.*daemon' 2>/dev/null; " +
                "pkill -f 'java.*gradle' 2>/dev/null; " +
                "pkill -f 'gradle' 2>/dev/null; " +
                "echo 'Gradle and Java cleanup done.'",
        )
      }
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
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditorApmMonitorScreen(
    snapshot: EditorProcessApmSnapshot?,
    onCleanProcesses: () -> Unit,
    onInAppSelfCleanup: () -> Unit,
    isMonitoring: Boolean,
    onToggleMonitoring: () -> Unit,
) {
  val cpuHistory = remember { mutableStateListOf<Float>() }
  val pssHistory = remember { mutableStateListOf<Float>() }
  var menuExpanded by remember { mutableStateOf(false) }

  LaunchedEffect(snapshot?.timestampMs) {
    snapshot?.let {
      cpuHistory.add(it.cpuUsagePercent.toFloat())
      pssHistory.add(it.processPssMb.toFloat())
      if (cpuHistory.size > MAX_HISTORY_POINTS) cpuHistory.removeAt(0)
      if (pssHistory.size > MAX_HISTORY_POINTS) pssHistory.removeAt(0)
    }
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(stringResource(ResString.string.apm_title)) },
            actions = {
              IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(ResString.string.apm_menu_more))
              }
              DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(ResString.string.apm_menu_clean_gradle_java)) },
                    onClick = {
                      menuExpanded = false
                      onCleanProcesses()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (isMonitoring) stringResource(ResString.string.apm_stop_monitoring) else stringResource(ResString.string.apm_start_monitoring)) },
                    onClick = {
                      menuExpanded = false
                      onToggleMonitoring()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(ResString.string.apm_menu_self_cleanup)) },
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
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Text(
            text = if (isMonitoring) stringResource(ResString.string.apm_sampling_desc) else stringResource(ResString.string.apm_monitoring_paused_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item {
        MetricChartCard(
            title = stringResource(ResString.string.apm_cpu_usage),
            value = format(snapshot?.cpuUsagePercent, "%"),
            values = cpuHistory,
            lineColor = Color(0xFF7E57C2),
        )
      }
      item {
        MetricChartCard(
            title = stringResource(ResString.string.apm_process_pss),
            value = format(snapshot?.processPssMb, "MB"),
            values = pssHistory,
            lineColor = Color(0xFF26A69A),
        )
      }
      item {
        TemperatureStatusCard(snapshot = snapshot)
      }
      item {
        AdvancedOverviewCard(snapshot = snapshot)
      }
      item {
        HealthAlertsCard(alerts = snapshot?.healthAlerts.orEmpty())
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
            ))
      }
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
          Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(ResString.string.apm_class_loading_stats), fontWeight = FontWeight.SemiBold)
            Text(stringResource(ResString.string.apm_dex_total_classes, snapshot?.dexClassStat?.totalClassCount ?: 0))
            Text(stringResource(ResString.string.apm_app_package_classes, snapshot?.dexClassStat?.appPackageClassCount ?: 0))
            Text(stringResource(ResString.string.apm_class_file_count, snapshot?.classArtifactStat?.classFileCount ?: 0))
            Text(stringResource(ResString.string.apm_clazz_file_count, snapshot?.classArtifactStat?.clazzFileCount ?: 0))
            Text(stringResource(ResString.string.apm_kt_file_count, snapshot?.classArtifactStat?.kotlinFileCount ?: 0))
          }
        }
      }
      item {
        PssBreakdownCard(stats = snapshot?.pssBreakdownStats.orEmpty())
      }
      item {
        RuntimeSignalsCard(snapshot = snapshot)
      }
      item {
        AdvancedHookCapabilityCard(snapshot = snapshot)
      }
      item {
        HotClassActivityCard(classStats = snapshot?.hotClassStats.orEmpty())
      }
      item {
        TermuxSubsystemCard(stats = snapshot?.termuxSubsystemStats.orEmpty())
      }
    }
  }
}

@Composable
private fun TemperatureStatusCard(snapshot: EditorProcessApmSnapshot?) {
  val temp = snapshot?.deviceThermalStat?.batteryTempCelsius
  val level = snapshot?.deviceThermalStat?.level ?: "unknown"
  val (textColor, label) =
      when (level) {
        "danger" -> Color(0xFFC62828) to stringResource(ResString.string.apm_temp_state_danger)
        "warning" -> Color(0xFFF9A825) to stringResource(ResString.string.apm_temp_state_warning)
        "safe" -> Color(0xFF2E7D32) to stringResource(ResString.string.apm_temp_state_safe)
        else -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(ResString.string.apm_temp_state_unknown)
      }

  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(stringResource(ResString.string.apm_temp_title), fontWeight = FontWeight.SemiBold)
      Text(
          stringResource(
              ResString.string.apm_temp_value,
              temp?.let { formatFloat(it) } ?: "--",
          ),
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = textColor,
      )
      Text(
          stringResource(ResString.string.apm_temp_state, label),
          color = textColor,
      )
    }
  }
}

@Composable
private fun AdvancedOverviewCard(snapshot: EditorProcessApmSnapshot?) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(stringResource(ResString.string.apm_advanced_capabilities), fontWeight = FontWeight.SemiBold)
      Text(stringResource(ResString.string.apm_cpu_apm_desc))
      Text(stringResource(ResString.string.apm_memory_desc))
      Text(stringResource(ResString.string.apm_gc_desc))
      val pressureLevel = when {
        (snapshot?.cpuUsagePercent ?: 0.0) >= 80.0 -> stringResource(ResString.string.apm_pressure_high)
        (snapshot?.cpuUsagePercent ?: 0.0) >= 40.0 -> stringResource(ResString.string.apm_pressure_medium)
        else -> stringResource(ResString.string.apm_pressure_low)
      }
      Text(stringResource(ResString.string.apm_pressure_level, pressureLevel))
      Text(
          stringResource(ResString.string.apm_hot_class_disclaimer),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp,
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
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
      Text(stringResource(ResString.string.apm_chart_min, formatFloat(min)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(stringResource(ResString.string.apm_chart_now, formatFloat(current)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(stringResource(ResString.string.apm_chart_max, formatFloat(max)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(modifier = Modifier.height(6.dp))
    Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
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
              color = Color.Gray.copy(alpha = 0.22f),
              start = Offset(0f, y),
              end = Offset(size.width, y),
              strokeWidth = 1.dp.toPx(),
          )
        }

        drawPath(path = fillPath, color = lineColor.copy(alpha = 0.14f), style = Fill)
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
private fun HotClassActivityCard(classStats: List<EditorHotClassStat>) {
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(ResString.string.apm_hot_class_title), fontWeight = FontWeight.SemiBold)
      if (classStats.isEmpty()) {
        Text(stringResource(ResString.string.apm_waiting_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@Column
      }

      classStats.take(15).forEach { stat ->
        Column(
            modifier =
                Modifier.fillMaxWidth().clickable {
                  val payload =
                      buildString {
                        appendLine("Class: ${stat.className}")
                        appendLine("calls=${stat.calls}")
                        appendLine("totalCPU=${formatFloat(stat.totalCpuMs)} ms")
                        appendLine("avgCPU=${formatFloat(stat.avgCpuMs)} ms")
                        appendLine("totalMem=${formatFloat(stat.totalMemMb)} MB")
                        appendLine("avgMem=${formatFloat(stat.avgMemMb)} MB")
                        appendLine("peakA=${formatFloat(stat.peakMemMb)} MB")
                      }
                  clipboardManager.setText(AnnotatedString(payload))
                  Toast.makeText(context, "已复制热点类指标到剪切板", Toast.LENGTH_SHORT).show()
                },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(stat.className, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
              "该类累计采样 ${stat.calls} 次，累计CPU ${formatFloat(stat.totalCpuMs)}ms，平均每次 ${formatFloat(stat.avgCpuMs)}ms。",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricPill(
                label = "调用",
                value = stat.calls.toString(),
                color = callsSeverityColor(stat.calls),
                modifier = Modifier.weight(1f),
            )
            MetricPill(
                label = "总CPU",
                value = "${formatFloat(stat.totalCpuMs)}ms",
                color = cpuSeverityColor(stat.totalCpuMs),
                modifier = Modifier.weight(1f),
            )
            MetricPill(
                label = "均CPU",
                value = "${formatFloat(stat.avgCpuMs)}ms",
                color = cpuLatencyColor(stat.avgCpuMs),
                modifier = Modifier.weight(1f),
            )
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricPill(
                label = "总内存",
                value = "${formatFloat(stat.totalMemMb)}MB",
                color = memSeverityColor(stat.totalMemMb),
                modifier = Modifier.weight(1f),
            )
            MetricPill(
                label = "均内存",
                value = "${formatFloat(stat.avgMemMb)}MB",
                color = memSeverityColor(stat.avgMemMb),
                modifier = Modifier.weight(1f),
            )
            MetricPill(
                label = "峰值",
                value = "${formatFloat(stat.peakMemMb)}MB",
                color = memSeverityColor(stat.peakMemMb),
                modifier = Modifier.weight(1f),
            )
          }
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

@Composable
private fun RuntimeSignalsCard(snapshot: EditorProcessApmSnapshot?) {
  val threadMax = snapshot?.topThreadStats?.maxOfOrNull { it.cpuDeltaTicks }?.coerceAtLeast(1L) ?: 1L
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(ResString.string.apm_runtime_signals_title), fontWeight = FontWeight.SemiBold)
      SignalProgressRow(
          label = stringResource(ResString.string.apm_runtime_cpu_user),
          value = "${snapshot?.cpuBreakdownStat?.userCpuMs ?: 0L}ms",
          progress = ((snapshot?.cpuBreakdownStat?.userCpuMs ?: 0L) / 2000f).coerceIn(0f, 1f),
          tint = Color(0xFF7E57C2),
      )
      SignalProgressRow(
          label = stringResource(ResString.string.apm_runtime_cpu_system),
          value = "${snapshot?.cpuBreakdownStat?.systemCpuMs ?: 0L}ms",
          progress = ((snapshot?.cpuBreakdownStat?.systemCpuMs ?: 0L) / 2000f).coerceIn(0f, 1f),
          tint = Color(0xFF5C6BC0),
      )
      SignalProgressRow(
          label = stringResource(ResString.string.apm_runtime_io_read),
          value = "${snapshot?.ioStat?.readBytes ?: 0L}B",
          progress = (((snapshot?.ioStat?.readBytes ?: 0L).toFloat()) / (1024f * 1024f)).coerceIn(0f, 1f),
          tint = Color(0xFF00897B),
      )
      SignalProgressRow(
          label = stringResource(ResString.string.apm_runtime_io_write),
          value = "${snapshot?.ioStat?.writeBytes ?: 0L}B",
          progress = (((snapshot?.ioStat?.writeBytes ?: 0L).toFloat()) / (1024f * 1024f)).coerceIn(0f, 1f),
          tint = Color(0xFFF57C00),
      )
      SignalProgressRow(
          label = stringResource(ResString.string.apm_runtime_jank_ratio),
          value = "${formatFloat(snapshot?.frameJankStat?.frameDropPercent ?: 0.0)}%",
          progress = (((snapshot?.frameJankStat?.frameDropPercent ?: 0.0) / 100.0).toFloat()).coerceIn(0f, 1f),
          tint = Color(0xFFC62828),
      )
      if (snapshot?.topThreadStats?.isNotEmpty() == true) {
        Text(stringResource(ResString.string.apm_runtime_top_threads), fontWeight = FontWeight.Medium)
        snapshot.topThreadStats.take(5).forEach { thread ->
          val progress = (thread.cpuDeltaTicks.toFloat() / threadMax.toFloat()).coerceIn(0f, 1f)
          SignalProgressRow(
              label = "T${thread.tid} ${thread.name}",
              value = "Δ${thread.cpuDeltaTicks}",
              progress = progress,
              tint = Color(0xFF3949AB),
          )
        }
      }
    }
  }
}

@Composable
private fun SignalProgressRow(
    label: String,
    value: String,
    progress: Float,
    tint: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = tint)
  }
}

@Composable
private fun AdvancedHookCapabilityCard(snapshot: EditorProcessApmSnapshot?) {
  val stat = snapshot?.advancedHookStat
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(ResString.string.apm_hook_title), fontWeight = FontWeight.SemiBold)
      HookStateRow("JVMTI", stat?.jvmtiReady == true)
      HookStateRow("PLT Hook", stat?.pltHookReady == true)
      HookStateRow("Inline Hook", stat?.inlineHookReady == true)
      HookStateRow("malloc hook", stat?.mallocHookReady == true)
      HookStateRow("libmemunreachable", stat?.memUnreachableReady == true)
      stat?.notes?.take(3)?.forEach {
        Text("• $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun HookStateRow(name: String, ready: Boolean) {
  val tint = if (ready) Color(0xFF2E7D32) else Color(0xFFF9A825)
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(if (ready) stringResource(ResString.string.apm_hook_ready) else stringResource(ResString.string.apm_hook_not_ready), color = tint, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
  Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.16f))) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
      Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
      Text(value, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
    }
  }
}

private fun cpuLatencyColor(avgCpuMs: Double): Color =
    when {
      avgCpuMs < 16 -> Color(0xFF2E7D32)
      avgCpuMs < 50 -> Color(0xFFF9A825)
      else -> Color(0xFFC62828)
    }

private fun cpuSeverityColor(totalCpuMs: Double): Color =
    when {
      totalCpuMs < 500 -> Color(0xFF2E7D32)
      totalCpuMs < 2000 -> Color(0xFFF9A825)
      else -> Color(0xFFC62828)
    }

private fun callsSeverityColor(calls: Int): Color =
    when {
      calls < 10 -> Color(0xFF2E7D32)
      calls < 50 -> Color(0xFFF9A825)
      else -> Color(0xFFC62828)
    }

private fun memSeverityColor(memMb: Double): Color =
    when {
      memMb < 1.0 -> Color(0xFF2E7D32)
      memMb < 8.0 -> Color(0xFFF9A825)
      else -> Color(0xFFC62828)
    }

private fun format(value: Double?, suffix: String): String {
  if (value == null) return "--"
  return String.format(Locale.US, "%.2f %s", value, suffix)
}

private fun formatFloat(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun formatFloat(value: Float): String = String.format(Locale.US, "%.2f", value)

private const val MAX_HISTORY_POINTS = 180
