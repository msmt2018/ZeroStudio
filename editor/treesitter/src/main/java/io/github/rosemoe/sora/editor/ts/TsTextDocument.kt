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

package io.github.rosemoe.sora.editor.ts

import com.itsaky.androidide.treesitter.TSLanguage
import com.itsaky.androidide.treesitter.TSParser
import com.itsaky.androidide.treesitter.TSPoint
import com.itsaky.androidide.treesitter.TSRange
import com.itsaky.androidide.treesitter.TSTree
import com.itsaky.androidide.treesitter.string.UTF16String
import com.itsaky.androidide.treesitter.string.UTF16StringFactory

/**
 * A text document which maintains a [TSTree] and the associated [UTF16String].
 *
 * @author Akash Yadav
 */
class TsTextDocument(language: TSLanguage) : AutoCloseable {

  companion object {
    /**
     * 单次全量 parse 的最大时长（微秒）。超出后 parser 提前返回 null。
     *
     * 此超时仅用于 Phase 2 全量解析。Phase 1 viewport 解析只解析前 [VIEWPORT_LINES] 行，
     * 始终在毫秒级完成，不受此超时影响。
     *
     * 全量解析超时后的降级路径：保留 Phase 1 的 viewport tree → 可见区域有高亮，
     * 视口外区域为纯文本。这比"完全无高亮"好得多。
     */
    internal const val PARSE_TIMEOUT_MICROS = 30_000_000L // 30s

    /**
     * Viewport 解析的行数上限。Phase 1 只解析前 N 行，确保任何文件大小下都能在毫秒级
     * 完成首次解析并立即渲染高亮。2000 行覆盖大多数编辑器可见区域 + 滚动缓冲。
     */
    internal const val VIEWPORT_LINES = 2000
  }

  @Volatile private var documentVersion = 1L

  /** The version of this text document. */
  val version: Long
    get() = documentVersion

  /** The source text. */
  val text = UTF16StringFactory.newString()

  /** The parser used to parse the source text into a syntax tree. */
  val parser =
      TSParser.create().also {
        // 显式检查 setLanguage 返回值：tree-sitter 0.27 中如果 grammar 的 ABI 版本与
        // parser 不兼容，setLanguage 返回 false 且 language 不会被设置。若不检查，
        // 后续 parseString 会静默返回 null → 文件完全不着色，且无明确错误日志。
        if (!it.setLanguage(language)) {
          throw IllegalStateException(
              "Failed to set language on TSParser: ABI version incompatible. " +
                  "Language: ${language.name}. Grammar may need recompilation for tree-sitter 0.27.")
        }
        // 升级：接入 tree-sitter 0.27 的 parser setTimeout，为单次 parse 设置时间上限，
        // 补齐 parse 侧的健壮性保护（query 侧已有 execWithOptions + setMatchLimit）。
        it.setTimeout(PARSE_TIMEOUT_MICROS)
      }

  /** The syntax tree. */
  var tree: TSTree? = null
    internal set

  /** Request the parser to cancel parsing if a parsing is in progress. */
  fun requestCancellationAndWaitIfParsing() {
    if (parser.isParsing) {
      parser.requestCancellationAndWait()
    }
  }

  /**
   * 非阻塞地请求取消解析。仅设置 native 取消标志，不等待解析结束。
   * 适用于主线程（如 stop()）场景，避免阻塞 UI 线程。
   */
  fun requestCancellationAsyncIfParsing() {
    if (parser.isParsing) {
      parser.requestCancellationAsync()
    }
  }

  /**
   * Initialize the source text with the given initialization message. The caller is responsible for
   * handling the source text state i.e. this method does not check whether the text is already
   * initialized or not.
   */
  internal fun doInit(init: TextInit) {
    text.append(init.text)
    documentVersion = init.contentVersion
  }

  /**
   * Apply the given [text modification][TextMod] to the source text.
   *
   * @param mod The text modification.
   */
  internal fun doMod(mod: TextMod) {
    val edit = mod.edit
    val newText = mod.changedText

    if (newText == null) {
      text.deleteBytes(edit.startByte, edit.oldEndByte)
    } else {
      if (mod.start == text.length) {
        text.append(newText)
      } else {
        text.insert(mod.start, newText)
      }
    }

    documentVersion = mod.contentVersion
  }

  /**
   * Parse the source text into a syntax tree, using the given [oldTree] for incremental parsing.
   */
  internal fun reparse(oldTree: TSTree? = null): TSTree? {
    tree = parser.parseString(oldTree, text)
    return tree
  }

  /**
   * Phase 1: Viewport-first parse. 通过 tree-sitter 的 `ts_parser_set_included_ranges`
   * 限制解析范围到 `[0, endByte)` 字节区间（对应 `[0, endRow)` 行）。
   *
   * 无论文件总行数是多少（几百到几十亿行），此方法始终在毫秒级完成，因为解析工作量与
   * `endRow` 成正比，与文件总大小无关。
   *
   * **调用方必须通过 `ContentReference.getCharPosition()` 的 O(1) 索引器计算 `endByte`/`endRow`**，
   * 而非逐字符扫描换行符（那会是 O(charLen) 的 JNI 开销，对大文件不可接受）。
   *
   * 解析结果是一棵只覆盖 viewport 范围的部分语法树。`LineSpansGenerator` 查询视口外的行时
   * 不会得到任何 capture，自然降级为纯文本显示——这比"完全无高亮"好得多。
   *
   * 调用方在 Phase 2 全量解析前必须调用 [resetIncludedRanges] 重置 parser 的 included ranges，
   * 否则后续的 `reparse` / `parseString` 仍会被限制在 viewport 范围内。
   *
   * @param endByte viewport 末尾字节偏移（UTF-16，每字符 2 字节），由调用方通过
   *        `ContentReference.getCharPosition()` 的 O(1) 索引器计算。
   * @param endRow viewport 末尾行号（0-based，exclusive）。
   * @return 部分语法树（仅覆盖前 endRow 行），解析失败返回 null。
   */
  internal fun reparseViewport(endByte: Int, endRow: Int): TSTree? {
    val range =
        TSRange.create(
            0, // startByte
            endByte, // endByte
            TSPoint.create(0, 0), // startPoint
            TSPoint.create(endRow, 0), // endPoint
        )

    parser.setIncludedRanges(arrayOf(range))
    tree = parser.parseString(null, text)
    return tree
  }

  /**
   * 重置 parser 的 included ranges 为空（即全文范围）。
   *
   * 在 [reparseViewport] 之后、全量 [reparse] 之前调用，确保后续解析覆盖整个文件。
   */
  internal fun resetIncludedRanges() {
    parser.setIncludedRanges(emptyArray())
  }

  override fun close() {
    text?.close()
    tree?.close()
    parser.close()
  }
}
