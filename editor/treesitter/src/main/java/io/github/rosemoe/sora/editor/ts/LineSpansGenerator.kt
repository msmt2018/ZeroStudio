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

/**
 * ****************************************************************************
 * sora-editor - the awesome code editor for Android https://github.com/Rosemoe/sora-editor
 * Copyright (C) 2020-2023 Rosemoe
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 * Please contact Rosemoe by email 2073412493@qq.com if you need additional information or have any
 * questions
 * ****************************************************************************
 */
package io.github.rosemoe.sora.editor.ts

import com.itsaky.androidide.treesitter.TSInputEdit
import com.itsaky.androidide.treesitter.TSQueryCapture
import com.itsaky.androidide.treesitter.TSQueryCursor
import com.itsaky.androidide.treesitter.TSTree
import com.itsaky.androidide.treesitter.api.TreeSitterQueryCapture
import com.itsaky.androidide.treesitter.api.safeExecQueryCursor
import io.github.rosemoe.sora.editor.ts.spans.TsSpanFactory
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Spans
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Spans generator for tree-sitter. Results are cached.
 *
 * Note that this implementation does not support external modifications.
 *
 * @author Rosemoe
 */
class LineSpansGenerator(
    internal var tree: TSTree,
    internal var lineCount: Int,
    private val content: Content,
    internal var theme: TsTheme,
    private val languageSpec: TsLanguageSpec,
    var scopedVariables: TsScopedVariables?,
    private val spanFactory: TsSpanFactory,
) : Spans {

  companion object {

    const val CACHE_THRESHOLD = 200
  }

  private val caches = mutableListOf<SpanCache>()

  // 复用 TSQueryCursor 避免每行都 create + close 的 native 分配/释放开销。
  // cursor 在 captureRegion 首次调用时懒初始化，tree 更换时由外部通过
  // updateTree() 重置（旧的由 GC 回收或外部关闭）。
  private var reusableCursor: TSQueryCursor? = null

  fun edit(edit: TSInputEdit) {
    tree.edit(edit)
  }

  /**
   * 增量更新：替换 generator 持有的 tree（用于 doMod 后的快速路径）。
   *
   * 关闭旧 tree、设置新 tree、清空 line cache、重置 reusableCursor。
   * 清空 cache 是必要的：reparse 后 tree 结构可能变化，旧 cache 中的 span 列号
   * 可能与新 tree 不一致。清空后渲染层会按需重新查询（`LineSpansGenerator.read()`
   * 是惰性按行的，只查询可见行）。
   *
   * @param newTree 新的语法树（调用方负责 copy，本方法负责关闭旧 tree）。
   */
  fun updateTree(newTree: TSTree) {
    val old = tree
    tree = newTree
    old?.close()
    caches.clear()
    reusableCursor?.close()
    reusableCursor = null
  }

  fun queryCache(line: Int): MutableList<Span>? {
    for (i in 0 until caches.size) {
      val cache = caches[i]
      if (cache.line == line) {
        caches.removeAt(i)
        caches.add(0, cache)
        return cache.spans
      }
    }
    return null
  }

  fun pushCache(line: Int, spans: MutableList<Span>) {
    while (caches.size >= CACHE_THRESHOLD) {
      caches.removeAt(caches.size - 1)
    }
    caches.add(0, SpanCache(spans, line))
  }

  fun captureRegion(startIndex: Int, endIndex: Int): MutableList<Span> {
    val list = mutableListOf<Span>()

    if (!tree.canAccess()) {
      list.add(emptySpan(0))
      return list
    }

    val captures = mutableListOf<TSQueryCapture>()

    // 复用 cursor，避免每行 create + close 的 native 开销。
    // 如果复用的 cursor 已不可用（被外部关闭或 tree 已更换），则新建一个。
    val cursor = reusableCursor
    if (cursor == null || !cursor.canAccess()) {
      reusableCursor?.close()
      reusableCursor = TSQueryCursor.create()
    }
    val activeCursor = reusableCursor!!
    // 必须设置 isAllowChangedNodes = true：TsAnalyzeManager.insert/delete 会给渲染副本 tree
    // 打 edit 标记（hasChanges=true），高亮查询仍需在此 tree 上正常执行。
    // safeExecQueryCursor 会检查此标志，为 true 时跳过 hasChanges 前置检查。
    activeCursor.isAllowChangedNodes = true

    try {
      activeCursor.setByteRange(startIndex * 2, endIndex * 2)

      activeCursor.safeExecQueryCursor(
          query = languageSpec.tsQuery,
          tree = tree,
          recycleNodeAfterUse = true,
          debugLogging = false,
          debugName = "LineSpansGenerator.captureRegion()",
      ) { match ->
        if (languageSpec.queryPredicator.doPredicate(languageSpec.predicates, content, match)) {
          captures.addAll(match.captures)
        }
      }

      captures.sortBy { it.node.startByte }
      var lastIndex = 0

      for (capture in captures) {
        val startByte = capture.node.startByte
        val endByte = capture.node.endByte
        val start = (startByte / 2 - startIndex).coerceAtLeast(0)
        val pattern = capture.index
        // Do not add span for overlapping regions and out-of-bounds regions
        // 使用预计算的 Set 查找避免 JNI 调用
        if (
            start >= lastIndex &&
                endByte / 2 >= startIndex &&
                startByte / 2 < endIndex &&
                (pattern !in languageSpec.localsScopeIndices &&
                    pattern !in languageSpec.localsDefinitionIndices &&
                    pattern !in languageSpec.localsDefinitionValueIndices &&
                    pattern !in languageSpec.localsMembersScopeIndices)
        ) {
          if (start != lastIndex) {
            list.addAll(createSpans(capture, lastIndex, start - 1, theme.normalTextStyle))
          }
          var style = 0L
          if (capture.index in languageSpec.localsReferenceIndices) {
            // scopedVariables 可能为 null（viewport-first 首次渲染时尚未构建），
            // 此时跳过变量引用解析，让后续 capture 提供 fallback 颜色。
            val sv = scopedVariables
            if (sv != null) {
              val def =
                  sv.findDefinition(
                      startByte / 2,
                      endByte / 2,
                      content.substring(startByte / 2, endByte / 2),
                  )
              if (def != null && def.matchedHighlightPattern != -1) {
                style = theme.resolveStyleForPattern(def.matchedHighlightPattern)
              }
            }
            // This reference can not be resolved to its definition
            // but it can have its own fallback color by other captures
            // so continue to next capture
            if (style == 0L) {
              continue
            }
          }
          if (style == 0L) {
            style = theme.resolveStyleForPattern(capture.index)
          }
          if (style == 0L) {
            style = theme.normalTextStyle
          }
          val end = (endByte / 2 - startIndex).coerceAtMost(endIndex)
          list.addAll(createSpans(capture, start, end, style))
          lastIndex = end
        }

        (capture as? TreeSitterQueryCapture?)?.recycle()
      }

      if (lastIndex != endIndex) {
        list.add(emptySpan(lastIndex))
      }
    } catch (e: Exception) {
      // cursor 可能因 tree 被更换而失效，重建 cursor 以备下次使用
      reusableCursor?.close()
      reusableCursor = null
      throw e
    }
    if (list.isEmpty()) {
      list.add(emptySpan(0))
    }
    return list
  }

  private fun createSpans(
      capture: TSQueryCapture,
      startColumn: Int,
      endColumn: Int,
      style: Long,
  ): List<Span> {
    val spans = spanFactory.createSpans(capture, startColumn, style)
    if (spans.size > 1) {
      var prevCol = spans[0].column
      if (prevCol > endColumn) {
        throw IndexOutOfBoundsException(
            "Span's column is out of bounds! column=$prevCol, endColumn=$endColumn"
        )
      }
      for (i in 1..spans.lastIndex) {
        val col = spans[i].column
        if (col <= prevCol) {
          throw IllegalStateException("Spans must not overlap! prevCol=$prevCol, col=$col")
        }
        if (col > endColumn) {
          throw IndexOutOfBoundsException(
              "Span's column is out of bounds! column=$col, endColumn=$endColumn"
          )
        }
        prevCol = col
      }
    }
    return spans
  }

  private fun emptySpan(column: Int): Span {
    return SpanFactory.obtain(column, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
  }

  override fun adjustOnInsert(start: CharPosition, end: CharPosition) {}

  override fun adjustOnDelete(start: CharPosition, end: CharPosition) {}

  override fun read() =
      object : Spans.Reader {

        private var spans = mutableListOf<Span>()

        override fun moveToLine(line: Int) {
          try {
            if (line < 0 || line >= lineCount) {
              spans = mutableListOf()
              return
            }
            val cached = queryCache(line)
            if (cached != null) {
              spans = cached
              return
            }
            val start = content.indexer.getCharPosition(line, 0).index
            val end = start + content.getColumnCount(line)
            spans = captureRegion(start, end)
            pushCache(line, spans)
          } catch (err: Throwable) {
            err.printStackTrace()
          }
        }

        override fun getSpanCount() = spans.size

        override fun getSpanAt(index: Int) = spans[index]

        override fun getSpansOnLine(line: Int): MutableList<Span> {
          try {
            val cached = queryCache(line)
            if (cached != null) {
              return ArrayList(cached)
            }
            val start = content.indexer.getCharPosition(line, 0).index
            val end = start + content.getColumnCount(line)
            return captureRegion(start, end)
          } catch (err: Throwable) {
            err.printStackTrace()
            throw err
          }
        }
      }

  override fun supportsModify() = false

  override fun modify(): Spans.Modifier {
    throw UnsupportedOperationException()
  }

  override fun getLineCount() = lineCount
}

data class SpanCache(val spans: MutableList<Span>, val line: Int)
