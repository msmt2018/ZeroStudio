package com.zerostudio.idea.editor.core.buffer

import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentLine
import io.github.rosemoe.sora.text.Indexer
import java.lang.StringBuilder

/**
 * 专为 Sora 设计的 64 位 Piece Tree Indexer 适配器
 * 行与列的计算直接由红黑树在 O(log N) 内返回，杜绝遍历式查找引发的 ANR
 */
class PieceTreeIndexer(private val pieceTree: LargeTextPieceTree) : Indexer {

    override fun getCharPosition(line: Int, column: Int): CharPosition {
        val lineStartOffset = pieceTree.getOffsetForLine(line)
        val targetOffset = (lineStartOffset + column).coerceAtMost(pieceTree.length())
        return CharPosition(line, column, targetOffset.toInt())
    }

    override fun getCharPosition(index: Int): CharPosition {
        // 二分定位全局字符位于哪一行
        var low = 0
        var high = pieceTree.lineCount() - 1
        var line = 0
        val targetOffset = index.toLong()

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midOffset = pieceTree.getOffsetForLine(mid)
            if (midOffset <= targetOffset) {
                line = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        val lineStart = pieceTree.getOffsetForLine(line)
        val col = (targetOffset - lineStart).toInt()
        return CharPosition(line, col, index)
    }

    override fun getCharIndex(line: Int, column: Int): Int {
        return getCharPosition(line, column).index
    }

    override fun getCharLine(index: Int): Int {
        return getCharPosition(index).line
    }

    override fun getCharColumn(index: Int): Int {
        return getCharPosition(index).column
    }

    override fun getCharPosition(index: Int, dest: CharPosition) {
        val p = getCharPosition(index)
        dest.line = p.line
        dest.column = p.column
        dest.index = p.index
    }

    override fun getCharPosition(line: Int, column: Int, dest: CharPosition) {
        val p = getCharPosition(line, column)
        dest.line = p.line
        dest.column = p.column
        dest.index = p.index
    }
}

/**
 * 重写的 Sora Content 核心实现
 * 内存中零冗余分配，Sora 渲染引擎查询某一行时，通过按需即时物化（Lazy Materialization）
 * 从 Piece Tree 获取该行内容构建轻量缓存，完全兼容 Sora-Editor 渲染层。
 */
class ZeroPieceTreeContent(
    val pieceTree: LargeTextPieceTree
) : Content("", false) {

    private val fastIndexer = PieceTreeIndexer(pieceTree)
    private val lineCache = HashMap<Int, ContentLine>(128)

    override fun getIndexer(): Indexer {
        return fastIndexer
    }

    override fun length(): Int {
        return pieceTree.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    override fun getLineCount(): Int {
        return pieceTree.lineCount()
    }

    override fun getLine(line: Int): ContentLine {
        require(line in 0 until getLineCount()) { "Line index out of bounds: $line" }
        // 局部 LRU 视口缓存优化
        lineCache[line]?.let { return it }

        val start = pieceTree.getOffsetForLine(line)
        val end = if (line + 1 < getLineCount()) {
            pieceTree.getOffsetForLine(line + 1) - 1 // 剥离尾部换行
        } else {
            pieceTree.length()
        }

        val len = (end - start).toInt().coerceAtLeast(0)
        val sb = StringBuilder(len)
        for (i in 0 until len) {
            sb.append(pieceTree.charAt(start + i))
        }

        val contentLine = ContentLine(sb)
        if (lineCache.size > 200) {
            lineCache.clear()
        }
        lineCache[line] = contentLine
        return contentLine
    }

    override fun getColumnCount(line: Int): Int {
        val start = pieceTree.getOffsetForLine(line)
        val end = if (line + 1 < getLineCount()) {
            pieceTree.getOffsetForLine(line + 1) - 1
        } else {
            pieceTree.length()
        }
        return (end - start).toInt().coerceAtLeast(0)
    }

    override fun charAt(line: Int, column: Int): Char {
        val offset = pieceTree.getOffsetForLine(line) + column
        return pieceTree.charAt(offset)
    }

    override fun insert(line: Int, column: Int, text: CharSequence?) {
        if (text.isNullOrEmpty()) return
        val offset = pieceTree.getOffsetForLine(line) + column
        pieceTree.insert(offset, text.toString())
        lineCache.clear()
    }

    override fun delete(startLine: Int, columnOnStartLine: Int, endLine: Int, columnOnEndLine: Int) {
        val startOffset = pieceTree.getOffsetForLine(startLine) + columnOnStartLine
        val endOffset = pieceTree.getOffsetForLine(endLine) + columnOnEndLine
        val deleteLen = endOffset - startOffset
        if (deleteLen > 0) {
            pieceTree.delete(startOffset, deleteLen)
            lineCache.clear()
        }
    }
}