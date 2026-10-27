package com.zerostudio.idea.editor.core.cursor

import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import java.util.Collections

/**
 * 单个光标/选区描述符
 * anchor: 选区锚点（手势起点）
 * active: 活动光标位置（手势终点/键入插入点）
 */
data class CursorSelection(
    val anchor: CharPosition,
    val active: CharPosition
) : Comparable<CursorSelection> {
    val isCollapsed: Boolean
        get() = anchor.index == active.index

    val start: CharPosition
        get() = if (anchor.index <= active.index) anchor else active

    val end: CharPosition
        get() = if (anchor.index <= active.index) active else anchor

    override fun compareTo(other: CursorSelection): Int {
        return this.start.index.compareTo(other.start.index)
    }

    fun containsIndex(index: Int): Boolean {
        return index in start.index..end.index
    }

    fun intersectsWith(other: CursorSelection): Boolean {
        return maxOf(this.start.index, other.start.index) <= minOf(this.end.index, other.end.index)
    }
}

/**
 * 工业级多光标与多选区管理器
 * 支持光标排序、重叠自动合并、多光标同步编辑操作
 */
class ZeroMultiCursorModel(private val contentProvider: () -> Content) {

    private val _selections = mutableListOf<CursorSelection>()
    val selections: List<CursorSelection>
        get() = Collections.unmodifiableList(_selections)

    var primaryIndex: Int = 0
        private set

    val primarySelection: CursorSelection
        get() = if (_selections.isNotEmpty() && primaryIndex in _selections.indices) {
            _selections[primaryIndex]
        } else {
            val zeroPos = CharPosition(0, 0, 0)
            CursorSelection(zeroPos, zeroPos)
        }

    init {
        val defaultPos = CharPosition(0, 0, 0)
        _selections.add(CursorSelection(defaultPos, defaultPos))
    }

    /**
     * 重置为单光标
     */
    fun setSingleCursor(line: Int, column: Int) {
        val content = contentProvider()
        val indexer = content.indexer
        val pos = indexer.getCharPosition(line, column)
        _selections.clear()
        _selections.add(CursorSelection(pos, pos))
        primaryIndex = 0
    }

    /**
     * 添加新光标 (例如 Alt + Click)
     */
    fun addCursor(line: Int, column: Int) {
        val content = contentProvider()
        val pos = content.indexer.getCharPosition(line, column)
        _selections.add(CursorSelection(pos, pos))
        normalizeAndMergeSelections()
    }

    /**
     * 选取下一个匹配项 (Ctrl/Cmd + D)
     */
    fun selectNextOccurrence() {
        val content = contentProvider()
        val primary = primarySelection
        if (primary.isCollapsed) {
            // 自动选中当前光标下的单词
            val wordRange = findWordAt(primary.active)
            if (wordRange != null) {
                _selections[primaryIndex] = wordRange
            }
            return
        }

        val query = content.subSequence(primary.start.index, primary.end.index).toString()
        val textStr = content.toString()
        val searchStart = primary.end.index

        var nextIndex = textStr.indexOf(query, searchStart)
        if (nextIndex == -1) {
            // 回绕到头部搜索
            nextIndex = textStr.indexOf(query, 0)
        }

        if (nextIndex != -1) {
            val startPos = content.indexer.getCharPosition(nextIndex)
            val endPos = content.indexer.getCharPosition(nextIndex + query.length)
            _selections.add(CursorSelection(startPos, endPos))
            normalizeAndMergeSelections()
        }
    }

