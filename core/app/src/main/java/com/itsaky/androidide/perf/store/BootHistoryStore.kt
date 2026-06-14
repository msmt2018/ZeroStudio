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
package com.itsaky.androidide.perf.store

import android.content.Context
import com.itsaky.androidide.perf.proto.PerfEvent
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory

/**
 * 启动历史持久化 (PR #4/5).
 *
 * 主进程启动结束后, 序列化当次启动的 [PerfEvent] 列表到本地文件,
 * 下次启动时读出来, UI 显示"最近 10 次启动耗时对比".
 *
 * ## 设计取舍
 *
 * - **JSON 文件 vs Room/SQLite**: 单次启动 phase 列表 < 100 条, JSON
 *   文件读写 1ms 内, 没必要引入 Room schema 维护成本.
 * - **保留最近 N 次**: N = [MAX_HISTORY], FIFO 替换, 防止文件无限增长.
 * - **位置**: 放 [Context.filesDir] (非 cacheDir), cacheDir 可被系统清理,
 *   历史数据需要持久.
 *
 * ## 格式
 *
 * ```json
 * {
 *   "version": 1,
 *   "sessions": [
 *     {
 *       "startElapsedMs": 12345,
 *       "events": [
 *         {"type":"phase","name":"init_koin","elapsed":45},
 *         {"type":"instant","name":"ide_on_create_end"},
 *         ...
 *       ]
 *     },
 *     ...
 *   ]
 * }
 * ```
 *
 * @author android_zero
 */
class BootHistoryStore(context: Context) {

  private val log = LoggerFactory.getLogger(BootHistoryStore::class.java)

  private val file = File(context.filesDir, FILE_NAME)

  /**
   * 追加一次启动的 phase 列表到历史.
   *
   * 调用时机: IDEApplication.onCreate 末尾 ([com.itsaky.androidide.perf.PerfTracer.endBoot] 之后)
   * 但因为是 :perf 进程, 实际调用方是 [com.itsaky.androidide.perf.PerfApplication.init] 内
   * 注册一个 end_boot listener. 简化起见, 本类提供静态 [append] 给 [PhaseCollector] 在收到
   * EndBoot 时调.
   */
  fun append(events: List<PerfEvent>, startElapsedMs: Long) {
    try {
      val root = readRoot()
      val sessions = root.optJSONArray("sessions") ?: JSONArray()

      val session =
          JSONObject().apply {
            put("startElapsedMs", startElapsedMs)
            put("timestamp", System.currentTimeMillis())
            put("events", JSONArray().also { arr ->
              events.forEach { ev ->
                arr.put(serializeEvent(ev))
              }
            })
          }
      sessions.put(session)

      // FIFO trim
      while (sessions.length() > MAX_HISTORY) {
        sessions.remove(0)
      }

      root.put("sessions", sessions)
      file.writeText(root.toString(2))
    } catch (e: Throwable) {
      log.warn("BootHistoryStore.append failed: {}", e.message)
    }
  }

  /** 读所有历史 session. */
  fun readAll(): List<BootSession> {
    return try {
      val root = readRoot()
      val sessions = root.optJSONArray("sessions") ?: return emptyList()
      (0 until sessions.length()).mapNotNull { i ->
        val obj = sessions.optJSONObject(i) ?: return@mapNotNull null
        val eventsArr = obj.optJSONArray("events") ?: return@mapNotNull null
        val events = (0 until eventsArr.length()).mapNotNull { j ->
          parseEvent(eventsArr.optJSONObject(j) ?: return@mapNotNull null)
        }
        BootSession(
            startElapsedMs = obj.optLong("startElapsedMs", 0L),
            timestamp = obj.optLong("timestamp", 0L),
            events = events,
        )
      }
    } catch (e: Throwable) {
      log.warn("BootHistoryStore.readAll failed: {}", e.message)
      emptyList()
    }
  }

  /** 清空历史. */
  fun clear() {
    runCatching { file.delete() }
  }

  // -- private --

  private fun readRoot(): JSONObject {
    if (!file.exists()) return JSONObject().put("version", VERSION).put("sessions", JSONArray())
    return try {
      JSONObject(file.readText())
    } catch (e: Throwable) {
      log.warn("BootHistoryStore corrupted, recreating: {}", e.message)
      JSONObject().put("version", VERSION).put("sessions", JSONArray())
    }
  }

  private fun serializeEvent(event: PerfEvent): JSONObject =
      when (event) {
        is PerfEvent.Phase ->
            JSONObject()
                .put("type", "phase")
                .put("name", event.name)
                .put("elapsed", event.elapsedMs)
        is PerfEvent.Instant ->
            JSONObject().put("type", "instant").put("name", event.name)
        PerfEvent.EndBoot -> JSONObject().put("type", "end_boot")
      }

  private fun parseEvent(obj: JSONObject): PerfEvent? {
    return when (obj.optString("type")) {
      "phase" ->
          PerfEvent.Phase(
              name = obj.optString("name"),
              elapsedMs = obj.optLong("elapsed", 0L),
          )
      "instant" -> PerfEvent.Instant(name = obj.optString("name"))
      "end_boot" -> PerfEvent.EndBoot
      else -> null
    }
  }

  /**
   * 一次启动的快照.
   */
  data class BootSession(
      val startElapsedMs: Long,
      val timestamp: Long,
      val events: List<PerfEvent>,
  )

  companion object {
    const val FILE_NAME = "perf/boot_history.json"
    private const val MAX_HISTORY = 10
    private const val VERSION = 1
  }
}
