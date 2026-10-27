package com.zerostudio.idea.editor.core.buffer

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class LargePieceTree(
    private val file: File? = null,
    private val charset: Charset = StandardCharsets.UTF_8
) {
    private val rwLock = ReentrantReadWriteLock()
    private val sentinel: PieceNode = PieceNode(
        source = BufferSource.ORIGINAL,
        start = 0L,
        length = 0L,
        lineBreaks = 0L,
        color = PieceNode.BLACK
    ).apply {
        parent = this
        left = this
        right = this
    }

    private var root: PieceNode = sentinel
    private var mappedBuffers: List<MappedByteBuffer> = emptyList()
    private var fileChannel: FileChannel? = null
    private var randomAccessFile: RandomAccessFile? = null

    // 追加修改缓冲区
    private var addBuffer = StringBuilder(1024 * 128)
    private var totalLength: Long = 0L
    private var totalLines: Long = 1L
    private var pieceCount: Int = 0

    init {
        rwLock.write {
            if (file != null && file.exists() && file.length() > 0L) {
                loadFileMapped(file)
            }
        }
    }

    private fun loadFileMapped(targetFile: File) {
        val fileLength = targetFile.length()
        randomAccessFile = RandomAccessFile(targetFile, "r")
        val channel = randomAccessFile!!.channel
        this.fileChannel = channel

        val buffers = mutableListOf<MappedByteBuffer>()
        val chunkSize = 1L shl 30 // 1GB 切片，规避 MappedByteBuffer 2GB 单段上限
        var offset = 0L
        while (offset < fileLength) {
            val size = minOf(chunkSize, fileLength - offset)
            buffers.add(channel.map(FileChannel.MapMode.READ_ONLY, offset, size))
            offset += size
        }
        mappedBuffers = buffers

        // 快速统计换行符，构建初始节点
        var initialLineBreaks = 0L
        for (buf in mappedBuffers) {
            val duplicate = buf.duplicate()
            while (duplicate.hasRemaining()) {
                if (duplicate.get() == '\n'.code.toByte()) {
                    initialLineBreaks++
                }
            }
        }

        totalLength = fileLength
        totalLines = initialLineBreaks + 1L

        root = PieceNode(
            source = BufferSource.ORIGINAL,
            start = 0L,
            length = fileLength,
            lineBreaks = initialLineBreaks,
            parent = sentinel,
            left = sentinel,
            right = sentinel,
            color = PieceNode.BLACK
        ).apply {
            updateSubtreeWeights(sentinel)
        }
        pieceCount = 1
    }

    fun charAt(offset: Long): Char = rwLock.read {
        require(offset in 0 until totalLength) { "Offset $offset out of bounds [0, $totalLength)" }
        val (node, nodeOffset) = findNodeAt(offset)
        val sourceOffset = node.start + nodeOffset
        return when (node.source) {
            BufferSource.ORIGINAL -> readMappedByteAsChar(sourceOffset)
            BufferSource.ADD -> addBuffer[sourceOffset.toInt()]
        }
    }

    private fun readMappedByteAsChar(globalOffset: Long): Char {
        val chunkSize = 1L shl 30
        val bufIdx = (globalOffset / chunkSize).toInt()
        val inOffset = (globalOffset % chunkSize).toInt()
        val byteVal = mappedBuffers[bufIdx].get(inOffset).toInt()
        return (byteVal and 0xFF).toChar()
    }

    private fun findNodeAt(offset: Long): Pair<PieceNode, Long> {
        var current = root
        var remaining = offset
        while (current !== sentinel) {
            val leftLen = if (current.left !== sentinel) current.left!!.subtreeLength else 0L
            if (remaining < leftLen) {
                current = current.left!!
            } else if (remaining >= leftLen + current.length) {
                remaining -= (leftLen + current.length)
                current = current.right!!
            } else {
                return Pair(current, remaining - leftLen)
            }
        }
        throw IllegalStateException("Node not found at offset $offset")
    }

    fun insert(offset: Long, text: String) = rwLock.write {
        if (text.isEmpty()) return@write
        val addStart = addBuffer.length.toLong()
        addBuffer.append(text)
        val breaks = text.count { it == '\n' }.toLong()

        if (root === sentinel) {
            root = PieceNode(
                source = BufferSource.ADD,
                start = addStart,
                length = text.length.toLong(),
                lineBreaks = breaks,
                parent = sentinel,
                left = sentinel,
                right = sentinel,
                color = PieceNode.BLACK
            ).apply { updateSubtreeWeights(sentinel) }
            totalLength = text.length.toLong()
            totalLines = breaks + 1L
            pieceCount = 1
            return@write
        }

        val (targetNode, atOffset) = findNodeAt(offset)
        splitAndInsert(targetNode, atOffset, addStart, text.length.toLong(), breaks)
        totalLength += text.length.toLong()
        totalLines += breaks
        pieceCount++
    }

    private fun splitAndInsert(target: PieceNode, atOffset: Long, addStart: Long, addLen: Long, breaks: Long) {
        val newNode = PieceNode(
            source = BufferSource.ADD,
            start = addStart,
            length = addLen,
            lineBreaks = breaks,
            parent = sentinel,
            left = sentinel,
            right = sentinel,
            color = PieceNode.RED
        )

        if (atOffset == 0L) {
            insertNodeBefore(target, newNode)
        } else if (atOffset == target.length) {
            insertNodeAfter(target, newNode)
        } else {
            // 三段式分裂
            val rightSplitLen = target.length - atOffset
            val rightSplitBreaks = 0L // 可根据 lineStarts 优化
            val rightNode = PieceNode(
                source = target.source,
                start = target.start + atOffset,
                length = rightSplitLen,
                lineBreaks = rightSplitBreaks,
                parent = sentinel,
                left = sentinel,
                right = sentinel,
                color = PieceNode.RED
            )
            target.length = atOffset
            target.lineBreaks = target.lineBreaks - rightSplitBreaks
            target.updateSubtreeWeights(sentinel)

            insertNodeAfter(target, newNode)
            insertNodeAfter(newNode, rightNode)
            pieceCount++
        }
    }

    private fun insertNodeAfter(pivot: PieceNode, node: PieceNode) {
        if (pivot.right === sentinel) {
            pivot.right = node
            node.parent = pivot
        } else {
            var curr = pivot.right!!
            while (curr.left !== sentinel) {
                curr = curr.left!!
            }
            curr.left = node
            node.parent = curr
        }
        fixAfterInsert(node)
    }

    private fun insertNodeBefore(pivot: PieceNode, node: PieceNode) {
        if (pivot.left === sentinel) {
            pivot.left = node
            node.parent = pivot
        } else {
            var curr = pivot.left!!
            while (curr.right !== sentinel) {
                curr = curr.right!!
            }
            curr.right = node
            node.parent = curr
        }
        fixAfterInsert(node)
    }

    private fun fixAfterInsert(inserted: PieceNode) {
        var x = inserted
        x.updateSubtreeWeights(sentinel)
        var p = x.parent
        while (p !== sentinel && p != null) {
            p.updateSubtreeWeights(sentinel)
            p = p.parent
        }

        while (x !== root && x.parent?.color == PieceNode.RED) {
            val parent = x.parent!!
            val gparent = parent.parent ?: break
            if (parent === gparent.left) {
                val uncle = gparent.right
                if (uncle?.color == PieceNode.RED) {
                    parent.color = PieceNode.BLACK
                    uncle.color = PieceNode.BLACK
                    gparent.color = PieceNode.RED
                    x = gparent
                } else {
                    if (x === parent.right) {
                        x = parent
                        rotateLeft(x)
                    }
                    x.parent?.color = PieceNode.BLACK
                    x.parent?.parent?.color = PieceNode.RED
                    if (x.parent?.parent != null) {
                        rotateRight(x.parent!!.parent!!)
                    }
                }
            } else {
                val uncle = gparent.left
                if (uncle?.color == PieceNode.RED) {
                    parent.color = PieceNode.BLACK
                    uncle.color = PieceNode.BLACK
                    gparent.color = PieceNode.RED
                    x = gparent
                } else {
                    if (x === parent.left) {
                        x = parent
                        rotateRight(x)
                    }
                    x.parent?.color = PieceNode.BLACK
                    x.parent?.parent?.color = PieceNode.RED
                    if (x.parent?.parent != null) {
                        rotateLeft(x.parent!!.parent!!)
                    }
                }
            }
        }
        root.color = PieceNode.BLACK
    }

    private fun rotateLeft(x: PieceNode) {
        val y = x.right ?: return
        if (y === sentinel) return
        x.right = y.left
        if (y.left !== sentinel) y.left?.parent = x
        y.parent = x.parent
        if (x.parent === sentinel || x.parent == null) {
            root = y
        } else if (x === x.parent?.left) {
            x.parent?.left = y
        } else {
            x.parent?.right = y
        }
        y.left = x
        x.parent = y

        x.updateSubtreeWeights(sentinel)
        y.updateSubtreeWeights(sentinel)
    }

    private fun rotateRight(y: PieceNode) {
        val x = y.left ?: return
        if (x === sentinel) return
        y.left = x.right
        if (x.right !== sentinel) x.right?.parent = y
        x.parent = y.parent
        if (y.parent === sentinel || y.parent == null) {
            root = x
        } else if (y === y.parent?.right) {
            y.parent?.right = x
        } else {
            y.parent?.left = x
        }
        x.right = y
        y.parent = x

        y.updateSubtreeWeights(sentinel)
        x.updateSubtreeWeights(sentinel)
    }

    fun length(): Long = rwLock.read { totalLength }
    fun lineCount(): Long = rwLock.read { totalLines }

    fun getStats(): StorageStats = rwLock.read {
        StorageStats(
            length = totalLength,
            lineCount = totalLines,
            pieceCount = pieceCount,
            memoryMappedBytes = mappedBuffers.sumOf { it.capacity().toLong() }
        )
    }

    fun close() {
        rwLock.write {
            mappedBuffers = emptyList()
            runCatching { fileChannel?.close() }
            runCatching { randomAccessFile?.close() }
            fileChannel = null
            randomAccessFile = null
        }
    }
}