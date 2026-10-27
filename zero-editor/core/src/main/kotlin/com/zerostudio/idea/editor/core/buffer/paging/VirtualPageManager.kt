package com.zerostudio.idea.editor.core.buffer.paging

import com.zerostudio.idea.editor.core.buffer.piecetree.PieceTreeCore
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class PageSlice(
    val pageIndex: Int,
    val startOffset: Long,
    val endOffset: Long,
    val startLine: Long,
    val lineCount: Int
)

class VirtualPageManager(
    val pieceTree: PieceTreeCore,
    val pageSizeChars: Long = 300_000L // 30万字分片，单页堆占用 < 1.5MB
) {
    private val lock = ReentrantReadWriteLock()
    private val slices = mutableListOf<PageSlice>()
    var currentPageIndex: Int = 0
        private set

    init {
        recomputePagination()
    }

    fun recomputePagination() = lock.write {
        slices.clear()
        val totalLength = pieceTree.getLength()
        if (totalLength == 0L) {
            slices.add(PageSlice(0, 0L, 0L, 0L, 1))
            return@write
        }

        var offset = 0L
        var pageIdx = 0
        var globalLine = 0L

        while (offset < totalLength) {
            var targetEnd = minOf(offset + pageSizeChars, totalLength)
            // 物理行边界对齐
            if (targetEnd < totalLength) {
                while (targetEnd < totalLength && pieceTree.charAt(targetEnd) != '\n') {
                    targetEnd++
                }
                if (targetEnd < totalLength && pieceTree.charAt(targetEnd) == '\n') {
                    targetEnd++
                }
            }

            var lines = 0
            for (i in offset until targetEnd) {
                if (pieceTree.charAt(i) == '\n') lines++
            }
            if (targetEnd == totalLength && (offset == targetEnd || pieceTree.charAt(targetEnd - 1) != '\n')) {
                lines++
            }

            slices.add(
                PageSlice(
                    pageIndex = pageIdx++,
                    startOffset = offset,
                    endOffset = targetEnd,
                    startLine = globalLine,
                    lineCount = maxOf(1, lines)
                )
            )
            globalLine += lines
            offset = targetEnd
        }
    }

    suspend fun loadCurrentPageContent(): Content = withContext(Dispatchers.IO) {
        lock.read {
            val slice = slices.getOrElse(currentPageIndex) { slices.first() }
            val len = (slice.endOffset - slice.startOffset).toInt()
            val sb = java.lang.StringBuilder(len)

            val chunk = CharArray(32768)
            var cur = slice.startOffset
            while (cur < slice.endOffset) {
                val toRead = minOf(chunk.size.toLong(), slice.endOffset - cur).toInt()
                for (i in 0 until toRead) {
                    chunk[i] = pieceTree.charAt(cur + i)
                }
                sb.append(chunk, 0, toRead)
                cur += toRead
            }

            Content(sb, true).apply {
                undoManager.maxUndoStackSize = 100
            }
        }
    }

    fun commitCurrentPage(currentContent: Content) = lock.write {
        val slice = slices.getOrElse(currentPageIndex) { return@write }
        val len = slice.endOffset - slice.startOffset
        val newText = currentContent.toString()

        pieceTree.delete(slice.startOffset, len)
        pieceTree.insert(slice.startOffset, newText)
        recomputePagination()
    }

    suspend fun switchPage(targetPage: Int, content: Content): Content? {
        if (targetPage !in slices.indices || targetPage == currentPageIndex) return null
        commitCurrentPage(content)
        currentPageIndex = targetPage
        return loadCurrentPageContent()
    }

    fun getTotalPages(): Int = lock.read { slices.size }
    fun getCurrentSlice(): PageSlice = lock.read { slices[currentPageIndex] }
}