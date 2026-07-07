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

package com.itsaky.androidide.fragments.editor.audio

import java.io.File

/**
 * 单行歌词。
 *
 * @param timeMs 时间戳 (毫秒)
 * @param text 歌词文本
 */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * 歌词同步控制器: 解析 `.lrc` 文件, 提供时间轴同步查找。
 *
 * 支持 LRC 标准格式: `[mm:ss.xx]歌词文本`, 一行可有多个时间标签。
 *
 * @author ZeroStudio
 */
object LyricSyncController {

    private val LRC_TIME_REGEX = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /**
     * 解析 LRC 文本为歌词行列表 (按时间排序)。
     *
     * 支持格式:
     * - `[00:12.34]歌词`
     * - `[00:12.345]歌词`
     * - `[01:02.34][02:03.45]同一行歌词多个时间戳`
     */
    fun parseLrc(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (rawLine in content.lineSequence()) {
            val matches = LRC_TIME_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) continue

            // 提取时间标签后的歌词文本 (最后一个时间标签之后的内容)
            val lastMatch = matches.last()
            val text = rawLine.substring(lastMatch.range.last + 1).trim()
            if (text.isEmpty()) continue

            // 每个时间标签对应一行歌词
            for (match in matches) {
                val (minStr, secStr, msStr) = match.destructured
                val minutes = minStr.toLong()
                val seconds = secStr.toLong()
                val milliseconds = when {
                    msStr.isEmpty() -> 0L
                    msStr.length == 1 -> msStr.toLong() * 100
                    msStr.length == 2 -> msStr.toLong() * 10
                    else -> msStr.take(3).toLong()
                }
                val timeMs = minutes * 60_000 + seconds * 1000 + milliseconds
                lines.add(LyricLine(timeMs, text))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * 从文件加载歌词。
     *
     * 查找顺序:
     * 1. 同名 `.lrc` 文件 (如 `song.mp3` → `song.lrc`)
     * 2. 返回空列表 (后续可扩展: 从 ID3/SYLT 标签提取内嵌歌词)
     */
    fun loadFromFile(audioFile: File): List<LyricLine> {
        val lrcFile = File(audioFile.parentFile, audioFile.nameWithoutExtension + ".lrc")
        if (!lrcFile.exists() || !lrcFile.canRead()) return emptyList()
        return runCatching { parseLrc(lrcFile.readText()) }.getOrDefault(emptyList())
    }

    /**
     * 二分查找当前播放位置对应的歌词行索引。
     *
     * @param positionMs 当前播放位置 (毫秒)
     * @param lyrics 歌词列表 (必须已按时间排序)
     * @return 当前行索引, 无匹配返回 -1
     */
    fun currentLine(positionMs: Long, lyrics: List<LyricLine>): Int {
        if (lyrics.isEmpty()) return -1
        // 二分查找: 找到最后一个 timeMs <= positionMs 的行
        var low = 0
        var high = lyrics.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lyrics[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
