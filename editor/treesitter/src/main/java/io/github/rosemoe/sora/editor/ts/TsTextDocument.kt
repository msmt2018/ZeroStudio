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
     * 单次 parse 的最大时长（微秒）。超出后 parser 提前返回 null，避免超大/病态文件无限阻塞工作线程。
     * 与 query 侧的 execWithOptions + setMatchLimit 共同构成 0.27 解析/查询的完整健壮性保护。
     * 超时降级路径：parseString 返回 null → tree 为 null → updateStyles 因 tree?.canAccess()!=true 早退。
     */
    private const val PARSE_TIMEOUT_MICROS = 5_000_000L // 5s
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
        it.language = language
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

  override fun close() {
    text?.close()
    tree?.close()
    parser.close()
  }
}
