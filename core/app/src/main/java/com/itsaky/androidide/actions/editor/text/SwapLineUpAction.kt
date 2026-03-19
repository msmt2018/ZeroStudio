package com.itsaky.androidide.actions.editor.text

import android.graphics.drawable.Drawable
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorActionItem
import com.itsaky.androidide.actions.requireEditor
import com.itsaky.androidide.resources.R

/**
 * 与上一行交换 Action
 * 
 * 将光标所在行完整内容与上一行内容互换
 * @author android_zero
 */
class SwapLineUpAction : EditorActionItem {
    override val id: String = "ide.editor.lines.swapUp"
    override var label: String = "与上一行交换"
    override var visible: Boolean = true
    override var enabled: Boolean = true
    override var icon: Drawable? = R.drawable.ic_editor_lines_swap_up
    override var requiresUIThread: Boolean = true
    override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS

    override suspend fun execAction(data: ActionData): Any {
        val editor = data.requireEditor()
        val text = editor.text
        val line = editor.cursor.leftLine
        
        // 已经是第一行则无法向上交换
        if (line <= 0) return false

        val currLineText = text.getLineString(line)
        val prevLineText = text.getLineString(line - 1)

        text.beginBatchEdit()
        text.replace(line - 1, 0, line - 1, text.getColumnCount(line - 1), currLineText)
        text.replace(line, 0, line, text.getColumnCount(line), prevLineText)
        text.endBatchEdit()
        
        // 保持光标跟随行向上移动
        editor.setSelection(line - 1, editor.cursor.leftColumn)
        return true
    }
}