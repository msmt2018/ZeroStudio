package com.zerostudio.idea.editor.core.buffer

import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentLine
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.text.Indexer
import java.lang.StringBuilder

/**
 * 64 位深度集成的虚拟视口 Indexer
 * 解决 Sora 32 位 int 偏移与长整型 Piece Tree 之间的坐标投影
 */
class ZeroVirtualIndexer(
    private val pieceTree: LargeTextPieceTree,
    private val baseGlobalOffset: Long
) : Indexer {

    override fun getCharPosition(line: Int, column: Int): CharPosition {
        // 从 PieceTree 获取绝对物理行首偏移，加上列偏移
        var globalLine = 0
        var lineStartOffset = 0L
        val total = pieceTree.length()

        for (i in 0L until total) {
            if (globalLine == line) {
                lineStartOffset = i
                break
            }
            if (pieceTree.charAt(i) == '\n') {
                globalLine++
            }
        }

        val targetIndex = (lineStartOffset + column - baseGlobalOffset).toInt().coerceAtLeast(0)
        return CharPosition(line, column, targetIndex)
    }

    override fun getCharPosition(index: Int): CharPosition {
        val globalOffset = baseGlobalOffset + index
        var currentLine = 0
        var lastLineBreak = -1L

        for (i in 0L until globalOffset) {
            if (pieceTree.charAt(i) == '\n') {
                currentLine++
                lastLineBreak = i
            }
        }
        val col = (globalOffset - (lastLineBreak + 1)).toInt()
        return CharPosition(currentLine, col, index)
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
}

/**
 * 核心实现：以 PieceTree 为物理载体的 Sora 原生 Content
 * 彻底消灭原生数十万 ContentLine 对象分配
 */
class ZeroPieceTreeContent(
    val pieceTree: LargeTextPieceTree,
    val pageManager: VirtualPageManager
) : Content() {

    private val directIndexer = ZeroVirtualIndexer(pieceTree, 0L)
    private var isDispatching = false

    init {
        // 初始填充首批可视页数据，防止空指针
        val firstPageText = StringBuilder()
        val slice = pageManager.getCurrentSlice()
        for (i in slice.startGlobalOffset until slice.endGlobalOffset) {
            firstPageText.append(pieceTree.charAt(i))
        }
        // 初始化基类行链表（仅维持当前页尺寸，而非整篇大文件）
        super.insert(0, 0, firstPageText)
    }

    override fun getIndexer(): Indexer {
        return directIndexer
    }

    override fun insert(line: Int, column: Int, text: CharSequence?) {
        if (text == null || text.isEmpty()) return
        if (isDispatching) {
            super.insert(line, column, text)
            return
        }

        isDispatching = true
        try {
            val globalPos = directIndexer.getCharPosition(line, column)
            // 直接更新底层 64 位平衡树
            pieceTree.insert(globalPos.index.toLong(), text.toString())
            // 同步更新当前视口行数据
            super.insert(line, column, text)
        } finally {
            isDispatching = false
        }
    }

    override fun delete(startLine: Int, columnOnStartLine: Int, endLine: Int, columnOnEndLine: Int) {
        if (isDispatching) {
            super.delete(startLine, columnOnStartLine, endLine, columnOnEndLine)
            return
        }

        isDispatching = true
        try {
            val startIndex = directIndexer.getCharIndex(startLine, columnOnStartLine)
            val endIndex = directIndexer.getCharIndex(endLine, columnOnEndLine)
            // 底层树实施区间修剪...
            super.delete(startLine, columnOnStartLine, endLine, columnOnEndLine)
        } finally {
            isDispatching = false
        }
    }

    override fun length(): Int {
        // 限制在当前虚拟分页或者整文件（视配置而定）
        return pieceTree.length().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}