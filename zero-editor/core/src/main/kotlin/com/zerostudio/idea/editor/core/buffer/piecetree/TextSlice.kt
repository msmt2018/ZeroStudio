package com.zerostudio.idea.editor.core.buffer.piecetree

import com.zerostudio.idea.editor.core.buffer.model.IntList
import com.zerostudio.idea.editor.core.buffer.model.Range

/**
 * 文本切片片段描述符：指向底层缓冲区的绝对区间，不持有实体字符拷贝
 */
internal class SliceSegment(
    val bufferIndex: Int,
    val startOffset: Long,
    val length: Long,
    val lineFeedCnt: Long
)

/**
 * 工业级双模切片容器：
 * - 小于 16KB：内联 String 存储，轻量且便于跨线程传递；
 * - 大于 16KB：以 SliceSegment 链表存储，撤销/重做千万字符耗费内存 < 1KB。
 */
class TextSlice internal constructor(
    internal val ownerTree: PieceTreeCore?,
    internal val inlineText: String?,
    internal val segments: List<SliceSegment>,
    val length: Long,
    val lineFeedCnt: Long,
    val eol: String?
) {
    val isInlineText: Boolean get() = inlineText != null

    /**
     * 按需物化为 String（仅在明确需要导出或持久化时调用）
     */
    fun materializeText(tree: PieceTreeCore): String {
        inlineText?.let { return it }
        val sb = StringBuilder(length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        for (seg in segments) {
            for (i in 0 until seg.length) {
                sb.append(tree.charAt(seg.startOffset + i))
            }
        }
        return sb.toString()
    }

    companion object {
        const val INLINE_THRESHOLD = 16 * 1024 // 16KB 临界阈值

        fun ofText(text: String, eol: String = "\n"): TextSlice {
            val breaks = text.count { it == '\n' }.toLong()
            return TextSlice(
                ownerTree = null,
                inlineText = text,
                segments = emptyList(),
                length = text.length.toLong(),
                lineFeedCnt = breaks,
                eol = eol
            )
        }
    }
}

/**
 * 切片编辑事务模型
 */
data class SliceEditOperation(
    val range: Range,
    val slice: TextSlice? // null 代表删除操作
)

data class SliceEditResult(
    val reverseEdits: List<SliceEditOperation>,
    val deltaLength: Long
)