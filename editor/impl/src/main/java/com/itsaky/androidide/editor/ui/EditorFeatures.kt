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
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Handler which implements various features in [IEditor].
 *
 * @author Akash Yadav
 * @author android_zero
 */
class EditorFeatures(var editor: IDEEditor? = null) : IEditor {

  /**
   * 多次重试的延迟链 (指数退避).
   *
   * sora-editor 0.23.6 的 `BlockIntList.set(index=0, length=0)` race condition
   * 在某些场景下可能持续较久 (例如 BuildOutputFragment 在 build 期间
   * 持续 append, 50ms 一次重试仍不够). 用 4 次退避 (50/100/200/400 = 750ms
   * 总预算) 大幅提升首次成功率, 避免数据丢失.
   */
  private val RETRY_DELAYS_MS = longArrayOf(50L, 100L, 200L, 400L)

  /** pending 队列 flush 间隔. */
  private val PENDING_FLUSH_DELAY_MS = 100L

  /** pending 队列每条内容最多 flush 次数, 防止坏布局状态导致无限重试刷日志. */
  private val MAX_PENDING_FLUSH_ATTEMPTS = 8

  /**
   * 所有重试都失败时被丢弃的 append, 暂存到 pending 队列, 等下一次
   * [append] 成功或定时器触发时再 flush. 避免 build output 静默丢失.
   */
  private val pendingAppends = ConcurrentLinkedQueue<PendingAppend>()

  /** 防止 scheduleFlush 重复排队. */
  @Volatile private var flushScheduled = false

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
        if (lineCount <= 0) {
          return@withEditor 0
        }
        val safeText = text ?: return@withEditor (lineCount - 1)
        attemptInsert(safeText, attempt = 0)
        return@withEditor (lineCount - 1)
      } ?: -1

  /**
   * 尝试在末尾行 insert, 失败时按 [RETRY_DELAYS_MS] 链式延迟重试.
   * 全部失败时把内容加到 [pendingAppends] 队列, 等待 [scheduleFlush] 触发再写.
   */
  private fun attemptInsert(text: CharSequence, attempt: Int) {
    val target = editor ?: return
    if (target.isReleased) return
    val currentLine = (target.lineCount - 1).coerceAtLeast(0)
    try {
      val currentCol = target.getText().getColumnCount(currentLine).coerceAtLeast(0)
      target.getText().insert(currentLine, currentCol, text)
      // insert 成功, 顺便把 pending 队列里积压的也写进去
      if (pendingAppends.isNotEmpty()) flushPending()
      return
    } catch (e: ArrayIndexOutOfBoundsException) {
      // sora-editor 0.23.6 race condition (upstream bug):
      //   CodeEditor.afterInsert -> LineBreakLayout.afterInsert
      //   -> LineBreakLayout.measureLineAndUpdateInlineWidths
      //   -> BlockIntList.set (index=0, length=0)
      //
      // 当 LineBreakLayout 仍在做异步 `measureAllLines` 时 (setText 或
      // 大批量 replace 后), `inlineElementsWidths` 和 `widthMaintainer`
      // 仍为空, 这时 insert 会抛 ArrayIndexOutOfBoundsException.
      // sora-editor 0.23.6 是 Maven Central 最新版 (2025-06-22), 不能升级.
      // 防御策略: 多次重试 + pending queue.
      if (attempt >= RETRY_DELAYS_MS.size) {
        // 用完所有重试, 暂存到 pending 队列, 稍后由 scheduleFlush 触发再写.
        // 避免之前那种 "dropping this append" 静默丢失.
        log.warn(
            "EditorFeatures.append race condition: all {} retries exhausted, " +
                "queued (pending size: {})",
            RETRY_DELAYS_MS.size,
            pendingAppends.size + 1)
        pendingAppends.add(PendingAppend(text))
        scheduleFlush()
        return
      }
      val delayMs = RETRY_DELAYS_MS[attempt]
      log.warn(
          "EditorFeatures.append race condition, retrying in {}ms (attempt {}/{})",
          delayMs,
          attempt + 1,
          RETRY_DELAYS_MS.size)
      target.postDelayedInLifecycle({ attemptInsert(text, attempt + 1) }, delayMs)
    }
  }

  /** 调度一次 flushPending. 多次调用合并为一次, 避免重复排队. */
  private fun scheduleFlush() {
    val target = editor ?: return
    if (flushScheduled) return
    flushScheduled = true
    target.postDelayedInLifecycle(
        {
          flushScheduled = false
          flushPending()
        },
        PENDING_FLUSH_DELAY_MS)
  }

  /**
   * 把 [pendingAppends] 队列里的内容依次 append 到末尾. 失败项会按次数重试,
   * 达到上限后丢弃,避免坏布局状态导致无限循环刷日志和消耗 CPU.
   */
  private fun flushPending() {
    val target = editor ?: return
    if (target.isReleased) return
    if (pendingAppends.isEmpty()) return
    while (true) {
      val pending = pendingAppends.poll() ?: return
      try {
        val currentLine = (target.lineCount - 1).coerceAtLeast(0)
        val currentCol = target.getText().getColumnCount(currentLine).coerceAtLeast(0)
        target.getText().insert(currentLine, currentCol, pending.text)
        // 成功, 继续 flush 下一项
      } catch (e: ArrayIndexOutOfBoundsException) {
        val nextAttempts = pending.flushAttempts + 1
        if (nextAttempts >= MAX_PENDING_FLUSH_ATTEMPTS) {
          log.error(
              "EditorFeatures.append pending flush failed {} times; dropping pending append to stop retry loop",
              nextAttempts,
              e)
        } else {
          pendingAppends.add(pending.copy(flushAttempts = nextAttempts))
          log.debug(
              "EditorFeatures.append pending flush failed, will retry (attempt {}/{}, pending size: {})",
              nextAttempts,
              MAX_PENDING_FLUSH_ATTEMPTS,
              pendingAppends.size)
          scheduleFlush()
        }
        return
      }
    }
  }

  private data class PendingAppend(val text: CharSequence, val flushAttempts: Int = 0)

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
