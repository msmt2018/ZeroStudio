/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.editor.video

import java.io.File

/**
 * 单条字幕 (起止时间 + 文本)。
 *
 * @param startMs 开始时间 (毫秒)
 * @param endMs 结束时间 (毫秒)
 * @param text 字幕文本 (可含多行, 用 \n 分隔)
 */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/**
 * 视频字幕控制器: 解析外部字幕文件 (.srt / .vtt), 提供时间轴查找。
 *
 * ## 支持格式
 * - **SRT** (SubRip): 最常见的字幕格式, 序号 + 时间戳 + 文本 + 空行
 * - **VTT** (WebVTT): Web 标准, 类似 SRT 但头标 `WEBVTT`, 时间戳用 `.` 而非 `,`
 *
 * ## 不支持 (按 spec 范围裁剪)
 * - ASS/SSA (Advanced SubStation Alpha) — 复杂样式, Media3 内置支持可直接用
 * - SMI, SUB 等 — 较少见, 后续按需扩展
 *
 * ## 字幕轨道策略
 * - 内嵌字幕 (mkv/mp4 内置): 由 [VideoPlaybackController.selectSubtitle] 处理
 * - 外挂字幕 (同名 .srt / .vtt 文件): 由本类加载 + Fragment 用 Compose 叠加渲染
 *
 * @author ZeroStudio
 */
object VideoSubtitleController {

    /**
     * 从文件加载字幕。
     *
     * 查找顺序:
     * 1. 同名 `.srt` 文件 (如 `movie.mp4` → `movie.srt`)
     * 2. 同名 `.vtt` 文件
     * 3. 返回空列表 (使用内嵌字幕)
     */
    fun loadFromFile(videoFile: File): List<SubtitleCue> {
        val srtFile = File(videoFile.parentFile, videoFile.nameWithoutExtension + ".srt")
        if (srtFile.exists() && srtFile.canRead()) {
            return runCatching { parseSrt(srtFile.readText()) }.getOrDefault(emptyList())
        }
        val vttFile = File(videoFile.parentFile, videoFile.nameWithoutExtension + ".vtt")
        if (vttFile.exists() && vttFile.canRead()) {
            return runCatching { parseVtt(vttFile.readText()) }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    /**
     * 解析 SRT 文本。
     *
     * 格式示例:
     * ```
     * 1
     * 00:00:01,000 --> 00:00:04,000
     * Hello World
     *
     * 2
     * 00:00:05,000 --> 00:00:08,000
     * 第二行字幕
     * ```
     */
    fun parseSrt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        // 用空行分块
        val blocks = content.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.size < 2) continue
            // 第一行可能是序号, 跳过
            val timeLineIdx = if (lines[0].contains("-->")) 0 else 1
            if (timeLineIdx >= lines.size) continue
            val timeLine = lines[timeLineIdx]
            val (startMs, endMs) = parseTimeLine(timeLine) ?: continue
            val text = lines.drop(timeLineIdx + 1).joinToString("\n").trim()
            if (text.isNotEmpty()) {
                cues.add(SubtitleCue(startMs = startMs, endMs = endMs, text = text))
            }
        }
        return cues
    }

    /**
     * 解析 VTT (WebVTT) 文本。
     *
     * 与 SRT 区别:
     * - 头标 `WEBVTT`
     * - 时间戳小数点用 `.` 而非 `,`
     * - 序号可选
     */
    fun parseVtt(content: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        // 跳过 WEBVTT 头
        val body = content.replace("\r\n", "\n").let {
            val idx = it.indexOf("\n\n")
            if (idx >= 0 && it.startsWith("WEBVTT")) it.substring(idx + 2) else it
        }
        val blocks = body.split(Regex("\\n\\s*\\n"))
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            // 找到时间行
            var timeLineIdx = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) {
                    timeLineIdx = i
                    break
                }
            }
            if (timeLineIdx < 0) continue
            val (startMs, endMs) = parseTimeLine(lines[timeLineIdx]) ?: continue
            val text = lines.drop(timeLineIdx + 1).joinToString("\n").trim()
            if (text.isNotEmpty()) {
                cues.add(SubtitleCue(startMs = startMs, endMs = endMs, text = text))
            }
        }
        return cues
    }

    /**
     * 二分查找当前播放位置对应的字幕索引。
     *
     * @param positionMs 当前播放位置 (毫秒)
     * @param cues 字幕列表 (必须已按 startMs 排序)
     * @return 当前的字幕文本, 无匹配返回 null
     */
    fun currentSubtitle(positionMs: Long, cues: List<SubtitleCue>): String? {
        if (cues.isEmpty()) return null
        // 二分查找: 找到最后一个 startMs <= positionMs 的 cue
        var low = 0
        var high = cues.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (result < 0) return null
        val cue = cues[result]
        // 检查是否仍在 endMs 范围内
        return if (positionMs in cue.startMs..cue.endMs) cue.text else null
    }

    // ── 内部: 时间戳解析 ──────────────────────────────────────

    /**
     * 解析时间行: `00:00:01,000 --> 00:00:04,000` 或 `00:01.000 --> 00:04.000`
     *
     * @return (startMs, endMs), 解析失败返回 null
     */
    private fun parseTimeLine(line: String): Pair<Long, Long>? {
        val regex = Regex(
            """(\d{1,2}:\d{2}:\d{2}[.,]\d{3}|\d{1,2}:\d{2}[.,]\d{3})\s*-->\s*(\d{1,2}:\d{2}:\d{2}[.,]\d{3}|\d{1,2}:\d{2}[.,]\d{3})""",
        )
        val match = regex.find(line) ?: return null
        val startMs = parseTimestamp(match.groupValues[1]) ?: return null
        val endMs = parseTimestamp(match.groupValues[2]) ?: return null
        return startMs to endMs
    }

    /**
     * 解析单个时间戳: `00:00:01,000` / `00:00:01.000` / `00:01.000`
     */
    private fun parseTimestamp(ts: String): Long? {
        val normalized = ts.replace(',', '.')
        val parts = normalized.split(':')
        return try {
            when (parts.size) {
                3 -> {
                    // hh:mm:ss.xxx
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val sParts = parts[2].split('.')
                    val s = sParts[0].toLong()
                    val ms = if (sParts.size > 1) sParts[1].padEnd(3, '0').take(3).toLong() else 0L
                    h * 3_600_000 + m * 60_000 + s * 1000 + ms
                }
                2 -> {
                    // mm:ss.xxx
                    val m = parts[0].toLong()
                    val sParts = parts[1].split('.')
                    val s = sParts[0].toLong()
                    val ms = if (sParts.size > 1) sParts[1].padEnd(3, '0').take(3).toLong() else 0L
                    m * 60_000 + s * 1000 + ms
                }
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
