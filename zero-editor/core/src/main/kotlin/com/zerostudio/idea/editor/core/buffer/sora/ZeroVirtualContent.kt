package com.zerostudio.idea.editor.core.buffer.sora

import com.zerostudio.idea.editor.core.buffer.paging.VirtualPageManager
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentLine
import io.github.rosemoe.sora.text.Indexer

/**
 * 解决 Sora-Editor 32位坐标与 64位大文件视口绝对坐标的双向透明映射
 */
class ZeroVirtualIndexer(
    private val pageManager: VirtualPageManager
) : Indexer {

    override fun getCharPosition(line: Int, column: Int): CharPosition {
        val slice = pageManager.getCurrentSlice()
        val localOffset = computeLocalOffset(line, column)
        return CharPosition(line, column, localOffset)
    }

    override fun getCharPosition(index: Int): CharPosition {
        // 在当前视口缓冲内二分查找行列
        val content = pageManager.pieceTree
        val slice = pageManager.getCurrentSlice()
        val globalOffset = slice.startOffset + index

        val pos = content.getPositionAt(globalOffset)
        val localLine = (pos.line - slice.startLine - 1).toInt().coerceAtLeast(0)
        return CharPosition(localLine, pos.column - 1, index)
    }

    override fun getCharIndex(line: Int, column: Int): Int = computeLocalOffset(line, column)
    override fun getCharLine(index: Int): Int = getCharPosition(index).line
    override fun getCharColumn(index: Int): Int = getCharPosition(index).column

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

    private fun computeLocalOffset(line: Int, col: Int): Int {
        val slice = pageManager.getCurrentSlice()
        val globalLine = slice.startLine + line + 1L
        val globalOffset = pageManager.pieceTree.getOffsetAt(globalLine, col.toLong() + 1L)
        return (globalOffset - slice.startOffset).toInt().coerceAtLeast(0)
    }
}