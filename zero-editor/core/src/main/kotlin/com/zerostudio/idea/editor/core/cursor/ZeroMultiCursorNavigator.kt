package com.zerostudio.idea.editor.core.cursor

import android.view.KeyEvent
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView

enum class CursorDirection { LEFT, RIGHT, UP, DOWN, HOME, END }

class ZeroMultiCursorNavigator(private val editor: ZeroCodeEditorView) {

    private val model: ZeroMultiCursorModel get() = editor.multiCursorModel

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (model.selections.size <= 1) return false

        val isShiftPressed = event.isShiftPressed
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { moveCursors(CursorDirection.LEFT, isShiftPressed); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { moveCursors(CursorDirection.RIGHT, isShiftPressed); true }
            KeyEvent.KEYCODE_DPAD_UP -> { moveCursors(CursorDirection.UP, isShiftPressed); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { moveCursors(CursorDirection.DOWN, isShiftPressed); true }
            KeyEvent.KEYCODE_MOVE_HOME -> { moveCursors(CursorDirection.HOME, isShiftPressed); true }
            KeyEvent.KEYCODE_MOVE_END -> { moveCursors(CursorDirection.END, isShiftPressed); true }
            else -> false
        }
    }

    fun moveCursors(direction: CursorDirection, selecting: Boolean) {
        val content = editor.text
        val indexer = content.indexer
        val newSelections = mutableListOf<CursorSelection>()

        for (sel in model.selections) {
            val active = sel.active
            val anchor = if (selecting) sel.anchor else null

            var newLine = active.line
            var newCol = active.column

            when (direction) {
                CursorDirection.LEFT -> {
                    if (!selecting && !sel.isCollapsed) {
                        newLine = sel.start.line
                        newCol = sel.start.column
                    } else if (active.index > 0) {
                        val prev = indexer.getCharPosition(active.index - 1)
                        newLine = prev.line
                        newCol = prev.column
                    }
                }
                CursorDirection.RIGHT -> {
                    if (!selecting && !sel.isCollapsed) {
                        newLine = sel.end.line
                        newCol = sel.end.column
                    } else if (active.index < content.length()) {
                        val next = indexer.getCharPosition(active.index + 1)
                        newLine = next.line
                        newCol = next.column
                    }
                }
                CursorDirection.UP -> {
                    if (newLine > 0) {
                        newLine--
                        newCol = newCol.coerceAtMost(content.getColumnCount(newLine))
                    }
                }
                CursorDirection.DOWN -> {
                    if (newLine < content.lineCount - 1) {
                        newLine++
                        newCol = newCol.coerceAtMost(content.getColumnCount(newLine))
                    }
                }
                CursorDirection.HOME -> {
                    val line = content.getLine(newLine)
                    var firstNonWs = 0
                    while (firstNonWs < line.length && line[firstNonWs].isWhitespace()) firstNonWs++
                    newCol = if (newCol == firstNonWs) 0 else firstNonWs
                }
                CursorDirection.END -> {
                    newCol = content.getColumnCount(newLine)
                }
            }

            val newActive = indexer.getCharPosition(newLine, newCol)
            val newAnchor = anchor ?: newActive
            newSelections.add(CursorSelection(newAnchor, newActive))
        }

        model.updateSelectionList(newSelections)
        editor.postInvalidateOnAnimation()
    }

    fun executeSmartPaste(clipboardText: String) {
        val lines = clipboardText.replace("\r\n", "\n").split("\n")
        val selections = model.selections

        if (lines.size == selections.size && selections.size > 1) {
            val sortedWithIndex = selections.mapIndexed { idx, sel -> Pair(idx, sel) }
                .sortedByDescending { it.second.start.index }

            val content = editor.text
            val indexer = content.indexer
            val updated = arrayOfNulls<CursorSelection>(selections.size)

            for ((origIdx, sel) in sortedWithIndex) {
                val lineText = lines[origIdx]
                if (!sel.isCollapsed) {
                    content.delete(sel.start.line, sel.start.column, sel.end.line, sel.end.column)
                }
                content.insert(sel.start.line, sel.start.column, lineText)
                val newPos = indexer.getCharPosition(sel.start.index + lineText.length)
                updated[origIdx] = CursorSelection(newPos, newPos)
            }
            model.updateSelectionList(updated.filterNotNull())
        } else {
            model.executeBatchInsert(clipboardText)
        }
        editor.postInvalidateOnAnimation()
    }
}