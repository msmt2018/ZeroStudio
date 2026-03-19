package com.itsaky.androidide.actions.editor.text

import android.graphics.drawable.Drawable
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorActionItem
import com.itsaky.androidide.actions.requireEditor
import com.itsaky.androidide.resources.R

/**
 * Swap Action with the next line
 * 
 * 将光标所在行完整内容与下一行内容互换
 * @author android_zero
 */
class SwapLineDownAction : EditorActionItem {
    override val id: String = "ide.editor.lines.swapDown"
    override var label: String = "Swap with the next line"
    override var visible: Boolean = true
    override var enabled: Boolean = true
    override var icon: Drawable? = R.drawable.ic_editor_lines_swap_down
    override var requiresUIThread: Boolean = true
    override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS

    override suspend fun execAction(data: ActionData): Any {
        val editor = data.requireEditor()
        val text = editor.text
        val line = editor.cursor.leftLine
        
        // 已经是最后一行则无法向下交换
        if (line >= text.lineCount - 1) return false

        val currLineText = text.getLineString(line)
        val nextLineText = text.getLineString(line + 1)

        text.beginBatchEdit()
        text.replace(line, 0, line, text.getColumnCount(line), nextLineText)
        text.replace(line + 1, 0, line + 1, text.getColumnCount(line + 1), currLineText)
        text.endBatchEdit()
        
        // 保持光标跟随行向下移动
        editor.setSelection(line + 1, editor.cursor.leftColumn)
        return true
    }
}