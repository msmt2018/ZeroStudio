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
package com.itsaky.androidide.perf.proto

/**
 * 跨进程事件的 JSON 编解码 (PR #3/5).
 *
 * 协议格式: line-delimited JSON, UTF-8, `\n` 分隔.
 *
 * ```
 * {"type":"phase","name":"koin_start","elapsed":45}
 * {"type":"instant","name":"ide_on_create_begin"}
 * {"type":"end_boot"}
 * ```
 *
 * ## 设计取舍
 *
 * - **JSON vs 二进制**: JSON 调试友好 (logcat 可读, 简单 `cat` 即可复盘),
 *   体积略大但 18 段 phase + 后续 1Hz 采样也才几 KB/分钟, 完全够用.
 * - **无 schema 校验**: 服务端容错 (解析失败返回 null 并 log warn), 允许
 *   主进程新版本加新字段而不破坏旧 server.
 * - **手工 parser**: 18 段 phase 字段固定 (type/name/elapsed), 用 regex
 *   比引入 org.json 或 kotlinx.serialization 更轻 (避免给 :perf 进程增加
 *   依赖体积).
 *
 * @author android_zero
 */
internal object PerfSocketProtocol {

  private val PHASE_REGEX =
      Regex(""""type"\s*:\s*"phase".*?"name"\s*:\s*"([^"]+)".*?"elapsed"\s*:\s*(\d+)""")

  private val INSTANT_REGEX =
      Regex(""""type"\s*:\s*"instant".*?"name"\s*:\s*"([^"]+)"""")

  private val END_BOOT_REGEX = Regex(""""type"\s*:\s*"end_boot"""")

  /**
   * 解析一行 JSON 为 [PerfEvent].
   *
   * @return 成功 → 事件; 失败 / 类型未知 → null
   */
  fun parse(line: String): PerfEvent? {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return null

    return when {
      END_BOOT_REGEX.containsMatchIn(trimmed) -> PerfEvent.EndBoot
      else -> {
        val phaseMatch = PHASE_REGEX.find(trimmed)
        if (phaseMatch != null) {
          val name = phaseMatch.groupValues[1].unescape()
          val elapsed = phaseMatch.groupValues[2].toLongOrNull() ?: 0L
          return PerfEvent.Phase(name, elapsed)
        }
        val instantMatch = INSTANT_REGEX.find(trimmed)
        if (instantMatch != null) {
          return PerfEvent.Instant(instantMatch.groupValues[1].unescape())
        }
        null
      }
    }
  }

  /**
   * 序列化 [event] 为单行 JSON (不含换行).
   *
   * 主要给 server 端回放 / 测试用, 正常路径是 server 收 client 发的事件.
   */
  fun format(event: PerfEvent): String =
      when (event) {
        is PerfEvent.Phase ->
            """{"type":"phase","name":"${event.name.escape()}","elapsed":${event.elapsedMs}}"""
        is PerfEvent.Instant -> """{"type":"instant","name":"${event.name.escape()}"}"""
        is PerfEvent.EndBoot -> """{"type":"end_boot"}"""
      }

  // -- 字符串转义 --

  private fun String.escape(): String =
      replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

  private fun String.unescape(): String =
      replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
}
