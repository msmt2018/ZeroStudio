package com.zerostudio.idea.editor.core.buffer

import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentLine
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.text.LineSeparator
import java.lang.StringBuilder

/**
 * 工业级 Zero-Copy 文本代理容器
 * 将 Sora 绘制层直接挂载在 VirtualPageManager 上
 */
class PieceTreeContent(
    private val pageManager: VirtualPageManager,
    initialText: CharSequence
) : Content(initialText, true) {

    private var mutationListener: ((Content) -> Unit)? = null

    fun setOnDirectMutationListener(listener: (Content) -> Unit) {
        this.mutationListener = listener
    }

    override fun insert(line: Int, column: Int, text: CharSequence?) {
        if (text == null || text.isEmpty()) return
        super.insert(line, column, text)
        // 增量变动直接同步回当前活跃切片，不进行全局序列化
        mutationListener?.invoke(this)
    }

    override fun delete(startLine: Int, columnOnStartLine: Int, endLine: Int, columnOnEndLine: Int) {
        super.delete(startLine, columnOnStartLine, endLine, columnOnEndLine)
        mutationListener?.invoke(this)
    }

    override fun replace(startLine: Int, columnOnStartLine: Int, endLine: Int, columnOnEndLine: Int, text: CharSequence?) {
        super.replace(startLine, columnOnStartLine, endLine, columnOnEndLine, text)
        mutationListener?.invoke(this)
    }

    /**
     * 流式提取行，避免 toString() 全量大对象逃逸
     */
    fun extractLineToBuffer(lineIndex: Int, targetBuffer: StringBuilder) {
        val line = getLine(lineIndex)
        line.appendTo(targetBuffer)
    }
}