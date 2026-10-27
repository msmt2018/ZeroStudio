package com.zerostudio.idea.editor.core.buffer

import com.zerostudio.idea.editor.core.config.EditorEngineEvent
import com.zerostudio.idea.editor.core.config.EditorEventBus
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 分页元数据描述符
 */
data class PageSlice(
    val pageIndex: Int,
    val startGlobalOffset: Long,
    val endGlobalOffset: Long,
    val startLineIndex: Long,
    val lineCount: Int
)

/**
 * 无损虚拟分页引擎（类似 MT 管理器超大文件编辑模式）
 * 无论文本是 10MB、1GB 还是 10 亿字符，当前加载至 Sora 内存中的永远只有一页（例如 500 万字符）。
 * 跨页时自动保存增量回 Piece Tree，换入新页，实现零卡顿、零内存泄漏。
 */
class VirtualPageManager(
    val pieceTree: LargePieceTree,
    var pageSizeChars: Long = 500_000L // 视口物理分页大小（约 50万字符，内存占用 < 2MB）
) {
    private val lock = ReentrantReadWriteLock()
    private val slices = mutableListOf<PageSlice>()
    var currentPageIndex: Int = 0
        private set

    init {
        recomputePagination()
    }

    /**
     * 重建分页边界（严格按物理行对齐断开，绝不切断中间行或 UTF 代理对）
     */
    fun recomputePagination() = lock.write {
        slices.clear()
        val totalLength = pieceTree.length()
        if (totalLength == 0L) {
            slices.add(PageSlice(0, 0L, 0L, 0L, 1))
            return@write
        }

        var cursor = 0L
        var pageIdx = 0
        var globalLine = 0L

        while (cursor < totalLength) {
            var targetEnd = minOf(cursor + pageSizeChars, totalLength)
            // 确保切片在真实行尾对齐，杜绝截断代码行或 Unicode 代理对
            if (targetEnd < totalLength) {
                while (targetEnd < totalLength && pieceTree.charAt(targetEnd) != '\n') {
                    targetEnd++
                }
                if (targetEnd < totalLength && pieceTree.charAt(targetEnd) == '\n') {
                    targetEnd++
                }
            }

            var localLines = 0
            for (i in cursor until targetEnd) {
                if (pieceTree.charAt(i) == '\n') localLines++
            }
            if (targetEnd == totalLength && (cursor == targetEnd || pieceTree.charAt(targetEnd - 1) != '\n')) {
                localLines++
            }

            slices.add(
                PageSlice(
                    pageIndex = pageIdx++,
                    startGlobalOffset = cursor,
                    endGlobalOffset = targetEnd,
                    startLineIndex = globalLine,
                    lineCount = maxOf(1, localLines)
                )
            )
            globalLine += localLines
            cursor = targetEnd
        }
    }

    /**
     * 加载当前页为可供 Sora 直接交互的 Content 对象
     */
    suspend fun loadCurrentPageContent(): Content = withContext(Dispatchers.IO) {
        lock.read {
            val slice = slices.getOrElse(currentPageIndex) { slices.first() }
            val len = (slice.endGlobalOffset - slice.startGlobalOffset).toInt()
            val sb = StringBuilder(len)

            val bufferChunkSize = 65536
            val chunk = CharArray(bufferChunkSize)
            var readOffset = slice.startGlobalOffset
            while (readOffset < slice.endGlobalOffset) {
                val toRead = minOf(bufferChunkSize.toLong(), slice.endGlobalOffset - readOffset).toInt()
                for (i in 0 until toRead) {
                    chunk[i] = pieceTree.charAt(readOffset + i)
                }
                sb.append(chunk, 0, toRead)
                readOffset += toRead
            }

            Content(sb, true).apply {
                undoManager.maxUndoStackSize = 200
            }
        }
    }
    /**
     * 将当前视口中修改过的内容持久化回底层 Piece Tree
     */
    fun commitCurrentPageEdits(content: Content) = lock.write {
        val slice = slices.getOrElse(currentPageIndex) { return@write }
        val newText = content.toString()
        pieceTree.insert(slice.startGlobalOffset, newText)
        recomputePagination()
    }

    /**
     * 切换到上一页/下一页
     */
    suspend fun switchPage(targetPage: Int, currentContent: Content): Content? {
        if (targetPage !in slices.indices || targetPage == currentPageIndex) return null
        commitCurrentPageEdits(currentContent)
        currentPageIndex = targetPage
        EditorEventBus.emit(EditorEngineEvent.PageSwapped(currentPageIndex, slices.size))
        return loadCurrentPageContent()
    }

    fun getTotalPages(): Int = lock.read { slices.size }
    fun getCurrentSlice(): PageSlice = lock.read { slices[currentPageIndex] }
}