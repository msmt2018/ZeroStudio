package com.zerostudio.idea.editor.core.buffer.sora

import com.zerostudio.idea.editor.core.buffer.paging.VirtualPageManager
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.style.LineNumberTipTextProvider

class GlobalLineNumberTipProvider(
    private val pageManagerProvider: () -> VirtualPageManager?
) : LineNumberTipTextProvider {

    override fun getCurrentText(editor: CodeEditor): String {
        val localLine = editor.firstVisibleLine
        val pm = pageManagerProvider()
        return if (pm != null && pm.getTotalPages() > 1) {
            val slice = pm.getCurrentSlice()
            val globalLine = slice.startLine + localLine + 1L
            "Line $globalLine (Page ${pm.currentPageIndex + 1}/${pm.getTotalPages()})"
        } else {
            "Line ${localLine + 1}"
        }
    }
}