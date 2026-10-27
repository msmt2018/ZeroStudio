package com.zerostudio.idea.editor.core.buffer.sora

import android.graphics.Canvas
import android.graphics.Paint
import com.zerostudio.idea.editor.core.buffer.paging.VirtualPageManager
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.max

/**
 * 工业级动态行号测量与绘制扩展器
 * 确保当大文件分页至百万/千万行时，行号栏（Gutter）绝不发生数字截断、文字重叠或抖动
 */
class GlobalGutterRenderer(
    private val editor: CodeEditor,
    private val pageManagerProvider: () -> VirtualPageManager?
) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.MONOSPACE
        textSize = editor.textSizePx * 0.85f
    }

    /**
     * 计算包含视口偏移的绝对行号宽度
     */
    fun computeGutterWidth(): Float {
        val pm = pageManagerProvider()
        val totalGlobalLines = pm?.pieceTree?.getLineCount() ?: editor.lineCount.toLong()
        val digits = totalGlobalLines.toString().length
        val charWidth = textPaint.measureText("9")
        // 内边距左右各留 12dp，确保清晰分界
        return max(charWidth * digits + (editor.dpUnit * 24), editor.dpUnit * 36)
    }

    /**
     * 获取指定视口局部行在整个大文件中的绝对格式化文本
     */
    fun getFormattedGlobalLine(localRowIndex: Int): String {
        val pm = pageManagerProvider() ?: return (localRowIndex + 1).toString()
        val currentSlice = pm.getCurrentSlice()
        val actualGlobalLine = currentSlice.startLine + localRowIndex + 1L
        return actualGlobalLine.toString()
    }

    /**
     * 在行号区域手动绘制右对齐行号
     */
    fun drawLineNumber(
        canvas: Canvas,
        localLineIndex: Int,
        rowTop: Float,
        rowBottom: Float,
        gutterWidth: Float,
        color: Int
    ) {
        textPaint.color = color
        textPaint.textSize = editor.textSizePx * 0.85f

        val text = getFormattedGlobalLine(localLineIndex)
        val textWidth = textPaint.measureText(text)
        val x = gutterWidth - textWidth - (editor.dpUnit * 8)
        val baseline = (rowTop + rowBottom) / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f)

        canvas.drawText(text, x, baseline, textPaint)
    }
}