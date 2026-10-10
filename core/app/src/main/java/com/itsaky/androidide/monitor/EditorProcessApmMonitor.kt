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

package com.itsaky.androidide.monitor

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.services.builder.GradleBuildService
import com.itsaky.androidide.utils.executioncommand.TermuxCommand
import com.termux.shared.reflection.ReflectionUtils
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** 监控特定进程的内存统计 */
data class ProcessMemMetric(
    val pid: Int,
    val name: String,
    val pssMb: Double,
    val rssMb: Double,
    val isContainerProcess: Boolean = false,
)

/** 汇总快照，纯粹供 EditorProcessApmFragment 渲染使用 */
data class EditorProcessApmSnapshot(
    val timestampMs: Long,
    val cpuUsagePercent: Double,
    val ideMainProcess: ProcessMemMetric,
    val gradleToolingProcess: ProcessMemMetric?,
    val containerJavaProcesses: List<ProcessMemMetric>,
    val containerTotalPssMb: Double,
    val processRssMb: Double,
    val processPssMb: Double,
    val processUssMb: Double,
    val processVssMb: Double,
    val javaHeapUsedMb: Double,
    val javaHeapMaxMb: Double,
    val nativeHeapMb: Double,
    val threadCount: Int,
    val openFdCount: Int,
    val gcCount: Long,
    val gcTimeMs: Long,
    val appUptimeMs: Long,
    val termuxSubsystemStats: List<EditorSubsystemStat>,
    val pssBreakdownStats: List<EditorPssBreakdownStat>,
    val deviceThermalStat: EditorDeviceThermalStat,
    val healthAlerts: List<String>,
)

data class EditorSubsystemStat(
    val name: String,
    val processCount: Int,
    val totalCpuPercent: Double,
    val totalRssMb: Double,
)

data class EditorPssBreakdownStat(
    val category: String,
    val pssMb: Double,
)

data class EditorDeviceThermalStat(
    val batteryTempCelsius: Double?,
    val level: String,
)

/**
 * 高性能、低开销且完全按需驱动的进程 APM 监视器。
 * 替代旧版全局常驻轮询监控机制，只有在流被消费时才启动后台采样协程。
 *
 * @author android_zero
 */
