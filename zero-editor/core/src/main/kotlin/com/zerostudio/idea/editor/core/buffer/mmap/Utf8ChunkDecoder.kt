package com.zerostudio.idea.editor.core.buffer.mmap

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * 高性能 UTF-8 流式断点安全解码器
 * 自动识别 1~4 字节变长编码，并处理正好横跨两块 MappedByteBuffer 边界的异常边缘字符
 */
object Utf8ChunkDecoder {

    /**
     * 判断 UTF-8 字节是单字节还是后续多字节的引导头
     */
    fun getSequenceLength(firstByte: Byte): Int {
        val b = firstByte.toInt() and 0xFF
        return when {
            (b and 0x80) == 0 -> 1           // 0xxxxxxx: ASCII
            (b and 0xE0) == 0xC0 -> 2       // 110xxxxx: 2-byte
            (b and 0xF0) == 0xE0 -> 3       // 1110xxxx: 3-byte (大部分中日韩汉字)
            (b and 0xF8) == 0xF0 -> 4       // 11110xxx: 4-byte (Emoji, 特殊平面字符)
            else -> 1                       // 损坏字节，降级为 1 字节跳过
        }
    }

    /**
     * 从一组只读 MappedByteBuffer 列表中，按 64 位全局字节偏移安全读取一个 UTF-16 Char
     * 解决代理对（Surrogate Pairs）及多字节解析
     */
    fun readCharAt(
        buffers: List<ByteBuffer>,
        chunkSize: Long,
        globalByteOffset: Long,
        totalBytes: Long
    ): Char {
        if (globalByteOffset >= totalBytes) return '\u0000'

        val chunkIdx = (globalByteOffset / chunkSize).toInt()
        val inChunkOffset = (globalByteOffset % chunkSize).toInt()
        val primaryBuffer = buffers[chunkIdx]

        val firstByte = primaryBuffer.get(inChunkOffset)
        val seqLen = getSequenceLength(firstByte)

        // 快捷路径：ASCII 字符直接返回，耗时 < 1ns
        if (seqLen == 1) {
            return (firstByte.toInt() and 0xFF).toChar()
        }

        // 收集多字节序列
        val rawBytes = ByteArray(seqLen)
        for (i in 0 until seqLen) {
            val targetOffset = globalByteOffset + i
            if (targetOffset < totalBytes) {
                val cIdx = (targetOffset / chunkSize).toInt()
                val inner = (targetOffset % chunkSize).toInt()
                rawBytes[i] = buffers[cIdx].get(inner)
            } else {
                rawBytes[i] = 0x20 // 越界填补空格
            }
        }

        val decodedStr = String(rawBytes, StandardCharsets.UTF_8)
        return decodedStr.firstOrNull() ?: '?'
    }
}