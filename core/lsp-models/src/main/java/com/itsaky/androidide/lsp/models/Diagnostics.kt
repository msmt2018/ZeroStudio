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

package com.itsaky.androidide.lsp.models

import com.google.gson.annotations.SerializedName
import com.itsaky.androidide.lsp.rpc.Range
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion.SEVERITY_ERROR
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion.SEVERITY_NONE
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion.SEVERITY_TYPO
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion.SEVERITY_WARNING
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 服务器发送诊断结果的参数 (textDocument/publishDiagnostics)
 * 完美对应 LSP 3.17 协议数据模型。
 * 
 * @author android_zero
 */
data class PublishDiagnosticsParams(
    val uri: String,
    val version: Int? = null,
    val diagnostics: List<DiagnosticItem>
)

/**
 * 表示一条诊断信息（错误、警告、提示等）。
 *
 * 优化说明：
 * LSP 官方协议中 severity 和 tags 是整数，而 AndroidIDE 原本使用的是枚举。
 * 这里使用 @SerializedName 和计算属性做了一层无缝桥接，既保证了 Gson 可以直接
 * 完美反序列化来自 Language Server 的 JSON-RPC 数据，又保留了 AndroidIDE 旧有代码
 * 对枚举类型的使用习惯。
 */
data class DiagnosticItem(
    @SerializedName("message") var message: String,
    @SerializedName("code") var code: String? = null,
    @SerializedName("range") var range: Range, // 使用 LSP RPC 的标准 Range
    @SerializedName("source") var source: String? = null,
    @SerializedName("severity") var severityValue: Int = 1,
    @SerializedName("tags") var tagsValue: List<Int>? = emptyList()
) {
  constructor(
      message: String,
      code: String? = null,
      range: com.itsaky.androidide.models.Range,
      source: String? = null,
      severity: DiagnosticSeverity = DiagnosticSeverity.ERROR,
      tags: List<DiagnosticTag> = emptyList(),
  ) : this(
      message = message,
      code = code,
      range =
          Range.newBuilder()
              .setStart(
                  com.itsaky.androidide.lsp.rpc.Position.newBuilder()
                      .setLine(range.start.line)
                      .setCharacter(range.start.column)
                      .build())
              .setEnd(
                  com.itsaky.androidide.lsp.rpc.Position.newBuilder()
                      .setLine(range.end.line)
                      .setCharacter(range.end.column)
                      .build())
              .build(),
      source = source,
      severityValue = severity.value,
      tagsValue = tags.map { it.value },
  )


  // 扩展存储数据，供 IDE 快速修复时绑定上下文
  var extra: Any? = null

  /**
   * 映射到 AndroidIDE 旧有的 DiagnosticSeverity 枚举
   */
  var severity: DiagnosticSeverity
    get() = DiagnosticSeverity.fromInt(severityValue)
    set(value) { severityValue = value.value }

  /**
   * 映射到 AndroidIDE 旧有的 DiagnosticTag 枚举
   */
  var tags: List<DiagnosticTag>
    get() = tagsValue?.mapNotNull { DiagnosticTag.fromInt(it) } ?: emptyList()
    set(value) { tagsValue = value.map { it.value } }

  companion object {
    @JvmField
    val START_COMPARATOR: Comparator<in DiagnosticItem> = Comparator { d1, d2 ->
        val lineCmp = d1.range.start.line.compareTo(d2.range.start.line)
        if (lineCmp != 0) lineCmp else d1.range.start.character.compareTo(d2.range.start.character)
    }

    private fun mapSeverity(severity: DiagnosticSeverity): Short {
      return when (severity) {
        DiagnosticSeverity.ERROR -> SEVERITY_ERROR
        DiagnosticSeverity.WARNING -> SEVERITY_WARNING
        DiagnosticSeverity.INFO -> SEVERITY_NONE
        DiagnosticSeverity.HINT -> SEVERITY_TYPO
      }
    }

    /**
     * Helper to convert line/column to absolute character index in the document content.
     */
    fun lineColumnToIndex(content: CharSequence, line: Int, column: Int): Int {
      var currentLine = 0
      var index = 0

      while (currentLine < line && index < content.length) {
        if (content[index] == '\n') {
          currentLine++
        }
        index++
      }

      return minOf(index + column, content.length)
    }
  }

  /**
   * 将该 LSP 诊断节点转化为 Sora Editor 的原生高亮区域 (DiagnosticRegion)。
   */
  fun asDiagnosticRegion(content: CharSequence): DiagnosticRegion {
    return try {
      // 转换行列为绝对偏移量 (LSP 中的列属性为 character)
      val startIndex = lineColumnToIndex(content, range.start.line, range.start.character)
      val endIndex = lineColumnToIndex(content, range.end.line, range.end.character)

      DiagnosticRegion(startIndex, endIndex, mapSeverity(severity)).apply {
          // 可以将自身附加进 detail 以便之后用于代码操作上下文
          this.detail = io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail(
              briefMessage = message,
              detailedMessage = message,
              extraData = this@DiagnosticItem
          )
      }
    } catch (e: Exception) {
      DiagnosticRegion(0, 1, mapSeverity(severity))
    }
  }
}

/**
 * 诊断合并结果包装类
 */
data class DiagnosticResult(var file: Path, var diagnostics: List<DiagnosticItem>) {
  companion object {
    @JvmField val NO_UPDATE = DiagnosticResult(Paths.get(""), emptyList())
  }
}

/**
 * LSP 规范的诊断严重级别 (对应 1, 2, 3, 4)
 */
enum class DiagnosticSeverity(val value: Int) {
  ERROR(1),
  WARNING(2),
  INFO(3),
  HINT(4);

  companion object {
    fun fromInt(value: Int): DiagnosticSeverity {
      return entries.find { it.value == value } ?: ERROR
    }
  }
}

/**
 * LSP 规范的诊断附加标签 (对应 1, 2)
 */
enum class DiagnosticTag(val value: Int) {
  UNNECESSARY(1),
  DEPRECATED(2);

  companion object {
    fun fromInt(value: Int): DiagnosticTag? {
      return entries.find { it.value == value }
    }
  }
}
