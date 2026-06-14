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

package com.itsaky.androidide.editor.ui

import com.itsaky.androidide.editor.api.IEditor
import com.itsaky.androidide.editor.ui.IDEEditor.Companion.log
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import io.github.rosemoe.sora.widget.SelectionMovement
import java.io.File

/**
 * Handler which implements various features in [IEditor].
 *
 * @author Akash Yadav
 * @author android_zero
 */
class EditorFeatures(var editor: IDEEditor? = null) : IEditor {

  /**
   * 短延迟, 等待 sora-editor LineBreakLayout 的异步 `measureAllLines` 完成.
   *
   * 经验值 50ms:
   * - sora-editor 0.23.6 的 LayoutTask 在内部线程池上跑, 测量一行约 0.1~2ms,
   *   几十行的 build output 累计 < 30ms.
   * - 50ms 在 UI 感知上几乎无延迟, 但给 layout task 足够完成时间.
   * - 太短 (< 20ms) 可能重试仍失败, 太长 (> 200ms) 用户会看到"延迟显示".
   */
  private val RETRY_DELAY_MS = 50L

  override fun getFile(): File? = withEditor { _file }

  override fun isModified(): Boolean = withEditor { this.isModified } ?: false

  override fun setSelection(position: Position) {
    withEditor { setSelection(position.line, position.column) }
  }

  override fun setSelection(start: Position, end: Position) {
    withEditor {
      if (!isValidPosition(start, true) || !isValidPosition(end, true)) {
        log.warn("Invalid selection range: start={} end={}", start, end)
        return@withEditor
      }

      setSelectionRegion(start.line, start.column, end.line, end.column)
    }
  }

  override fun setSelectionAround(line: Int, column: Int) {
    withEditor {
      if (line < lineCount) {
        val columnCount = text.getColumnCount(line)
        setSelection(line, if (column > columnCount) columnCount else column)
      } else {
        setSelection(lineCount - 1, text.getColumnCount(lineCount - 1))
      }
    }
  }

  override fun getCursorLSPRange(): Range =
      withEditor {
        val end =
            cursor.right().let { Position(line = it.line, column = it.column, index = it.index) }
        return@withEditor Range(cursorLSPPosition, end)
      } ?: Range.NONE

  override fun getCursorLSPPosition(): Position =
      withEditor {
        return@withEditor cursor.left().let {
          Position(line = it.line, column = it.column, index = it.index)
        }
      } ?: Position.NONE

  override fun validateRange(range: Range) {
    withEditor {
      val start = range.start
      val end = range.end
      val text = text
      val lineCount = text.lineCount

      start.line = 0.coerceAtLeast(start.line).coerceAtMost(lineCount - 1)
      start.column = 0.coerceAtLeast(start.column).coerceAtMost(text.getColumnCount(start.line))

      end.line = 0.coerceAtLeast(end.line).coerceAtMost(lineCount - 1)
      end.column = 0.coerceAtLeast(end.column).coerceAtMost(text.getColumnCount(end.line))
    }
  }

  override fun isValidRange(range: Range?, allowColumnEqual: Boolean): Boolean =
      withEditor {
        if (range == null) {
          return@withEditor false
        }
        val start = range.start
        val end = range.end
        return@withEditor isValidPosition(start, allowColumnEqual)
        // make sure start position is before end position
        && isValidPosition(end, allowColumnEqual) && start < end
      } ?: false

  override fun isValidPosition(position: Position?, allowColumnEqual: Boolean): Boolean =
      withEditor {
        return@withEditor if (position == null) {
          false
        } else
            isValidLine(position.line) &&
                isValidColumn(position.line, position.column, allowColumnEqual)
      } ?: false

  override fun isValidLine(line: Int): Boolean =
      withEditor { line >= 0 && line < text.lineCount } ?: false

  override fun isValidColumn(line: Int, column: Int, allowColumnEqual: Boolean): Boolean =
      withEditor {
        val columnCount = text.getColumnCount(line)
        return@withEditor column >= 0 &&
            (column < columnCount || allowColumnEqual && column == columnCount)
      } ?: false

  override fun append(text: CharSequence?): Int =
      withEditor {
        val content = getText()
        if (lineCount <= 0) {
          return@withEditor 0
        }

        val line = lineCount - 1
        var col = content.getColumnCount(line)
        if (col < 0) {
          col = 0
        }
        // capture by value so the retry below uses the same insert site
        val safeText = text
        try {
          content.insert(line, col, safeText)
          return@withEditor line
        } catch (e: ArrayIndexOutOfBoundsException) {
          // sora-editor 0.23.6 race condition (upstream bug):
          //   CodeEditor.afterInsert -> LineBreakLayout.afterInsert
          //   -> LineBreakLayout.measureLineAndUpdateInlineWidths
          //   -> BlockIntList.set (index=0, length=0)
          //
          // When the layout is still doing its asynchronous `measureAllLines`
          // (triggered by setText() or by a large batch replace), the
          // `inlineElementsWidths` and `widthMaintainer` BlockIntList instances
          // are still empty. The first `insert()` after the layout is created
          // (e.g. the very first build output appended to a freshly-created
          // BuildOutputFragment) crashes here.
          //
          // sora-editor 0.23.6 is the latest published version on Maven Central
          // (2025-06-22), so we cannot upgrade. Defensive retry after a short
          // delay: the layout task will have completed by then and the second
          // `insert()` succeeds.
          log.warn(
              "EditorFeatures.append race condition, deferring retry: {}",
              e.message)
          postDelayedInLifecycle(
              {
                try {
                  val currentLine = lineCount - 1
                  val currentCol =
                      getText().getColumnCount(currentLine).coerceAtLeast(0)
                  getText().insert(currentLine, currentCol, safeText)
                } catch (retryError: Throwable) {
                  // Give up after one retry. The next user-driven append will
                  // succeed once the layout has settled.
                  log.error(
                      "EditorFeatures.append retry failed, dropping this append",
                      retryError)
                }
              },
              RETRY_DELAY_MS)
          return@withEditor line
        }
      } ?: -1

  override fun replaceContent(newContent: CharSequence?) {
    withEditor {
      val lastLine = text.lineCount - 1
      val lastColumn = text.getColumnCount(lastLine)
      text.replace(0, 0, lastLine, lastColumn, newContent ?: "")
    }
  }

  override fun goToEnd() {
    withEditor { moveSelection(SelectionMovement.TEXT_END) }
  }

  private inline fun <T> withEditor(crossinline action: IDEEditor.() -> T): T? {
    return this.editor?.run {
      if (isReleased) {
        null
      } else action()
    }
  }
}
