package com.zerostudio.idea.editor.core.buffer

enum class BufferSource { ORIGINAL, ADD }

/**
 * 64 位大型文本 Piece Tree 节点
 * 内部节点存储子树字符总权重与行分隔符计数，用于 O(log N) 快速二分定位
 */
internal class PieceNode(
    var source: BufferSource,
    var start: Long,
    var length: Long,
    var lineBreaks: Long,
    var parent: PieceNode? = null,
    var left: PieceNode? = null,
    var right: PieceNode? = null,
    var color: Boolean = RED
) {
    var subtreeLength: Long = length
    var subtreeLineBreaks: Long = lineBreaks

    fun updateSubtreeWeights(nil: PieceNode) {
        subtreeLength = length +
                (if (left !== nil && left != null) left!!.subtreeLength else 0L) +
                (if (right !== nil && right != null) right!!.subtreeLength else 0L)
        subtreeLineBreaks = lineBreaks +
                (if (left !== nil && left != null) left!!.subtreeLineBreaks else 0L) +
                (if (right !== nil && right != null) right!!.subtreeLineBreaks else 0L)
    }

    companion object {
        const val RED = true
        const val BLACK = false
    }
}

data class StorageStats(
    val length: Long,
    val lineCount: Long,
    val pieceCount: Int,
    val memoryMappedBytes: Long
)