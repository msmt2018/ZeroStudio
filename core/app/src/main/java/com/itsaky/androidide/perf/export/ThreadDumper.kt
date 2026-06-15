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
package com.itsaky.androidide.perf.export

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.slf4j.LoggerFactory

/**
 * 线程 stack dump 工具 (PR #6/6).
 *
 * 把当前 JVM 所有线程的 stack trace dump 到 cacheDir 文件, 配合
 * [com.itsaky.androidide.perf.server.PhaseCollector] 数据可定位:
 * - ANR 发生时主线程卡在哪里
 * - 哪些后台线程在跑、占用资源
 * - 线程死锁 / 死循环
 *
 * ## 与 ANR 检测的协作
 *
 * 当 [com.itsaky.androidide.perf.monitor.AnrMonitor] 检测到 main thread
 * 往返 > 5s 时, UI 可自动调 [dumpToCache] 并通过通知 / 文件分享给用户.
 *
 * ## 线程安全
 *
 * [Thread.getAllStackTraces] 内部用 synchronized, 不会抛
 * ConcurrentModificationException. dumpToCache 在 IO 线程跑 (PR #7 UI 用
 * 协程 launch), 不会阻塞 UI.
 *
 * @author android_zero
 */
object ThreadDumper {

  private val log = LoggerFactory.getLogger(ThreadDumper::class.java)

  private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

  /**
   * Dump 所有线程 stack 到 cacheDir.
   *
   * @return 写入的文件 (含 JVM 全部活跃线程), 失败返回 null
   */
  fun dumpToCache(cacheDir: File, reason: String? = null): File? {
    return try {
      val dumpDir = File(cacheDir, DUMP_DIR)
      dumpDir.mkdirs()

      val nameSuffix = reason?.let { "_${sanitize(it)}" } ?: ""
      val file = File(dumpDir, "thread_dump_${fileNameFormat.format(Date())}$nameSuffix.txt")
      file.writeText(buildDumpString(reason))
      log.info("ThreadDumper: wrote {} ({} bytes)", file.absolutePath, file.length())
      file
    } catch (e: Throwable) {
      log.warn("ThreadDumper.dumpToCache failed: {}", e.message)
      null
    }
  }

  /** 列出 cacheDir 下所有 dump 文件 (按修改时间倒序). */
  fun listDumps(cacheDir: File): List<File> {
    val dir = File(cacheDir, DUMP_DIR)
    if (!dir.exists()) return emptyList()
    return dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
  }

  /** 删除指定 dump 文件. */
  fun deleteDump(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

  /**
   * 仅构建 dump 字符串 (不写文件). 用于测试.
   */
  fun buildDumpString(reason: String? = null): String {
    val sb = StringBuilder()
    sb.appendLine("=== Thread Dump ===")
    sb.appendLine("Timestamp: ${System.currentTimeMillis()} (${Date()})")
    if (reason != null) sb.appendLine("Reason: $reason")
    sb.appendLine("JVM: ${System.getProperty("java.vendor")} ${System.getProperty("java.version")}")
    sb.appendLine("Active thread count: ${Thread.activeCount()}")
    sb.appendLine()

    // 按线程名排序, 便于 diff
    val entries = Thread.getAllStackTraces().entries.sortedBy { it.key.name }
    entries.forEach { (thread, stack) ->
      sb.appendLine("--- Thread: ${thread.name} (id=${thread.id}, priority=${thread.priority}, state=${thread.state}) ---")
      val sw = StringWriter()
      PrintWriter(sw).use { pw: PrintWriter -> thread.printStackTrace(pw) }
      sb.append(sw.toString())
      sb.appendLine()
    }
    return sb.toString()
  }

  private fun sanitize(reason: String): String =
      reason.lowercase(Locale.US)
          .replace(Regex("[^a-z0-9]+"), "_")
          .take(MAX_REASON_LEN)

  const val DUMP_DIR = "perf/dumps"
  private const val MAX_REASON_LEN = 32
}