    /**
     * 列选择/矩形框选 (Box Selection)
     */
    fun setColumnSelection(startLine: Int, startCol: Int, endLine: Int, endCol: Int) {
        val content = contentProvider()
        val indexer = content.indexer
        val minLine = minOf(startLine, endLine)
        val maxLine = maxOf(startLine, endLine)
        val minCol = minOf(startCol, endCol)
        val maxCol = maxOf(startCol, endCol)

        _selections.clear()
        for (l in minLine..maxLine) {
            val lineLength = content.getColumnCount(l)
            val actualStartCol = minCol.coerceAtMost(lineLength)
            val actualEndCol = maxCol.coerceAtMost(lineLength)

            val anchor = indexer.getCharPosition(l, actualStartCol)
            val active = indexer.getCharPosition(l, actualEndCol)
            _selections.add(CursorSelection(anchor, active))
        }
        primaryIndex = _selections.size - 1
        normalizeAndMergeSelections()
    }

    /**
     * 自动拓扑排序并合并重叠的光标或选区
     */
    fun normalizeAndMergeSelections() {
        if (_selections.size <= 1) return

        _selections.sort()
        val merged = mutableListOf<CursorSelection>()
        var current = _selections[0]

        for (i in 1 until _selections.size) {
            val next = _selections[i]
            if (current.intersectsWith(next) || (current.end.index == next.start.index)) {
                // 合并重叠选区
                val newStart = if (current.start.index <= next.start.index) current.start else next.start
                val newEnd = if (current.end.index >= next.end.index) current.end else next.end
                current = CursorSelection(newStart, newEnd)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        _selections.clear()
        _selections.addAll(merged)
        primaryIndex = primaryIndex.coerceIn(0, _selections.size - 1)
    }

    /**
     * 多光标同步批量键入字符
     */
    fun executeBatchInsert(textToInsert: String) {
        val content = contentProvider()
        // 关键性能逻辑：从后向前逆序插入，保证后续偏移不影响前面未执行的光标坐标！
        _selections.sortByDescending { it.start.index }

        val newSelections = mutableListOf<CursorSelection>()
        for (sel in _selections) {
            if (!sel.isCollapsed) {
                content.delete(sel.start.line, sel.start.column, sel.end.line, sel.end.column)
            }
            content.insert(sel.start.line, sel.start.column, textToInsert)
            val newPos = content.indexer.getCharPosition(sel.start.index + textToInsert.length)
            newSelections.add(CursorSelection(newPos, newPos))
        }

        _selections.clear()
        _selections.addAll(newSelections.reversed())
        normalizeAndMergeSelections()
    }

    /**
     * 多光标同步批量退格删除 (Backspace)
     */
    fun executeBatchBackspace() {
        val content = contentProvider()
        _selections.sortByDescending { it.start.index }

        val newSelections = mutableListOf<CursorSelection>()
        for (sel in _selections) {
            if (!sel.isCollapsed) {
                content.delete(sel.start.line, sel.start.column, sel.end.line, sel.end.column)
                val pos = content.indexer.getCharPosition(sel.start.index)
                newSelections.add(CursorSelection(pos, pos))
            } else if (sel.start.index > 0) {
                val prevPos = content.indexer.getCharPosition(sel.start.index - 1)
                content.delete(prevPos.line, prevPos.column, sel.start.line, sel.start.column)
                newSelections.add(CursorSelection(prevPos, prevPos))
            } else {
                newSelections.add(sel)
            }
        }

        _selections.clear()
        _selections.addAll(newSelections.reversed())
        normalizeAndMergeSelections()
    }

    private fun findWordAt(pos: CharPosition): CursorSelection? {
        val content = contentProvider()
        val line = content.getLine(pos.line)
        if (line.isEmpty()) return null

        var startCol = pos.column.coerceIn(0, line.length)
        var endCol = startCol

        while (startCol > 0 && Character.isJavaIdentifierPart(line[startCol - 1])) {
            startCol--
        }
        while (endCol < line.length && Character.isJavaIdentifierPart(line[endCol])) {
            endCol++
        }

        if (startCol == endCol) return null
        val indexer = content.indexer
        return CursorSelection(
            indexer.getCharPosition(pos.line, startCol),
            indexer.getCharPosition(pos.line, endCol)
        )
    }
}