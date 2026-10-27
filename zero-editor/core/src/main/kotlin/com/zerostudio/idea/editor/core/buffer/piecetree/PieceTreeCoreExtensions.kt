package com.zerostudio.idea.editor.core.buffer.piecetree

import com.zerostudio.idea.editor.core.buffer.model.Position

/**
 * 校验跨节点物理行 CRLF 分裂
 */
fun PieceTreeCore.validateCRLFWithPrevNode(node: TreeNode): Boolean {
    if (node === sentinel || node.piece.length == 0L) return false
    val prev = node.prevNode(sentinel)
    if (prev === sentinel || prev.piece.length == 0L) return false

    // 获取前驱节点的最后一个字符和当前节点的第一个字符
    val lastCharOfPrev = charAt(calculateNodeStartOffset(prev) + prev.piece.length - 1L)
    val firstCharOfCurr = charAt(calculateNodeStartOffset(node))

    return lastCharOfPrev == '\r' && firstCharOfCurr == '\n'
}

/**
 * O(log N) 快速由绝对行号 (1-indexed) 与列号 (1-indexed) 查询 64 位字符偏移量
 */
fun PieceTreeCore.getOffsetAt(line: Long, column: Long): Long {
    var remainingLine = line - 1L
    var curr = root
    var accumulatedOffset = 0L

    while (curr !== sentinel) {
        if (curr.left !== sentinel && curr.lf_left >= remainingLine) {
            curr = curr.left
        } else {
            val currentLeftLf = if (curr.left !== sentinel) curr.lf_left else 0L
            val currentLeftSize = if (curr.left !== sentinel) curr.size_left else 0L

            if (remainingLine <= currentLeftLf + curr.piece.lineFeedCnt) {
                // 目标落在此节点
                val lineInPiece = remainingLine - currentLeftLf
                val starts = getBufferLineStarts(curr.piece.bufferIndex)
                val pieceBufferStart = starts[curr.piece.start.line] + curr.piece.start.column
                val targetLineInBuf = curr.piece.start.line + lineInPiece.toInt()
                val lineStartInBuf = starts[targetLineInBuf]

                val offsetInsideNode = (lineStartInBuf - pieceBufferStart).toLong()
                return accumulatedOffset + currentLeftSize + offsetInsideNode + (column - 1L)
            } else {
                remainingLine -= (currentLeftLf + curr.piece.lineFeedCnt)
                accumulatedOffset += (currentLeftSize + curr.piece.length)
                curr = curr.right
            }
        }
    }
    return getLength()
}

/**
 * O(log N) 快速由绝对偏移获取行列坐标 Position(Line, Column)
 */
fun PieceTreeCore.getPositionAt(offset: Long): Position {
    val clampedOffset = offset.coerceIn(0L, getLength())
    var curr = root
    var rem = clampedOffset
    var lineAccumulator = 1L
    var lastLineBreakGlobalOffset = -1L

    while (curr !== sentinel) {
        val leftLen = if (curr.left !== sentinel) curr.size_left else 0L
        val leftLf = if (curr.left !== sentinel) curr.lf_left else 0L

        if (rem < leftLen) {
            curr = curr.left
        } else if (rem >= leftLen + curr.piece.length) {
            rem -= (leftLen + curr.piece.length)
            lineAccumulator += (leftLf + curr.piece.lineFeedCnt)
            curr = curr.right
        } else {
            // 命中节点，在节点内部通过 lineStarts 二分行列
            val offsetInPiece = rem - leftLen
            lineAccumulator += leftLf

            val starts = getBufferLineStarts(curr.piece.bufferIndex)
            val globalStartInBuf = starts[curr.piece.start.line] + curr.piece.start.column
            val targetCharOffsetInBuf = globalStartInBuf + offsetInPiece

            var low = curr.piece.start.line
            var high = curr.piece.end.line
            var foundLine = low

            while (low <= high) {
                val mid = (low + high) ushr 1
                if (starts[mid] <= targetCharOffsetInBuf) {
                    foundLine = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }

            val finalLine = lineAccumulator + (foundLine - curr.piece.start.line)
            val col = targetCharOffsetInBuf - starts[foundLine] + 1
            return Position(finalLine.toInt(), col.toInt())
        }
    }
    return Position(1, 1)
}

internal fun PieceTreeCore.calculateNodeStartOffset(node: TreeNode): Long {
    var offset = if (node.left !== sentinel) node.size_left else 0L
    var curr = node
    while (curr !== root && curr.parent !== sentinel) {
        if (curr.parent.right === curr) {
            offset += (curr.parent.size_left + curr.parent.piece.length)
        }
        curr = curr.parent
    }
    return offset
}

internal fun TreeNode.prevNode(sentinel: TreeNode): TreeNode {
    if (left !== sentinel) {
        var curr = left
        while (curr.right !== sentinel) curr = curr.right
        return curr
    }
    var curr = this
    var p = parent
    while (p !== sentinel && curr === p.left) {
        curr = p
        p = p.parent
    }
    return p
}