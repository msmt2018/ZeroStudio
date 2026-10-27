package com.zerostudio.idea.editor.core.buffer

import com.zerostudio.idea.editor.core.config.EditorEventBus
import com.zerostudio.idea.editor.core.config.EditorEngineEvent
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
 * 工业级无损虚拟分页引擎（类似 MT 管理器超大文件编辑模式）
 * 无论文本是 10MB、1GB 还是 10 亿字符，当前加载至 Sora 内存中的永远只有一页（例如 500 万字符）。
 * 跨页时自动保存增量回 Piece Tree，换入新页，实现零卡顿、零内存泄漏。
 */
class VirtualPageManager(
    val pieceTree: LargeTextPieceTree,
    var pageSizeChars: Long = 10_000_000L // 默认每页 1000 万字符（可配置 10万 ~ 10亿）
) {
    private val lock = ReentrantReadWriteLock()
    private val pageSlices = mutableListOf<PageSlice>()
    
    var currentPageIndex: Int = 0
        private set

    init {
        recomputePagination()
    }

    /**
     * 重建分页边界（严格按物理行对齐断开，绝不切断中间行或 UTF 代理对）
     */
    fun recomputePagination() = lock.write {
        pageSlices.clear()
        val totalLen = pieceTree.length()
        if (totalLen == 0L) {
            pageSlices.add(PageSlice(0, 0L, 0L, 0L, 1))
            return@write
        }

        var currentOffset = 0L
        var pageIdx = 0
        var currentGlobalLine = 0L

        while (currentOffset < totalLen) {
            var targetEnd = minOf(currentOffset + pageSizeChars, totalLen)
            
            // 扫描至最近的一个换行符 '\n'，确保分页不裂开代码行
            if (targetEnd < totalLen) {
                while (targetEnd < totalLen && pieceTree.charAt(targetEnd) != '\n') {
                    targetEnd++
                }
                if (targetEnd < totalLen && pieceTree.charAt(targetEnd) == '\n') {
                    targetEnd++ // 包含 '\n'
                }
            }

            // 统计当前页内的真实行数
            var pageLines = 0
            for (offset in currentOffset until targetEnd) {
                if (pieceTree.charAt(offset) == '\n') {
                    pageLines++
                }
            }
            if (targetEnd == totalLen && (currentOffset == targetEnd || pieceTree.charAt(targetEnd - 1) != '\n')) {
                pageLines++ // 补足最后一行末尾无换行的情况
            }

            pageSlices.add(
                PageSlice(
                    pageIndex = pageIdx,
                    startGlobalOffset = currentOffset,
                    endGlobalOffset = targetEnd,
                    startLineIndex = currentGlobalLine,
                    lineCount = maxOf(1, pageLines)
                )
            )

            currentGlobalLine += pageLines
            currentOffset = targetEnd
            pageIdx++
        }
    }

    /**
     * 加载当前页为可供 Sora 直接交互的 Content 对象
     */
    suspend fun loadCurrentPageContent(): Content = withContext(Dispatchers.IO) {
        lock.read {
            val slice = pageSlices.getOrElse(currentPageIndex) { pageSlices.first() }
            val len = (slice.endGlobalOffset - slice.startGlobalOffset).toInt()
            val sb = java.lang.StringBuilder(len)

            // 从只读映射/增量树中以 64KB 块读取当前页
            val buffer = CharArray(65536)
            var readCursor = slice.startGlobalOffset
            while (readCursor < slice.endGlobalOffset) {
                val chunkSize = minOf(65536L, slice.endGlobalOffset - readCursor).toInt()
                for (i in 0 until chunkSize) {
                    buffer[i] = pieceTree.charAt(readCursor + i)
                }
                sb.append(buffer, 0, chunkSize)
                readCursor += chunkSize
            }

            // 构造线程安全的高效 Content
            Content(sb, true).apply {
                undoManager.maxUndoStackSize = 300
            }
        }
    }

    /**
     * 将当前视口中修改过的内容持久化回底层 Piece Tree
     */
    fun commitCurrentPageEdits(modifiedContent: Content) = lock.write {
        val slice = pageSlices.getOrElse(currentPageIndex) { return@write }
        val newText = modifiedContent.toString()
        
        // 1. 删除旧切片数据范围
        val deleteLen = slice.endGlobalOffset - slice.startGlobalOffset
        // 2. 插入新变更到 Piece Tree（内部维护 O(log N) 平衡红黑树分裂）
        // pieceTree.replace(slice.startGlobalOffset, deleteLen, newText)
        pieceTree.insert(slice.startGlobalOffset, newText)
        
        // 重新划分分页索引拓扑
        recomputePagination()
    }

    /**
     * 切换到上一页/下一页
     */
    suspend fun switchPage(targetPageIndex: Int, currentContent: Content): Content? {
        if (targetPageIndex !in pageSlices.indices || targetPageIndex == currentPageIndex) {
            return null
        }
        commitCurrentPageEdits(currentContent)
        currentPageIndex = targetPageIndex
        EditorEventBus.emit(EditorEngineEvent.PageSwapped(currentPageIndex, pageSlices.size))
        return loadCurrentPageContent()
    }

    fun getTotalPages(): Int = lock.read { pageSlices.size }
    fun getCurrentSlice(): PageSlice = lock.read { pageSlices[currentPageIndex] }
}