package com.zerostudio.idea.editor.core.buffer.piecetree

import java.util.Arrays

class PieceTreeSnapshot internal constructor(
    private val chunks: Array<CharSequence>,
    private val chunkOffsets: LongArray, // 各 chunk 在全局中的起始绝对偏移
    private val chunkLengths: LongArray,
    val totalLength: Long,
    val totalLines: Long,
    val versionId: Int,
    val eol: String
) : CharSequence {

    override val length: Int
        get() = totalLength.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    val lengthLong: Long get() = totalLength

    override fun get(index: Int): Char {
        return getLong(index.toLong())
    }

    fun getLong(offset: Long): Char {
        require(offset in 0 until totalLength) { "Offset out of bounds: $offset, total: $totalLength" }
        val idx = findChunkIndex(offset)
        val inChunkOffset = (offset - chunkOffsets[idx]).toInt()
        return chunks[idx][inChunkOffset]
    }

    private fun findChunkIndex(offset: Long): Int {
        var low = 0
        var high = chunkOffsets.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val start = chunkOffsets[mid]
            val end = start + chunkLengths[mid]
            when {
                offset < start -> high = mid - 1
                offset >= end -> low = mid + 1
                else -> return mid
            }
        }
        return (chunkOffsets.size - 1).coerceAtLeast(0)
    }

    /**
     * 针对语法解析器（如 Tree-Sitter / LSP）的近似分块流式读取接口
     * 返回以 offset 开始的连续字符块，长度最大约 64KB，读取至末尾返回空串 ""
     */
    fun getNearestChunk(offset: Long, maxChunkSize: Int = 65536): String {
        if (offset >= totalLength) return ""
        val idx = findChunkIndex(offset)
        val inChunkOffset = (offset - chunkOffsets[idx]).toInt()
        val chunkLen = chunkLengths[idx].toInt()
        val available = minOf(chunkLen - inChunkOffset, maxChunkSize)

        return chunks[idx].subSequence(inChunkOffset, inChunkOffset + available).toString()
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        val len = endIndex - startIndex
        val sb = StringBuilder(len)
        var cur = startIndex.toLong()
        while (cur < endIndex.toLong()) {
            val chunk = getNearestChunk(cur, (endIndex.toLong() - cur).coerceAtMost(65536L).toInt())
            if (chunk.isEmpty()) break
            sb.append(chunk)
            cur += chunk.length
        }
        return sb
    }

    override fun toString(): String {
        return subSequence(0, length).toString()
    }
}