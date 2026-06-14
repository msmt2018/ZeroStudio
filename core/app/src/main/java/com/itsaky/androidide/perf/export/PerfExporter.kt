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

import com.itsaky.androidide.perf.proto.PerfEvent
import com.itsaky.androidide.perf.server.PhaseCollector
import com.itsaky.androidide.perf.store.BootHistoryStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory

/**
 * 启动数据导出器 (PR #6/6).
 *
 * 把当前 session + 历史 session 序列化为 JSON, 写到 cacheDir.
 * 用户可:
 *
 * 1. 通过 Perf Console 的 Export 按钮 (PR #7 加 UI) 触发
 * 2. 通过 adb 手动 pull: `adb pull /data/data/<pkg>/cache/perf/exports/`
 * 3. 通过 Android Studio Profiler / Perfetto 导入做后续分析
 *
 * ## 格式
 *
 * 文件: `<cacheDir>/perf/exports/perf_<timestamp>.json`
 *
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "exportedAt": 1700000000000,
 *   "currentSession": {
 *     "startElapsedMs": 12345,
 *     "bootEnded": true,
 *     "phases": [
 *       {"name":"super_on_create","elapsedMs": 50},
 *       {"name":"init_koin","elapsedMs": 230},
 *       ...
 *     ]
 *   },
 *   "history": [
 *     {
 *       "startElapsedMs": 12000,
 *       "timestamp": 1699000000000,
 *       "phases": [...]
 *     },
 *     ...
 *   ]
 * }
 * ```
 *
 * ## 线程安全
 *
 * 所有方法用 [PhaseCollector.snapshot] / [BootHistoryStore.readAll] 返回不可变快照,
 * 业务代码可放心在 UI 线程调. 序列化 JSON 走系统 [JSONObject] (线程安全).
 *
 * @author android_zero
 */
object PerfExporter {

  private val log = LoggerFactory.getLogger(PerfExporter::class.java)

  private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

  /**
   * 导出当前 session + 历史到新文件.
   *
   * @return 写入的文件 (用于 UI 反馈 / 分享), 失败返回 null
   */
  fun exportToCache(
      cacheDir: File,
      collector: PhaseCollector?,
      historyStore: BootHistoryStore,
  ): File? {
    if (collector == null) {
      log.warn("PerfExporter.exportToCache: PhaseCollector not ready")
      return null
    }
    return try {
      val exportsDir = File(cacheDir, EXPORT_DIR)
      exportsDir.mkdirs()

      val file = File(exportsDir, "perf_${fileNameFormat.format(Date())}.json")
      val payload = buildPayload(collector, historyStore)
      file.writeText(payload.toString(2))
      log.info("PerfExporter: wrote {} ({} bytes)", file.absolutePath, file.length())
      file
    } catch (e: Throwable) {
      log.warn("PerfExporter.exportToCache failed: {}", e.message)
      null
    }
  }

  /**
   * 列出 cacheDir 下所有已导出的 JSON 文件 (按修改时间倒序).
   *
   * UI 可在 "Exports" 页面展示列表, 让用户分享 / 删除.
   */
  fun listExports(cacheDir: File): List<File> {
    val dir = File(cacheDir, EXPORT_DIR)
    if (!dir.exists()) return emptyList()
    return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()
  }

  /** 删除指定导出文件. */
  fun deleteExport(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

  /**
   * 仅构建 JSON 字符串 (不写文件). 用于测试 / 单元验证.
   */
  fun buildPayload(
      collector: PhaseCollector,
      historyStore: BootHistoryStore,
  ): JSONObject {
    val root = JSONObject()
    root.put("schemaVersion", SCHEMA_VERSION)
    root.put("exportedAt", System.currentTimeMillis())

    root.put("currentSession", buildSession(collector))

    val historyArr = JSONArray()
    historyStore.readAll().forEach { session ->
      val obj = JSONObject()
      obj.put("startElapsedMs", session.startElapsedMs)
      obj.put("timestamp", session.timestamp)
      obj.put("phases", buildPhasesArray(session.events))
      historyArr.put(obj)
    }
    root.put("history", historyArr)

    return root
  }

  private fun buildSession(collector: PhaseCollector): JSONObject {
    val obj = JSONObject()
    obj.put("startElapsedMs", collector.startElapsedMs())
    obj.put("bootEnded", collector.isBootEnded())
    obj.put("phases", buildPhasesArray(collector.snapshot()))
    return obj
  }

  private fun buildPhasesArray(events: List<PerfEvent>): JSONArray {
    val arr = JSONArray()
    events.forEach { event ->
      when (event) {
        is PerfEvent.Phase -> {
          arr.put(
              JSONObject()
                  .put("name", event.name)
                  .put("type", "phase")
                  .put("elapsedMs", event.elapsedMs)
          )
        }
        is PerfEvent.Instant -> {
          arr.put(JSONObject().put("name", event.name).put("type", "instant"))
        }
        PerfEvent.EndBoot -> {
          arr.put(JSONObject().put("name", "end_boot").put("type", "end_boot"))
        }
      }
    }
    return arr
  }

  const val EXPORT_DIR = "perf/exports"
  private const val SCHEMA_VERSION = 1
}
