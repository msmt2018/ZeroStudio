package com.zerostudio.idea.editor.core.cursor

import android.view.KeyEvent
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.text.CharPosition

/**
 * 多光标移动方向
 */
enum class CursorDirection { LEFT, RIGHT, UP, DOWN, HOME, END }

/**
 * 工业级多光标键盘操作与全向移动导航器
 */
class ZeroMultiCursorNavigator(private val editor: ZeroCodeEditorView) {

    private val model: ZeroMultiCursorModel
        get() = editor.multiCursorModel

    /**
     * 处理键盘方向键与导航键事件
     * @return true 表示已被多光标引擎消费，拦截原生单光标默认行为
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (model.selections.size <= 1) return false // 单光标退回原生高效处理

        val isShiftPressed = event.isShiftPressed
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveCursors(CursorDirection.LEFT, isShiftPressed)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveCursors(CursorDirection.RIGHT, isShiftPressed)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveCursors(CursorDirection.UP, isShiftPressed)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveCursors(CursorDirection.DOWN, isShiftPressed)
                true
            }
            KeyEvent.KEYCODE_MOVE_HOME -> {
                moveCursors(CursorDirection.HOME, isShiftPressed)
                true
            }
            KeyEvent.KEYCODE_MOVE_END -> {
                moveCursors(CursorDirection.END, isShiftPressed)
                true
            }
            else -> false
        }
    }

    /**
     * 同步移动所有光标并支持选区扩选
     */
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
                        // 选区折叠到最左侧
                        val target = sel.start
                        newLine = target.line
                        newCol = target.column
                    } else if (active.index > 0) {
                        val prevPos = indexer.getCharPosition(active.index - 1)
                        newLine = prevPos.line
                        newCol = prevPos.column
                    }
                }
                CursorDirection.RIGHT -> {
                    if (!selecting && !sel.isCollapsed) {
                        val target = sel.end
                        newLine = target.line
                        newCol = target.column
                    } else if (active.index < content.length()) {
                        val nextPos = indexer.getCharPosition(active.index + 1)
                        newLine = nextPos.line
                        newCol = nextPos.column
                    }
                }
                CursorDirection.UP -> {
                    if (newLine > 0) {
                        newLine--
                        val maxColInNewLine = content.getColumnCount(newLine)
                        newCol = newCol.coerceAtMost(maxColInNewLine)
                    }
                }
                CursorDirection.DOWN -> {
                    if (newLine < content.lineCount - 1) {
                        newLine++
                        val maxColInNewLine = content.getColumnCount(newLine)
                        newCol = newCol.coerceAtMost(maxColInNewLine)
                    }
                }
                CursorDirection.HOME -> {
                    // 智能定位到行首第一个非空白字符
                    val lineStr = content.getLine(newLine)
                    var firstNonWs = 0
                    while (firstNonWs < lineStr.length && lineStr[firstNonWs].isWhitespace()) {
                        firstNonWs++
                    }
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

        // 刷新模型并自动合并相交光标
        replaceSelections(newSelections)
        editor.postInvalidateOnAnimation()
    }

    /**
     * 智能多光标粘贴：如果剪贴板行数与光标数量相等，执行 1:1 分发，否则广播粘贴
     */
    fun executeSmartPaste(clipboardText: String) {
        val lines = clipboardText.replace("\r\n", "\n").split("\n")
        val selections = model.selections

        if (lines.size == selections.size && selections.size > 1) {
            // 按照逆序操作，防止前面插入改变后方坐标
            val sortedWithIndex = selections.mapIndexed { index, sel -> Pair(index, sel) }
                .sortedByDescending { it.second.start.index }

            val content = editor.text
            val indexer = content.indexer
            val updatedPositions = arrayOfNulls<CursorSelection>(selections.size)

            for ((origIndex, sel) in sortedWithIndex) {
                val lineText = lines[origIndex]
                if (!sel.isCollapsed) {
                    content.delete(sel.start.line, sel.start.column, sel.end.line, sel.end.column)
                }
                content.insert(sel.start.line, sel.start.column, lineText)
                val newPos = indexer.getCharPosition(sel.start.index + lineText.length)
                updatedPositions[origIndex] = CursorSelection(newPos, newPos)
            }

            replaceSelections(updatedPositions.filterNotNull())
        } else {
            // 普通广播粘贴
            model.executeBatchInsert(clipboardText)
        }
        editor.postInvalidateOnAnimation()
    }

    private fun replaceSelections(newSelections: List<CursorSelection>) {
        val field = ZeroMultiCursorModel::class.java.getDeclaredField("_selections")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val internalList = field.get(model) as MutableList<CursorSelection>
        internalList.clear()
        internalList.addAll(newSelections)
        model.normalizeAndMergeSelections()
    }
}