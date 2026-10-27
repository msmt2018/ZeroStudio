package com.zerostudio.idea.editor.core.buffer.mmap

import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MappedChunkBuffer(val file: File) : CharSequence {
    private val chunks: List<MappedByteBuffer>
    override val length: Int
    val lengthLong: Long = file.length()

    init {
        require(file.exists() && file.isFile) { "File must exist: ${file.absolutePath}" }
        length = lengthLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val list = mutableListOf<MappedByteBuffer>()
        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            val segmentSize = 1L shl 30 // 1GB 切片
            var offset = 0L
            while (offset < lengthLong) {
                val size = minOf(segmentSize, lengthLong - offset)
                list.add(channel.map(FileChannel.MapMode.READ_ONLY, offset, size))
                offset += size
            }
        }
        chunks = list
    }

    override fun get(index: Int): Char {
        return getLong(index.toLong())
    }

    fun getLong(offset: Long): Char {
        val segmentSize = 1L shl 30
        val chunkIdx = (offset / segmentSize).toInt()
        val inOffset = (offset % segmentSize).toInt()
        val byteVal = chunks[chunkIdx].get(inOffset).toInt()
        return (byteVal and 0xFF).toChar()
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        val sb = StringBuilder(endIndex - startIndex)
        for (i in startIndex until endIndex) {
            sb.append(get(i))
        }
        return sb
    }
}