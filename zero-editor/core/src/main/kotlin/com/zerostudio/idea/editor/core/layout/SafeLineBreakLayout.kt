package com.zerostudio.idea.editor.core.layout

import com.zerostudio.idea.editor.core.layout.ZeroLongLineChunker
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentLine
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.layout.LineBreakLayout

class SafeLineBreakLayout(editor: CodeEditor, text: Content) : LineBreakLayout(editor, text) {

    /**
     * 拦截行测量逻辑，针对超长行进行步进平滑测量
     */
    fun measureLineSafely(line: ContentLine): Float {
        val len = line.length()
        if (!ZeroLongLineChunker.isDangerousLongLine(len)) {
            return editor.textPaint.measureText(line, 0, len)
        }

        // 分块累加测量，防止向 Skia 传递巨型 buffer
        var totalWidth = 0f
        val slices = ZeroLongLineChunker.chunkLongLine(0, line)
        for (slice in slices) {
            totalWidth += editor.textPaint.measureText(line, slice.startColumn, slice.endColumn)
        }
        return totalWidth
    }
}