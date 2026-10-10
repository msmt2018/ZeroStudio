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

package com.itsaky.androidide.editor.logs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * 日志文件路径与行号链接解析器。
 * 能够精确提取日志中的文件路径与行号，并高亮为可点击的超链接。
 *
 * @author android_zero
 */
object LogFileLinkParser {

  private const val TAG_FILE_LINK = "LOG_FILE_LINK"

  // 1. file:///path 或 file:/path, 支持 URL 编码及可选 :line:col 或 :line
  private val FILE_URI_PATTERN =
      Pattern.compile("""file://?([^\s'":]+)(?::(\d+)(?::(\d+))?)?""")

  // 2. 引号包围的路径: 'path' line: 17 或 "path"
  private val QUOTED_PATH_PATTERN =
      Pattern.compile("""['"]((?:/storage/|/data/)[^'"]+)['"](?:\s+line:\s*(\d+))?""")

  // 3. 常规绝对路径开头: /storage/ 或 /data/
  private val DIRECT_PATH_PATTERN =
      Pattern.compile("""(?<=\s|^)((?:/storage/|/data/)[^\s'":]+)(?::(\d+)(?::(\d+))?)?(?:\s+line:\s*(\d+))?""")

  data class ClickableFileTarget(
      val file: File,
      val line: Int = 1, // 1-based
      val column: Int = 1,
  )

  /**
   * 将普通日志文本解析为带超链接色彩与点击 Annotation 的 [AnnotatedString]
   */
  fun parseAndStyleLine(
      rawLine: String,
      linkColor: Color = Color(0xFF4FC3F7),
  ): AnnotatedString {
    val targets = mutableListOf<MatchCandidate>()

    findMatches(rawLine, FILE_URI_PATTERN, isUri = true, targets)
    findMatches(rawLine, QUOTED_PATH_PATTERN, isUri = false, targets)
    findMatches(rawLine, DIRECT_PATH_PATTERN, isUri = false, targets)

    if (targets.isEmpty()) {
      return AnnotatedString(rawLine)
    }

    // 按起始位置升序排序，去除相互重叠的子片段
    val sorted = targets.sortedBy { it.startIndex }
    val nonOverlapping = mutableListOf<MatchCandidate>()
    var lastEnd = -1
    for (cand in sorted) {
      if (cand.startIndex >= lastEnd) {
        nonOverlapping.add(cand)
        lastEnd = cand.endIndex
      }
    }

    return buildAnnotatedString {
      var cursor = 0
      for (match in nonOverlapping) {
        if (match.startIndex > cursor) {
          append(rawLine.substring(cursor, match.startIndex))
        }

        val startAnnotation = length
        val matchText = rawLine.substring(match.startIndex, match.endIndex)
        pushStringAnnotation(
            tag = TAG_FILE_LINK,
            annotation = "${match.file.absolutePath}::${match.line}::${match.column}",
        )
        pushStyle(
            SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium,
            )
        )
        append(matchText)
        pop() // pop style
        pop() // pop annotation

        cursor = match.endIndex
      }
      if (cursor < rawLine.length) {
        append(rawLine.substring(cursor))
      }
    }
  }

  fun extractTarget(annotation: String): ClickableFileTarget? {
    val parts = annotation.split("::")
    if (parts.isEmpty()) return null
    val path = parts[0]
    val line = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val col = parts.getOrNull(2)?.toIntOrNull() ?: 1
    val file = File(path)
    return if (file.exists()) ClickableFileTarget(file, line, col) else null
  }

  private fun findMatches(
      text: String,
      pattern: Pattern,
      isUri: Boolean,
      out: MutableList<MatchCandidate>,
  ) {
    val matcher = pattern.matcher(text)
    while (matcher.find()) {
      var rawPath = matcher.group(1) ?: continue
      if (isUri) {
        try {
          rawPath = URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {}
      }

      val file = File(rawPath)
      // 快速验证真实存在性，防止普通日志文本误匹配
      if (file.exists() && file.isFile) {
        var lineNum = 1
        var colNum = 1

        // 提取各种捕获组中的行号
        val g2 = runCatching { matcher.group(2) }.getOrNull()
        val g3 = runCatching { matcher.group(3) }.getOrNull()
        val g4 = runCatching { matcher.group(4) }.getOrNull()

        if (!g2.isNullOrBlank()) lineNum = g2.toIntOrNull() ?: 1
        if (!g3.isNullOrBlank()) colNum = g3.toIntOrNull() ?: 1
        if (!g4.isNullOrBlank()) lineNum = g4.toIntOrNull() ?: lineNum

        out.add(
            MatchCandidate(
                startIndex = matcher.start(),
                endIndex = matcher.end(),
                file = file,
                line = lineNum,
                column = colNum,
            )
        )
      }
    }
  }

  private data class MatchCandidate(
      val startIndex: Int,
      val endIndex: Int,
      val file: File,
      val line: Int,
      val column: Int,
  )
}