class EditorProcessApmMonitor(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  private val getMemoryInfoMethod by lazy {
    runCatching {
      ReflectionUtils.getDeclaredMethod(
          Debug::class.java,
          "getMemoryInfo",
          Int::class.javaPrimitiveType,
          Debug.MemoryInfo::class.java,
      )
    }.getOrNull()
  }

  /**
   * 仅当被 collect 时在后台运行采样。
   * 取消收集时，协程退出，零后台时钟唤醒，零资源占用。
   */
  fun stream(intervalMs: Long = 1000L): Flow<EditorProcessApmSnapshot> = flow {
    var previousCpu = readCpuStat()
    val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    var ticks = 0

    // 缓存子系统统计信息，避免每次循环都执行繁重的 ps 解析
    var cachedTermuxStats: List<EditorSubsystemStat> = emptyList()
    var cachedPssBreakdown: List<EditorPssBreakdownStat> = emptyList()

    while (true) {
      val currentCpu = readCpuStat()
      val cpuUsage = calcCpuUsage(previousCpu, currentCpu)
      previousCpu = currentCpu

      // 1. 本地 IDE 主进程内存 (PSS, RSS, USS, VSS)
      val idePid = Process.myPid()
      val idePssMb = readSingleProcessPss(idePid, activityManager)
      val ideRssMb = readProcessRssMb()
      val ideUssMb = readProcessUssMb()
      val ideVssMb = readProcessVssMb()

      val ideMainMetric = ProcessMemMetric(
          pid = idePid,
          name = "IDE Main",
          pssMb = idePssMb,
          rssMb = ideRssMb,
          isContainerProcess = false,
      )

      // 2. Gradle Tooling 进程内存采样
      val toolingPid = resolveGradleToolingPid()
      val toolingMetric = if (toolingPid != null && toolingPid > 0) {
        val pss = readSingleProcessPss(toolingPid, activityManager)
        val rss = readOtherProcessRssMb(toolingPid)
        ProcessMemMetric(
            pid = toolingPid,
            name = "Gradle Tooling",
            pssMb = pss,
            rssMb = rss,
            isContainerProcess = false,
        )
      } else null

      // 3. Linux / Termux 容器内的 JDK / Java 守护进程采样
      val containerJavaMetrics = scanContainerJavaProcesses(activityManager, setOf(idePid, toolingPid ?: -1))
      val containerTotalPss = containerJavaMetrics.sumOf { it.pssMb }

      val javaHeapUsed = readJavaHeapUsedMb()
      val javaHeapMax = readJavaHeapMaxMb()
      val nativeHeap = readNativeHeapMb()
      val gcTime = readGcTimeMs()
      val thermalStat = readBatteryThermalStat()

      // 降低低频统计项更新频率（每 3 秒刷新一次结构拆解，每 5 秒刷新一次全局 ps 树）
      if (ticks % 3 == 0 || cachedPssBreakdown.isEmpty()) {
        cachedPssBreakdown = readPssBreakdownStats()
      }
      if (ticks % 5 == 0 || cachedTermuxStats.isEmpty()) {
        cachedTermuxStats = readTermuxSubsystemStats()
      }
      ticks++

      val alerts = buildHealthAlerts(
          cpuUsagePercent = cpuUsage,
          javaHeapUsedMb = javaHeapUsed,
          javaHeapMaxMb = javaHeapMax,
          gcTimeMs = gcTime,
          batteryTempC = thermalStat.batteryTempCelsius,
      )

      emit(
          EditorProcessApmSnapshot(
              timestampMs = System.currentTimeMillis(),
              cpuUsagePercent = cpuUsage,
              ideMainProcess = ideMainMetric,
              gradleToolingProcess = toolingMetric,
              containerJavaProcesses = containerJavaMetrics,
              containerTotalPssMb = containerTotalPss,
              processRssMb = ideRssMb,
              processPssMb = idePssMb,
              processUssMb = ideUssMb,
              processVssMb = ideVssMb,
              javaHeapUsedMb = javaHeapUsed,
              javaHeapMaxMb = javaHeapMax,
              nativeHeapMb = nativeHeap,
              threadCount = readThreadCount(),
              openFdCount = readOpenFdCount(),
              gcCount = readGcCount(),
              gcTimeMs = gcTime,
              appUptimeMs = SystemClock.elapsedRealtime(),
              termuxSubsystemStats = cachedTermuxStats,
              pssBreakdownStats = cachedPssBreakdown,
              deviceThermalStat = thermalStat,
              healthAlerts = alerts,
          )
      )

      delay(intervalMs)
    }
  }.flowOn(ioDispatcher)

  private fun readSingleProcessPss(pid: Int, am: ActivityManager?): Double {
    if (pid <= 0) return 0.0
    val memInfo = Debug.MemoryInfo()
    val method = getMemoryInfoMethod
    if (method != null) {
      runCatching {
        ReflectionUtils.invokeMethod(method, null, pid, memInfo)
        return memInfo.totalPss.toDouble() / 1024.0
      }
    }
    val info = am?.getProcessMemoryInfo(intArrayOf(pid))?.firstOrNull()
    return (info?.totalPss?.toDouble() ?: 0.0) / 1024.0
  }

  private fun resolveGradleToolingPid(): Int? {
    return runCatching {
      val service = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE) as? GradleBuildService
      if (service?.isToolingServerStarted() == true) {
        val meta = service.metadata().get()
        meta.pid
      } else null
    }.getOrNull()
  }

  private fun readOtherProcessRssMb(pid: Int): Double {
    val statm = File("/proc/$pid/statm")
    if (!statm.exists()) return 0.0
    val pages = runCatching {
      statm.readText().trim().split(" ").getOrNull(1)?.toLongOrNull()
    }.getOrNull() ?: return 0.0
    return (pages * 4096L).toDouble() / 1024.0 / 1024.0
  }

  /**
   * 探测系统以及 Linux / Termux 容器内部正在运行的所有 java / gradle 相关的 JDK 进程
   */
  private fun scanContainerJavaProcesses(am: ActivityManager?, excludePids: Set<Int>): List<ProcessMemMetric> {
    val procDir = File("/proc")
    val list = mutableListOf<ProcessMemMetric>()
    val pidDirs = procDir.listFiles { f -> f.isDirectory && f.name.all { it.isDigit() } } ?: return emptyList()

    for (dir in pidDirs) {
      val pid = dir.name.toIntOrNull() ?: continue
      if (pid in excludePids) continue

      val cmdlineFile = File(dir, "cmdline")
      if (!cmdlineFile.exists()) continue
      val cmdline = runCatching {
        cmdlineFile.readBytes().toString(Charsets.UTF_8).replace('\u0000', ' ').trim()
      }.getOrNull() ?: continue

      if (cmdline.isBlank()) continue

      val isJavaRelated = cmdline.contains("java") ||
          cmdline.contains("openjdk") ||
          cmdline.contains("gradle") ||
          cmdline.contains("kotlinc")

      if (isJavaRelated) {
        val pss = readSingleProcessPss(pid, am)
        val rss = readOtherProcessRssMb(pid)
        val displayName = when {
          cmdline.contains("GradleDaemon") -> "Gradle Daemon ($pid)"
          cmdline.contains("KotlinCompile") -> "Kotlin Daemon ($pid)"
          cmdline.contains("compiler") -> "JDK Compiler ($pid)"
          else -> cmdline.substringBefore(' ').substringAfterLast('/') + " ($pid)"
        }
        list.add(
            ProcessMemMetric(
                pid = pid,
                name = displayName,
                pssMb = pss,
                rssMb = rss,
                isContainerProcess = true,
            )
        )
      }
    }
    return list.sortedByDescending { it.pssMb }
  }

  private fun readJavaHeapUsedMb(): Double {
    val runtime = Runtime.getRuntime()
    return (runtime.totalMemory() - runtime.freeMemory()).toDouble() / 1024.0 / 1024.0
  }

  private fun readJavaHeapMaxMb(): Double = Runtime.getRuntime().maxMemory().toDouble() / 1024.0 / 1024.0

  private fun readNativeHeapMb(): Double = Debug.getNativeHeapAllocatedSize().toDouble() / 1024.0 / 1024.0

  private fun readOpenFdCount(): Int = File("/proc/self/fd").list()?.size ?: 0

  private fun readGcCount(): Long = Debug.getRuntimeStats()["art.gc.gc-count"]?.toLongOrNull() ?: 0L

  private fun readGcTimeMs(): Long = Debug.getRuntimeStats()["art.gc.gc-time"]?.toLongOrNull() ?: 0L

  private fun readProcessRssMb(): Double = readOtherProcessRssMb(Process.myPid())

  private fun readProcessUssMb(): Double {
    val privateKb = runCatching {
      File("/proc/self/smaps_rollup").useLines { lines ->
        lines.mapNotNull { line ->
          if (line.startsWith("Private_Clean:") || line.startsWith("Private_Dirty:")) {
            line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
          } else null
        }.sum()
      }
    }.getOrElse { 0L }
    return privateKb.toDouble() / 1024.0
  }

  private fun readProcessVssMb(): Double {
    val statm = File("/proc/self/statm")
    if (!statm.exists()) return 0.0
    val pages = runCatching { statm.readText().trim().split(" ").getOrNull(0)?.toLongOrNull() }.getOrNull() ?: return 0.0
    return (pages * 4096L).toDouble() / 1024.0 / 1024.0
  }

  private fun readPssBreakdownStats(): List<EditorPssBreakdownStat> {
    val categories = linkedMapOf(
        "Java Heap" to 0.0,
        "Native Heap" to 0.0,
        "Code" to 0.0,
        "Stack" to 0.0,
        "Graphics" to 0.0,
        "Other" to 0.0,
    )
    runCatching {
      val memoryInfo = Debug.MemoryInfo()
      Debug.getMemoryInfo(memoryInfo)
      categories["Java Heap"] = memoryInfo.dalvikPss.toDouble() / 1024.0
      categories["Native Heap"] = memoryInfo.nativePss.toDouble() / 1024.0
      categories["Code"] = (memoryInfo.getMemoryStat("summary.code")?.toIntOrNull() ?: 0).toDouble() / 1024.0
      categories["Stack"] = (memoryInfo.getMemoryStat("summary.stack")?.toIntOrNull() ?: 0).toDouble() / 1024.0
      categories["Graphics"] = (memoryInfo.getMemoryStat("summary.graphics")?.toIntOrNull() ?: 0).toDouble() / 1024.0
      categories["Other"] = memoryInfo.otherPss.toDouble() / 1024.0
    }
    return categories.map { EditorPssBreakdownStat(it.key, it.value) }
  }

  private fun readCpuStat(): CpuStat {
    val processTicks = runCatching {
      val tokens = File("/proc/self/stat").readText().trim().split(" ")
      (tokens.getOrNull(13)?.toLongOrNull() ?: 0L) + (tokens.getOrNull(14)?.toLongOrNull() ?: 0L)
    }.getOrElse { Process.getElapsedCpuTime() }

    val systemTicks = runCatching {
      val tokens = File("/proc/stat").readLines().firstOrNull()?.trim()?.split(Regex("\\s+")) ?: emptyList()
      tokens.drop(1).mapNotNull { it.toLongOrNull() }.sum()
    }.getOrElse { SystemClock.elapsedRealtime() }

    return CpuStat(processTicks = processTicks, systemTicks = max(1L, systemTicks))
  }

  private fun calcCpuUsage(previous: CpuStat, current: CpuStat): Double {
    val processDelta = current.processTicks - previous.processTicks
    val systemDelta = max(1L, current.systemTicks - previous.systemTicks)
    return ((processDelta.toDouble() / systemDelta.toDouble()) * 100.0).coerceIn(0.0, 100.0)
  }

  private suspend fun readTermuxSubsystemStats(): List<EditorSubsystemStat> {
    val result = runCatching {
      TermuxCommand.run(appContext) {
        label("APM Process Sample")
        executable("sh")
        args("-c", "ps -A -o PID,NAME,%CPU,RSS,ARGS 2>/dev/null")
      }
    }.getOrNull() ?: return emptyList()

    if (!result.isSuccess || result.stdout.isBlank()) return emptyList()

    val buckets = linkedMapOf<String, MutableSubsystemAccumulator>()
    val keywords = linkedMapOf(
        "Termux Shell" to listOf("termux", "sh", "bash", "zsh"),
        "Gradle Daemon" to listOf("gradle", "gradlew", "daemon"),
        "JVM / Compilers" to listOf("java", "openjdk", "dalvikvm", "kotlinc"),
    )

    result.stdout.lineSequence().drop(1).forEach { rawLine ->
      val line = rawLine.trim()
      if (line.isBlank()) return@forEach
      val parts = line.split(Regex("\\s+"), limit = 5)
      if (parts.size < 5) return@forEach
      val lower = parts[4].lowercase()
      val cpu = parts[2].toDoubleOrNull() ?: 0.0
      val rssKb = parts[3].toDoubleOrNull() ?: 0.0
      keywords.forEach { (bucketName, matches) ->
        if (matches.any { lower.contains(it) }) {
          val acc = buckets.getOrPut(bucketName) { MutableSubsystemAccumulator() }
          acc.processCount += 1
          acc.totalCpuPercent += cpu
          acc.totalRssMb += rssKb / 1024.0
        }
      }
    }

    return buckets.map { (name, acc) ->
      EditorSubsystemStat(
          name = name,
          processCount = acc.processCount,
          totalCpuPercent = acc.totalCpuPercent,
          totalRssMb = acc.totalRssMb,
      )
    }
  }

  private fun readThreadCount(): Int = File("/proc/self/task").list()?.size ?: 0

  private fun readBatteryThermalStat(): EditorDeviceThermalStat {
    val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val raw = intent?.getIntExtra("temperature", -1) ?: -1
    val celsius = if (raw > 0) raw / 10.0 else null
    val level = when {
      celsius == null -> "unknown"
      celsius >= 42.0 -> "danger"
      celsius >= 38.0 -> "warning"
      else -> "safe"
    }
    return EditorDeviceThermalStat(batteryTempCelsius = celsius, level = level)
  }

  private fun buildHealthAlerts(
      cpuUsagePercent: Double,
      javaHeapUsedMb: Double,
      javaHeapMaxMb: Double,
      gcTimeMs: Long,
      batteryTempC: Double?,
  ): List<String> {
    val alerts = mutableListOf<String>()
    if (cpuUsagePercent >= 85.0) alerts += "CPU 处于高负载运行状态"
    if (javaHeapMaxMb > 0 && (javaHeapUsedMb / javaHeapMaxMb) >= 0.88) alerts += "内存警告：Java Heap 超过 88%"
    if (gcTimeMs >= 300L) alerts += "GC 停顿时间偏高"
    if ((batteryTempC ?: 0.0) >= 42.0) alerts += "设备温度过高：${batteryTempC} °C"
    if (alerts.isEmpty()) alerts += "运行指标正常，无内存或卡顿风险"
    return alerts
  }

  private data class CpuStat(val processTicks: Long, val systemTicks: Long)

  private data class MutableSubsystemAccumulator(
      var processCount: Int = 0,
      var totalCpuPercent: Double = 0.0,
      var totalRssMb: Double = 0.0,
  )
}