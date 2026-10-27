package com.zerostudio.idea.editor.core.buffer.piecetree

import com.zerostudio.idea.editor.core.buffer.model.IntList

enum class NodeColor { BLACK, RED }
enum class BufferSource { ORIGINAL, ADD }

internal class BufferCursor(var line: Int, var column: Int) {
    fun copy(): BufferCursor = BufferCursor(line, column)
}

internal class Piece(
    val bufferIndex: Int,
    val start: BufferCursor,
    val end: BufferCursor,
    val length: Long,
    val lineFeedCnt: Long
)

internal class TreeNode(
    var piece: Piece,
    var color: NodeColor
) {
    var size_left: Long = 0L
    var lf_left: Long = 0L
    var parent: TreeNode = this
    var left: TreeNode = this
    var right: TreeNode = this

    fun isDetached(): Boolean = parent === this && left === this && right === this
}

data class NodePosition(
    val node: TreeNode,
    val remainder: Long,
    val nodeStartOffset: Long
)

class PieceTreeCore(
    private val originalChunks: List<CharSequence>,
    private var eol: String = "\n",
    private val eolNormalized: Boolean = true
) {
    internal val sentinel: TreeNode = TreeNode(
        piece = Piece(0, BufferCursor(0, 0), BufferCursor(0, 0), 0L, 0L),
        color = NodeColor.BLACK
    ).apply {
        parent = this
        left = this
        right = this
    }

    internal var root: TreeNode = sentinel

    // buffers[0] 始终为只追加写入缓冲 (AddBuffer)
    private val buffers: MutableList<StringBuilder> = mutableListOf(StringBuilder(64 * 1024))
    private val lineStarts: MutableList<IntList> = mutableListOf(IntList().apply { add(0) })

    private var versionId: Int = 0

    init {
        // 装载原始数据块（如 mmap 代理流）
        for (chunk in originalChunks) {
            val sb = StringBuilder(chunk)
            buffers.add(sb)
            val starts = IntList()
            starts.add(0)
            for (i in 0 until sb.length) {
                val ch = sb[i]
                if (ch == '\n') {
                    starts.add(i + 1)
                } else if (ch == '\r') {
                    if (i + 1 < sb.length && sb[i + 1] == '\n') {
                        // CRLF
                    } else {
                        starts.add(i + 1)
                    }
                }
            }
            lineStarts.add(starts)
        }

        buildInitialTree()
    }

    private fun buildInitialTree() {
        if (buffers.size <= 1) return
        for (i in 1 until buffers.size) {
            val buf = buffers[i]
            val starts = lineStarts[i]
            val breaks = (starts.size - 1).toLong()
            val piece = Piece(
                bufferIndex = i,
                start = BufferCursor(0, 0),
                end = BufferCursor(breaks.toInt(), buf.length - starts.last()),
                length = buf.length.toLong(),
                lineFeedCnt = breaks
            )
            insertAtEnd(piece)
        }
    }

    private fun insertAtEnd(piece: Piece) {
        val node = TreeNode(piece, NodeColor.RED).apply {
            left = sentinel
            right = sentinel
            parent = sentinel
        }
        if (root === sentinel) {
            node.color = NodeColor.BLACK
            root = node
            return
        }
        var current = root
        while (current.right !== sentinel) {
            current = current.right
        }
        current.right = node
        node.parent = current
        fixInsert(node)
    }

    fun getLength(): Long = calculateSize(root)
    fun getLineCount(): Long = calculateLF(root) + 1L
    fun getVersionId(): Int = versionId
    fun getEOL(): String = eol

    fun charAt(offset: Long): Char {
        require(offset in 0 until getLength()) { "Offset out of bounds: $offset" }
        val pos = findNodeAt(offset)
        val piece = pos.node.piece
        val targetBuf = buffers[piece.bufferIndex]
        val starts = lineStarts[piece.bufferIndex]
        val pieceStartOffset = starts[piece.start.line] + piece.start.column
        return targetBuf[(pieceStartOffset + pos.remainder).toInt()]
    }

    fun insert(offset: Long, text: String) {
        if (text.isEmpty()) return
        versionId++

        val addBuf = buffers[0]
        val addStarts = lineStarts[0]
        val startLine = addStarts.size - 1
        val startCol = addBuf.length - addStarts.last()

        val insertStart = addBuf.length
        addBuf.append(text)
        var addedBreaks = 0L
        for (i in insertStart until addBuf.length) {
            if (addBuf[i] == '\n') {
                addStarts.add(i + 1)
                addedBreaks++
            }
        }
        val endLine = addStarts.size - 1
        val endCol = addBuf.length - addStarts.last()

        val newPiece = Piece(
            bufferIndex = 0,
            start = BufferCursor(startLine, startCol),
            end = BufferCursor(endLine, endCol),
            length = text.length.toLong(),
            lineFeedCnt = addedBreaks
        )

        if (root === sentinel) {
            insertAtEnd(newPiece)
            return
        }

        val nodePos = findNodeAt(offset)
        splitNodeAndInsert(nodePos, newPiece)
    }

    fun delete(offset: Long, length: Long) {
        if (length <= 0L || root === sentinel) return
        versionId++
        val startPos = findNodeAt(offset)
        val endPos = findNodeAt(offset + length)

        if (startPos.node === endPos.node) {
            // 单节点局部删除
            val node = startPos.node
            val piece = node.piece
            val leftLen = startPos.remainder
            val rightLen = piece.length - endPos.remainder

            if (leftLen == 0L && rightLen == 0L) {
                rbDelete(node)
            } else if (leftLen > 0L && rightLen > 0L) {
                // 分裂保留两端
                val leftPiece = createSubPiece(piece, 0L, leftLen)
                val rightPiece = createSubPiece(piece, endPos.remainder, rightLen)
                node.piece = leftPiece
                updateTreeMetadata(node, - (length - leftPiece.length), - (piece.lineFeedCnt - leftPiece.lineFeedCnt))
                insertNodeAfter(node, TreeNode(rightPiece, NodeColor.RED).apply {
                    left = sentinel
                    right = sentinel
                    parent = sentinel
                })
            } else if (leftLen > 0L) {
                val sub = createSubPiece(piece, 0L, leftLen)
                val deltaLen = sub.length - piece.length
                val deltaLf = sub.lineFeedCnt - piece.lineFeedCnt
                node.piece = sub
                updateTreeMetadata(node, deltaLen, deltaLf)
            } else {
                val sub = createSubPiece(piece, endPos.remainder, rightLen)
                val deltaLen = sub.length - piece.length
                val deltaLf = sub.lineFeedCnt - piece.lineFeedCnt
                node.piece = sub
                updateTreeMetadata(node, deltaLen, deltaLf)
            }
            return
        }

        // 跨节点区间范围删除
        val nodesToDelete = mutableListOf<TreeNode>()
        var curr: TreeNode? = startPos.node.next()
        while (curr != null && curr !== sentinel && curr !== endPos.node) {
            nodesToDelete.add(curr)
            curr = curr.next()
        }
        nodesToDelete.forEach { rbDelete(it) }

        // 修整起始节点
        if (startPos.remainder == 0L) {
            rbDelete(startPos.node)
        } else {
            val sub = createSubPiece(startPos.node.piece, 0L, startPos.remainder)
            val dLen = sub.length - startPos.node.piece.length
            val dLf = sub.lineFeedCnt - startPos.node.piece.lineFeedCnt
            startPos.node.piece = sub
            updateTreeMetadata(startPos.node, dLen, dLf)
        }

        // 修整终止节点
        val endRemainder = endPos.node.piece.length - endPos.remainder
        if (endRemainder == 0L) {
            rbDelete(endPos.node)
        } else {
            val sub = createSubPiece(endPos.node.piece, endPos.remainder, endRemainder)
            val dLen = sub.length - endPos.node.piece.length
            val dLf = sub.lineFeedCnt - endPos.node.piece.lineFeedCnt
            endPos.node.piece = sub
            updateTreeMetadata(endPos.node, dLen, dLf)
        }
    }

    private fun createSubPiece(base: Piece, offsetInPiece: Long, length: Long): Piece {
        val starts = lineStarts[base.bufferIndex]
        val pieceStartOffset = starts[base.start.line] + base.start.column
        val subStartGlobal = pieceStartOffset + offsetInPiece
        val subEndGlobal = subStartGlobal + length

        // 二分确定子片段的首尾行号
        val startLine = binarySearchLineStarts(starts, subStartGlobal)
        val startCol = (subStartGlobal - starts[startLine]).toInt()

        val endLine = binarySearchLineStarts(starts, subEndGlobal)
        val endCol = (subEndGlobal - starts[endLine]).toInt()

        return Piece(
            bufferIndex = base.bufferIndex,
            start = BufferCursor(startLine, startCol),
            end = BufferCursor(endLine, endCol),
            length = length,
            lineFeedCnt = (endLine - startLine).toLong()
        )
    }

    private fun binarySearchLineStarts(starts: IntList, targetOffset: Long): Int {
        var low = 0
        var high = starts.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = starts[mid].toLong()
            when {
                midVal < targetOffset -> low = mid + 1
                midVal > targetOffset -> high = mid - 1
                else -> return mid
            }
        }
        return (low - 1).coerceAtLeast(0)
    }

    private fun splitNodeAndInsert(nodePos: NodePosition, newPiece: Piece) {
        val targetNode = nodePos.node
        val piece = targetNode.piece
        val offset = nodePos.remainder

        val newNode = TreeNode(newPiece, NodeColor.RED).apply {
            left = sentinel
            right = sentinel
            parent = sentinel
        }

        if (offset == 0L) {
            insertNodeBefore(targetNode, newNode)
        } else if (offset == piece.length) {
            insertNodeAfter(targetNode, newNode)
        } else {
            // 三段式切分
            val leftPiece = createSubPiece(piece, 0L, offset)
            val rightPiece = createSubPiece(piece, offset, piece.length - offset)

            targetNode.piece = leftPiece
            val dLen = leftPiece.length - piece.length
            val dLf = leftPiece.lineFeedCnt - piece.lineFeedCnt
            updateTreeMetadata(targetNode, dLen, dLf)

            val rightNode = TreeNode(rightPiece, NodeColor.RED).apply {
                left = sentinel
                right = sentinel
                parent = sentinel
            }
            insertNodeAfter(targetNode, newNode)
            insertNodeAfter(newNode, rightNode)
        }
    }

    private fun findNodeAt(offset: Long): NodePosition {
        var curr = root
        var rem = offset
        var accumulated = 0L

        while (curr !== sentinel) {
            if (curr.size_left > rem) {
                curr = curr.left
            } else if (curr.size_left + curr.piece.length > rem) {
                val nodeOffset = accumulated + curr.size_left
                return NodePosition(curr, rem - curr.size_left, nodeOffset)
            } else {
                rem -= (curr.size_left + curr.piece.length)
                accumulated += (curr.size_left + curr.piece.length)
                curr = curr.right
            }
        }
        throw IllegalStateException("Offset not found: $offset")
    }

    private fun calculateSize(node: TreeNode): Long {
        if (node === sentinel) return 0L
        return node.size_left + node.piece.length + calculateSize(node.right)
    }

    private fun calculateLF(node: TreeNode): Long {
        if (node === sentinel) return 0L
        return node.lf_left + node.piece.lineFeedCnt + calculateLF(node.right)
    }

    private fun updateTreeMetadata(node: TreeNode, deltaSize: Long, deltaLF: Long) {
        var x = node
        while (x !== root && x !== sentinel && !x.isDetached()) {
            if (x.parent.left === x) {
                x.parent.size_left += deltaSize
                x.parent.lf_left += deltaLF
            }
            x = x.parent
        }
    }

    private fun insertNodeBefore(pivot: TreeNode, node: TreeNode) {
        if (pivot.left === sentinel) {
            pivot.left = node
            node.parent = pivot
        } else {
            var curr = pivot.left
            while (curr.right !== sentinel) {
                curr = curr.right
            }
            curr.right = node
            node.parent = curr
        }
        fixInsert(node)
    }

    private fun insertNodeAfter(pivot: TreeNode, node: TreeNode) {
        if (pivot.right === sentinel) {
            pivot.right = node
            node.parent = pivot
        } else {
            var curr = pivot.right
            while (curr.left !== sentinel) {
                curr = curr.left
            }
            curr.left = node
            node.parent = curr
        }
        fixInsert(node)
    }

    private fun fixInsert(node: TreeNode) {
        var x = node
        updateTreeMetadata(x, x.piece.length, x.piece.lineFeedCnt)

        while (x !== root && x.parent.color == NodeColor.RED) {
            if (x.parent === x.parent.parent.left) {
                val y = x.parent.parent.right
                if (y.color == NodeColor.RED) {
                    x.parent.color = NodeColor.BLACK
                    y.color = NodeColor.BLACK
                    x.parent.parent.color = NodeColor.RED
                    x = x.parent.parent
                } else {
                    if (x === x.parent.right) {
                        x = x.parent
                        leftRotate(x)
                    }
                    x.parent.color = NodeColor.BLACK
                    x.parent.parent.color = NodeColor.RED
                    rightRotate(x.parent.parent)
                }
            } else {
                val y = x.parent.parent.left
                if (y.color == NodeColor.RED) {
                    x.parent.color = NodeColor.BLACK
                    y.color = NodeColor.BLACK
                    x.parent.parent.color = NodeColor.RED
                    x = x.parent.parent
                } else {
                    if (x === x.parent.left) {
                        x = x.parent
                        rightRotate(x)
                    }
                    x.parent.color = NodeColor.BLACK
                    x.parent.parent.color = NodeColor.RED
                    leftRotate(x.parent.parent)
                }
            }
        }
        root.color = NodeColor.BLACK
    }

    private fun leftRotate(x: TreeNode) {
        val y = x.right
        y.size_left += x.size_left + x.piece.length
        y.lf_left += x.lf_left + x.piece.lineFeedCnt
        x.right = y.left
        if (y.left !== sentinel) y.left.parent = x
        y.parent = x.parent
        if (x.parent === sentinel) {
            root = y
        } else if (x === x.parent.left) {
            x.parent.left = y
        } else {
            x.parent.right = y
        }
        y.left = x
        x.parent = y
    }

    private fun rightRotate(y: TreeNode) {
        val x = y.left
        y.size_left -= (x.size_left + x.piece.length)
        y.lf_left -= (x.lf_left + x.piece.lineFeedCnt)
        y.left = x.right
        if (x.right !== sentinel) x.right.parent = y
        x.parent = y.parent
        if (y.parent === sentinel) {
            root = x
        } else if (y === y.parent.right) {
            y.parent.right = x
        } else {
            y.parent.left = x
        }
        x.right = y
        y.parent = x
    }

    private fun rbDelete(z: TreeNode) {
        var y = if (z.left === sentinel || z.right === sentinel) z else z.next()!!
        var x = if (y.left !== sentinel) y.left else y.right
        x.parent = y.parent

        if (y.parent === sentinel) {
            root = x
        } else if (y === y.parent.left) {
            y.parent.left = x
        } else {
            y.parent.right = x
        }

        if (y !== z) {
            z.piece = y.piece
        }

        updateTreeMetadata(x, -y.piece.length, -y.piece.lineFeedCnt)

        if (y.color == NodeColor.BLACK) {
            fixDelete(x)
        }
    }

    private fun fixDelete(node: TreeNode) {
        var x = node
        while (x !== root && x.color == NodeColor.BLACK) {
            if (x === x.parent.left) {
                var w = x.parent.right
                if (w.color == NodeColor.RED) {
                    w.color = NodeColor.BLACK
                    x.parent.color = NodeColor.RED
                    leftRotate(x.parent)
                    w = x.parent.right
                }
                if (w.left.color == NodeColor.BLACK && w.right.color == NodeColor.BLACK) {
                    w.color = NodeColor.RED
                    x = x.parent
                } else {
                    if (w.right.color == NodeColor.BLACK) {
                        w.left.color = NodeColor.BLACK
                        w.color = NodeColor.RED
                        rightRotate(w)
                        w = x.parent.right
                    }
                    w.color = x.parent.color
                    x.parent.color = NodeColor.BLACK
                    w.right.color = NodeColor.BLACK
                    leftRotate(x.parent)
                    x = root
                }
            } else {
                var w = x.parent.left
                if (w.color == NodeColor.RED) {
                    w.color = NodeColor.BLACK
                    x.parent.color = NodeColor.RED
                    rightRotate(x.parent)
                    w = x.parent.left
                }
                if (w.right.color == NodeColor.BLACK && w.left.color == NodeColor.BLACK) {
                    w.color = NodeColor.RED
                    x = x.parent
                } else {
                    if (w.left.color == NodeColor.BLACK) {
                        w.right.color = NodeColor.BLACK
                        w.color = NodeColor.RED
                        leftRotate(w)
                        w = x.parent.left
                    }
                    w.color = x.parent.color
                    x.parent.color = NodeColor.BLACK
                    w.left.color = NodeColor.BLACK
                    rightRotate(x.parent)
                    x = root
                }
            }
        }
        x.color = NodeColor.BLACK
    }

    private fun TreeNode.next(): TreeNode? {
        if (right !== sentinel) {
            var curr = right
            while (curr.left !== sentinel) curr = curr.left
            return curr
        }
        var curr = this
        var p = parent
        while (p !== sentinel && curr === p.right) {
            curr = p
            p = p.parent
        }
        return if (p === sentinel) null else p
    }
}