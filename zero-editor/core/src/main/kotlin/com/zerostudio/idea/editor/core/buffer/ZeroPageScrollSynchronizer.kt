package com.zerostudio.idea.editor.core.buffer

import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 滚动驱动的虚拟分页平滑换页调度器
 */
class ZeroPageScrollSynchronizer(
    private val editor: ZeroCodeEditorView,
    private val pageManager: VirtualPageManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val isSwapping = AtomicBoolean(false)

    /**
     * 绑定编辑器滚动侦测事件
     */
    fun attach() {
        // 挂载至每帧渲染前的滚动检测
        editor.post {
            checkAndTriggerPageSwap()
        }
    }

    /**
     * 核心阈值判定与无损换页逻辑
     */
    fun checkAndTriggerPageSwap() {
        if (isSwapping.get() || pageManager.getTotalPages() <= 1) return

        val firstVisibleLine = editor.firstVisibleLine
        val lastVisibleLine = firstVisibleLine + (editor.height / editor.rowHeight)
        val currentSlice = pageManager.getCurrentSlice()
        val totalPageLines = currentSlice.lineCount

        // 设定边界安全缓冲区（前 10% 触发向前换页，后 90% 触发向后换页）
        val topThreshold = (totalPageLines * 0.10f).toInt().coerceAtLeast(5)
        val bottomThreshold = (totalPageLines * 0.90f).toInt().coerceAtMost(totalPageLines - 5)

        val targetPageIndex = when {
            firstVisibleLine <= topThreshold && pageManager.currentPageIndex > 0 -> {
                pageManager.currentPageIndex - 1
            }
            lastVisibleLine >= bottomThreshold && pageManager.currentPageIndex < pageManager.getTotalPages() - 1 -> {
                pageManager.currentPageIndex + 1
            }
            else -> -1
        }

        if (targetPageIndex != -1) {
            executeSeamlessSwap(targetPageIndex)
        }
    }

    private fun executeSeamlessSwap(targetPage: Int) {
        if (!isSwapping.compareAndSet(false, true)) return

        scope.launch {
            val oldOffsetY = editor.offsetY
            val oldPage = pageManager.currentPageIndex

            // 后台完成当前页增量提交并加载新页 Content
            val newContent = withContext(Dispatchers.IO) {
                pageManager.switchPage(targetPage, editor.text)
            }

            if (newContent != null) {
                // 计算行偏移补偿，保证画面不跳动
                editor.setText(newContent)

                // 物理位移平滑回正
                if (targetPage > oldPage) {
                    // 向下滚动进入新页：视口平移至新页上方
                    editor.scrollTo(editor.offsetX, (editor.rowHeight * 6).coerceAtMost(editor.offsetY))
                } else {
                    // 向上滚动进入新页：视口平移至新页下方
                    val targetLine = (pageManager.getCurrentSlice().lineCount - 10).coerceAtLeast(0)
                    editor.scrollTo(editor.offsetX, targetLine * editor.rowHeight)
                }
            }
            isSwapping.set(false)
        }
    }

    fun detach() {
        scope.cancel()
    }
}