package com.itsaky.androidide.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import dalvik.system.DexFile
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class EditorClassArtifactStat(
    val classFileCount: Int,
    val clazzFileCount: Int,
    val kotlinFileCount: Int,
)

data class EditorDexClassStat(
    val totalClassCount: Int,
    val appPackageClassCount: Int,
)

data class EditorProcessApmSnapshot(
    val timestampMs: Long,
    val cpuUsagePercent: Double,
    val processRssMb: Double,
    val processPssMb: Double,
    val javaHeapUsedMb: Double,
    val javaHeapMaxMb: Double,
    val nativeHeapMb: Double,
    val threadCount: Int,
    val openFdCount: Int,
    val gcCount: Long,
    val gcTimeMs: Long,
    val appUptimeMs: Long,
    val dexClassStat: EditorDexClassStat,
    val classArtifactStat: EditorClassArtifactStat,
)

/** Non-intrusive process-level APM monitor for editor bottom sheet dashboard. */
class EditorProcessApmMonitor(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  fun stream(intervalMs: Long = 1000L): Flow<EditorProcessApmSnapshot> = flow {
    var previous = readCpuStat()
    val dexStat = readDexClassStat()
    var classArtifactStat = scanClassArtifacts()
    var ticks = 0

    while (true) {
      val current = readCpuStat()
      val cpuUsage = calcCpuUsage(previous, current)
      previous = current

      if (ticks % 15 == 0) {
        classArtifactStat = scanClassArtifacts()
      }
      ticks++

      emit(
          EditorProcessApmSnapshot(
              timestampMs = System.currentTimeMillis(),
              cpuUsagePercent = cpuUsage,
              processRssMb = readProcessRssMb(),
              processPssMb = readProcessPssMb(),
              javaHeapUsedMb = readJavaHeapUsedMb(),
              javaHeapMaxMb = readJavaHeapMaxMb(),
              nativeHeapMb = readNativeHeapMb(),
              threadCount = Thread.getAllStackTraces().size,
              openFdCount = readOpenFdCount(),
              gcCount = readGcCount(),
              gcTimeMs = readGcTimeMs(),
              appUptimeMs = android.os.SystemClock.elapsedRealtime(),
              dexClassStat = dexStat,
              classArtifactStat = classArtifactStat,
          )
      )
      delay(intervalMs)
    }
  }.flowOn(ioDispatcher)

  private fun readProcessPssMb(): Double {
    val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = am.getProcessMemoryInfo(intArrayOf(Process.myPid())).firstOrNull()
    return (info?.totalPss?.toDouble() ?: 0.0) / 1024.0
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

  private fun readProcessRssMb(): Double {
    val statm = File("/proc/self/statm")
    if (!statm.exists()) return 0.0
    val pages = statm.readText().trim().split(" ").getOrNull(1)?.toLongOrNull() ?: return 0.0
    return (pages * 4096L).toDouble() / 1024.0 / 1024.0
  }

  private fun readDexClassStat(): EditorDexClassStat {
    return runCatching {
          val sourceDir = appContext.applicationInfo.sourceDir
          val appPackage = appContext.packageName
          var total = 0
          var inApp = 0

          DexFile(sourceDir).entries().asSequence().forEach { className ->
            total++
            if (className.startsWith(appPackage)) {
              inApp++
            }
          }

          EditorDexClassStat(totalClassCount = total, appPackageClassCount = inApp)
        }
        .getOrElse { EditorDexClassStat(totalClassCount = 0, appPackageClassCount = 0) }
  }

  private fun scanClassArtifacts(): EditorClassArtifactStat {
    val roots =
        listOfNotNull(
            appContext.filesDir,
            appContext.cacheDir,
            appContext.codeCacheDir,
            appContext.externalCacheDir,
            appContext.getExternalFilesDir(null),
        )

    var classCount = 0
    var clazzCount = 0
    var ktCount = 0

    roots.forEach { root ->
      root.walkTopDown().forEach { file ->
        if (!file.isFile) return@forEach
        when {
          file.name.endsWith(".class", ignoreCase = true) -> classCount++
          file.name.endsWith(".clazz", ignoreCase = true) -> clazzCount++
          file.name.endsWith(".kt", ignoreCase = true) -> ktCount++
        }
      }
    }

    return EditorClassArtifactStat(classCount, clazzCount, ktCount)
  }

  private fun readCpuStat(): CpuStat {
    val processTokens = File("/proc/self/stat").readText().trim().split(" ")
    val processTicks =
        (processTokens.getOrNull(13)?.toLongOrNull() ?: 0L) +
            (processTokens.getOrNull(14)?.toLongOrNull() ?: 0L)

    val systemTokens = File("/proc/stat").readLines().firstOrNull()?.trim()?.split(Regex("\\s+")) ?: emptyList()
    val systemTicks = systemTokens.drop(1).mapNotNull { it.toLongOrNull() }.sum()
    return CpuStat(processTicks = processTicks, systemTicks = systemTicks)
  }

  private fun calcCpuUsage(previous: CpuStat, current: CpuStat): Double {
    val processDelta = current.processTicks - previous.processTicks
    val systemDelta = max(1L, current.systemTicks - previous.systemTicks)
    return (processDelta.toDouble() / systemDelta.toDouble()) * 100.0
  }

  private data class CpuStat(
      val processTicks: Long,
      val systemTicks: Long,
  )
}
