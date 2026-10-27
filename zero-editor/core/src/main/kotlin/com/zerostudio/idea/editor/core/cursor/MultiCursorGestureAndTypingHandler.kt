package com.zerostudio.idea.editor.core.cursor

import android.view.KeyEvent
import android.view.MotionEvent
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.text.CharPosition
import kotlin.math.abs

class MultiCursorGestureAndTypingHandler(private val editor: ZeroCodeEditorView) {

    private val model: ZeroMultiCursorModel get() = editor.multiCursorModel
    private var isBoxSelecting = false
    private var boxStartPoint: Pair<Int, Int>? = null

    /**
     * 接管触控手势，支持矩形框选 (Column / Box Selection)
     */
    fun handleTouchEvent(event: MotionEvent): Boolean {
        // Alt + 拖拽 或 双指长按：激活矩形框选
        if (event.isAltPressed || event.pointerCount == 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val p = editor.getPointPositionOnScreen(event.x, event.y)
                    boxStartPoint = p
                    isBoxSelecting = true
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isBoxSelecting && boxStartPoint != null) {
                        val currentPoint = editor.getPointPositionOnScreen(event.x, event.y)
                        val start = boxStartPoint!!
                        // 建立从 start 到 currentPoint 的多行矩形选区
                        model.setColumnSelection(
                            startLine = start.first,
                            startCol = start.second,
                            endLine = currentPoint.first,
                            endCol = currentPoint.second
                        )
                        editor.invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                    isBoxSelecting = false
                    boxStartPoint = null
                    return true
                }
            }
        }
        return false
    }

    /**
     * 多光标符号自动包裹与闭合推进 (Auto-Closing & Surround)
     */
    fun onMultiCursorTypeCharacter(char: Char): Boolean {
        if (model.selections.size <= 1) return false // 单光标直接交给 Sora 原生

        val closingPair = when (char) {
            '(' -> ')'
            '{' -> '}'
            '[' -> ']'
            '"' -> '"'
            '\'' -> '\''
            else -> null
        }

        if (closingPair != null) {
            val content = editor.text
            // 逆序执行，防止前序插入修改后续光标位置
            val sortedSelections = model.selections.sortedByDescending { it.start.index }
            val newSelections = mutableListOf<CursorSelection>()

            for (sel in sortedSelections) {
                if (!sel.isCollapsed) {
                    // 选区包裹：在选区两端包裹
                    val selectedText = content.subSequence(sel.start.index, sel.end.index).toString()
                    content.replace(sel.start.line, sel.start.column, sel.end.line, sel.end.column, "$char$selectedText$closingPair")
                    val newStart = content.indexer.getCharPosition(sel.start.index + 1)
                    val newEnd = content.indexer.getCharPosition(sel.start.index + 1 + selectedText.length)
                    newSelections.add(CursorSelection(newStart, newEnd))
                } else {
                    // 闭合符自动补全并将光标居中
                    content.insert(sel.start.line, sel.start.column, "$char$closingPair")
                    val centerPos = content.indexer.getCharPosition(sel.start.index + 1)
                    newSelections.add(CursorSelection(centerPos, centerPos))
                }
            }
            replaceSelections(newSelections.reversed())
            editor.invalidate()
            return true
        }

        return false
    }

    private fun replaceSelections(newSelections: List<CursorSelection>) {
        val field = ZeroMultiCursorModel::class.java.getDeclaredField("_selections")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(model) as MutableList<CursorSelection>
        list.clear()
        list.addAll(newSelections)
        model.normalizeAndMergeSelections()
    }
}