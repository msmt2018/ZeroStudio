package com.zerostudio.idea.editor.core.cursor

import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import java.util.Collections

data class CursorSelection(
    val anchor: CharPosition,
    val active: CharPosition
) : Comparable<CursorSelection> {
    val isCollapsed: Boolean get() = anchor.index == active.index
    val start: CharPosition get() = if (anchor.index <= active.index) anchor else active
    val end: CharPosition get() = if (anchor.index <= active.index) active else anchor

    override fun compareTo(other: CursorSelection): Int = this.start.index.compareTo(other.start.index)

    fun intersectsWith(other: CursorSelection): Boolean =
        maxOf(this.start.index, other.start.index) <= minOf(this.end.index, other.end.index)
}

class ZeroMultiCursorModel(private val contentProvider: () -> Content) {
    private val _selections = mutableListOf<CursorSelection>()
    val selections: List<CursorSelection> get() = Collections.unmodifiableList(_selections)

    var primaryIndex: Int = 0
        private set

    val primarySelection: CursorSelection
        get() = if (_selections.isNotEmpty() && primaryIndex in _selections.indices) {
            _selections[primaryIndex]
        } else {
            val zero = CharPosition(0, 0, 0)
            CursorSelection(zero, zero)
        }

    init {
        val zero = CharPosition(0, 0, 0)
        _selections.add(CursorSelection(zero, zero))
    }

    fun setSingleCursor(line: Int, column: Int) {
        val content = contentProvider()
        val pos = content.indexer.getCharPosition(line, column)
        _selections.clear()
        _selections.add(CursorSelection(pos, pos))
        primaryIndex = 0
    }

    fun addCursor(line: Int, column: Int) {
        val content = contentProvider()
        val pos = content.indexer.getCharPosition(line, column)
        _selections.add(CursorSelection(pos, pos))
        normalizeAndMergeSelections()
    }

    /**
     * 对应桌面 IDE 的 Ctrl + D: 自动选中当前词或向下选取下一个相同项
     */
    fun selectNextOccurrence() {
        val content = contentProvider()
        val primary = primarySelection
        if (primary.isCollapsed) {
            val wordRange = findWordAt(primary.active)
            if (wordRange != null) {
                _selections[primaryIndex] = wordRange
            }
            return
        }

        val query = content.subSequence(primary.start.index, primary.end.index).toString()
        val textStr = content.toString()
        var nextIndex = textStr.indexOf(query, primary.end.index)
        if (nextIndex == -1) {
            nextIndex = textStr.indexOf(query, 0) // 回绕查找
        }

        if (nextIndex != -1) {
            val startPos = content.indexer.getCharPosition(nextIndex)
            val endPos = content.indexer.getCharPosition(nextIndex + query.length)
            _selections.add(CursorSelection(startPos, endPos))
            normalizeAndMergeSelections()
        }
    }

    /**
     * 列选择/矩形框选 (Column/Box Selection)
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
            val lineLen = content.getColumnCount(l)
            val actualStart = minCol.coerceAtMost(lineLen)
            val actualEnd = maxCol.coerceAtMost(lineLen)
            val anchor = indexer.getCharPosition(l, actualStart)
            val active = indexer.getCharPosition(l, actualEnd)
            _selections.add(CursorSelection(anchor, active))
        }
        primaryIndex = _selections.size - 1
        normalizeAndMergeSelections()
    }

    fun normalizeAndMergeSelections() {
        if (_selections.size <= 1) return
        _selections.sort()
        val merged = mutableListOf<CursorSelection>()
        var current = _selections[0]

        for (i in 1 until _selections.size) {
            val next = _selections[i]
            if (current.intersectsWith(next) || current.end.index == next.start.index) {
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
     * 关键算法：逆序执行批量插入，避免前序写入导致后序光标偏移失效
     */
    fun executeBatchInsert(textToInsert: String) {
        val content = contentProvider()
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
                val prev = content.indexer.getCharPosition(sel.start.index - 1)
                content.delete(prev.line, prev.column, sel.start.line, sel.start.column)
                newSelections.add(CursorSelection(prev, prev))
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
        while (startCol > 0 && Character.isJavaIdentifierPart(line[startCol - 1])) startCol--
        while (endCol < line.length && Character.isJavaIdentifierPart(line[endCol])) endCol++

        if (startCol == endCol) return null
        val indexer = content.indexer
        return CursorSelection(
            indexer.getCharPosition(pos.line, startCol),
            indexer.getCharPosition(pos.line, endCol)
        )
    }
}