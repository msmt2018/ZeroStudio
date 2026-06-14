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
package com.itsaky.androidide.perf.monitor

import android.content.Context
import com.itsaky.androidide.perf.PhaseStore
import com.itsaky.androidide.perf.export.ThreadDumper
import com.itsaky.androidide.perf.proto.PerfEvent
import com.itsaky.androidide.perf.tracer.PerfTracer
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.slf4j.LoggerFactory

/**
 * Crash 自动 dump 现场 (Advanced / Commit 2).
 *
 * 在主进程挂掉时, 把**完整现场**写到 cacheDir:
 * 1. 全线程 stack trace (复用 PR #6 [ThreadDumper])
 * 2. 最近 perf events 列表 (从 [PhaseCollector] 读 snapshot)
 * 3. Throwable 完整堆栈 + 原因链
 * 4. 进程 / 设备 / 时间 / Perf Console 状态元数据
 *
 * ## 调用
 *
 * 在 [com.itsaky.androidide.app.IDEApplication.handleCrash] 调 [dumpCrashContext] —
 * 原 IDE 已有 CrashHandlerActivity 流程, 我们只是**追加**写文件, 不改变 crash 处理链.
 *
 * ## 输出路径
 *
 * `cacheDir/perf/crashes/crash_<yyyyMMdd_HHmmss>_<exceptionClass>.txt`
 *
 * ## 上报
 *
 * crash 发生时上报 `crash_<exceptionClass>` instant, 让 Perf Console
 * UI (BootTab CrashListCard) 能即时看到.
 *
 * @author android_zero
 */
object CrashHandler {

  private val log = LoggerFactory.getLogger(CrashHandler::class.java)

  private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
  private val CRASH_DIR = "perf/crashes"

  /**
   * Dump crash 现场到 cacheDir, 写 perf event.
   *
   * @return 写入的文件, 失败返回 null
   */
  @JvmStatic
  fun dumpCrashContext(context: Context, thread: Thread, throwable: Throwable): File? {
    log.info("CrashHandler: dumping crash context for {}", throwable.javaClass.simpleName)

    // 1. 上报 instant (UI 能即时看到)
    PerfTracer.reportInstant("crash_${throwable.javaClass.simpleName}")

    // 2. 写文件
    return try {
      val crashDir = File(context.cacheDir, CRASH_DIR)
      crashDir.mkdirs()

      val safeName = sanitize(throwable.javaClass.simpleName)
      val file =
          File(crashDir, "crash_${fileNameFormat.format(Date())}_${safeName.take(48)}.txt")
      file.writeText(buildCrashString(thread, throwable))
      log.info("CrashHandler: wrote {} ({} bytes)", file.absolutePath, file.length())
      file
    } catch (e: Throwable) {
      log.warn("CrashHandler: dump failed: {}", e.message)
      null
    }
  }

  /**
   * 构造 crash dump 字符串 (不写文件, 用于测试 / 直接走 Logcat).
   */
  @JvmStatic
  fun buildCrashString(thread: Thread, throwable: Throwable): String {
    val sb = StringBuilder()
    sb.appendLine("=== Crash Dump ===")
    sb.appendLine("Timestamp: ${System.currentTimeMillis()} (${Date()})")
    sb.appendLine("Thread: ${thread.name} (id=${thread.id}, priority=${thread.priority}, state=${thread.state})")
    sb.appendLine("Exception: ${throwable.javaClass.name}")
    sb.appendLine("Message: ${throwable.message}")
    sb.appendLine()
    sb.appendLine("--- Throwable Stack ---")
    val sw = StringWriter()
    throwable.printStackTrace(PrintWriter(sw))
    sb.append(sw.toString())
    sb.appendLine()

    // 全线程 stack
    sb.appendLine()
    sb.appendLine(ThreadDumper.buildDumpString(reason = "crash"))

    // 最近 perf events
    sb.appendLine()
    sb.appendLine("--- Recent Perf Events (last 200) ---")
    val collector = PhaseStore.collector()
    if (collector != null) {
      try {
        val snapshot = collector.snapshot()
        val recent = snapshot.takeLast(200)
        if (recent.isEmpty()) {
          sb.appendLine("  (no events yet)")
        } else {
          recent.forEach { e ->
            when (e) {
              is PerfEvent.Phase -> sb.appendLine("  phase: ${e.name} = ${e.elapsedMs}ms")
              is PerfEvent.Instant -> sb.appendLine("  instant: ${e.name}")
              PerfEvent.EndBoot -> sb.appendLine("  end_boot")
            }
          }
        }
      } catch (e: Throwable) {
        sb.appendLine("  (failed to read perf events: ${e.message})")
      }
    } else {
      sb.appendLine("  (collector not ready, :perf process not started)")
    }
    return sb.toString()
  }

  /** 列出 cacheDir 下所有 crash dump 文件 (按修改时间倒序). */
  @JvmStatic
  fun listCrashes(cacheDir: File): List<File> {
    val dir = File(cacheDir, CRASH_DIR)
    if (!dir.exists()) return emptyList()
    return dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
  }

  private fun sanitize(name: String): String =
      name.replace(Regex("[^A-Za-z0-9_]+"), "_").take(48)
}
