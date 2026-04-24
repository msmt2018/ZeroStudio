package com.itsaky.androidide.fragments.output

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.itsaky.androidide.monitor.EditorProcessApmMonitor
import com.itsaky.androidide.monitor.EditorProcessApmSnapshot
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Editor bottom-sheet APM dashboard fragment rendered with Jetpack Compose. */
class EditorProcessApmFragment : Fragment() {

  private var monitorJob: Job? = null
  private val snapshotState = mutableStateOf<EditorProcessApmSnapshot?>(null)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return ComposeView(requireContext()).apply {
      setContent { EditorApmMonitorScreen(snapshot = snapshotState.value) }
    }
  }

  override fun onStart() {
    super.onStart()
    val monitor = EditorProcessApmMonitor(requireContext().applicationContext)
    monitorJob?.cancel()
    monitorJob =
        viewLifecycleOwner.lifecycleScope.launch {
          monitor.stream(intervalMs = 1000L).collect { snapshot -> snapshotState.value = snapshot }
        }
  }

  override fun onStop() {
    monitorJob?.cancel()
    super.onStop()
  }
}

@Composable
private fun EditorApmMonitorScreen(snapshot: EditorProcessApmSnapshot?) {
  val cpuHistory = remember { mutableStateListOf<Float>() }
  val pssHistory = remember { mutableStateListOf<Float>() }

  snapshot?.let {
    cpuHistory.add(it.cpuUsagePercent.toFloat())
    pssHistory.add(it.processPssMb.toFloat())
    if (cpuHistory.size > 60) cpuHistory.removeAt(0)
    if (pssHistory.size > 60) pssHistory.removeAt(0)
  }

  Scaffold { contentPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Text(
            text = "APM 实时监控",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
      }
      item {
        Text(
            text = "采样: 1s（类文件扫描: 15s）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      item {
        MetricChartCard(
            title = "CPU 使用率(%)",
            value = format(snapshot?.cpuUsagePercent, "%"),
            values = cpuHistory,
            lineColor = Color(0xFF7E57C2),
        )
      }
      item {
        MetricChartCard(
            title = "进程 PSS(MB)",
            value = format(snapshot?.processPssMb, "MB"),
            values = pssHistory,
            lineColor = Color(0xFF26A69A),
        )
      }
      item {
        MetricGrid(
            listOf(
                "RSS" to format(snapshot?.processRssMb, "MB"),
                "Java Heap" to
                    "${format(snapshot?.javaHeapUsedMb, "MB")} / ${format(snapshot?.javaHeapMaxMb, "MB")}",
                "Native Heap" to format(snapshot?.nativeHeapMb, "MB"),
                "线程数" to "${snapshot?.threadCount ?: 0}",
                "打开FD" to "${snapshot?.openFdCount ?: 0}",
                "GC次数" to "${snapshot?.gcCount ?: 0}",
                "GC耗时" to "${snapshot?.gcTimeMs ?: 0} ms",
                "Uptime" to "${(snapshot?.appUptimeMs ?: 0L) / 1000}s",
            ))
      }
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
          Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("类加载与产物统计", fontWeight = FontWeight.SemiBold)
            Text("Dex总类数: ${snapshot?.dexClassStat?.totalClassCount ?: 0}")
            Text("App包类数: ${snapshot?.dexClassStat?.appPackageClassCount ?: 0}")
            Text("*.class: ${snapshot?.classArtifactStat?.classFileCount ?: 0}")
            Text("*.clazz: ${snapshot?.classArtifactStat?.clazzFileCount ?: 0}")
            Text("*.kt: ${snapshot?.classArtifactStat?.kotlinFileCount ?: 0}")
          }
        }
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
  Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
    if (values.size < 2) return@Canvas
    val maxValue = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val minValue = values.minOrNull() ?: 0f
    val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f

    val stepX = size.width / (values.size - 1)
    val path = Path()

    values.forEachIndexed { index, value ->
      val normalized = (value - minValue) / range
      val x = stepX * index
      val y = size.height - (normalized * size.height)
      if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawLine(
        color = Color.Gray.copy(alpha = 0.4f),
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx(),
    )
    drawPath(path = path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
  }
}

private fun format(value: Double?, suffix: String): String {
  if (value == null) return "--"
  return String.format(Locale.US, "%.2f %s", value, suffix)
}
