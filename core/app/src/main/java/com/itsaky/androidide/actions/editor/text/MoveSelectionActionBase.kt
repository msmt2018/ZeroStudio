// by android_zero
package com.itsaky.androidide.actions.editor.text

import android.graphics.drawable.Drawable
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorActionItem
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * The base execution class for moving selected content
 * @author android_zero
 */
abstract class MoveSelectionActionBase : EditorActionItem {
    override var label: String = ""
    override var visible: Boolean = true
    override var enabled: Boolean = false
    override var icon: Drawable? = null
    override var requiresUIThread: Boolean = true
    override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS

    private var currentEditor: CodeEditor? = null

    override fun prepare(data: ActionData) {
        super.prepare(data)
        val editor = data.get(CodeEditor::class.java)
        if (editor != null) {
            currentEditor = editor
            enabled = editor.cursor.isSelected
        } else {
            enabled = false
        }
    }

    override suspend fun execAction(data: ActionData): Any {
        val editor = currentEditor ?: return false
        if (!editor.cursor.isSelected) return false

        val text = editor.text
        val cursor = editor.cursor
        val start = cursor.left().fromThis()
        val end = cursor.right().fromThis()
        
        val selectedText = text.subSequence(start.index, end.index).toString()
        val selectionLength = end.index - start.index

        val targetInsertIndex = calculateTargetInsertIndex(editor, start, end)
        if (targetInsertIndex == -1) return false

        text.beginBatchEdit()
        text.delete(start.index, end.index)

        val finalInsertIndex = if (targetInsertIndex > start.index) {
            targetInsertIndex - selectionLength
        } else {
            targetInsertIndex
        }

        val insertPos = text.indexer.getCharPosition(finalInsertIndex)
        text.insert(insertPos.line, insertPos.column, selectedText)
        text.endBatchEdit()

        val finalStart = text.indexer.getCharPosition(finalInsertIndex)
        val finalEnd = text.indexer.getCharPosition(finalInsertIndex + selectionLength)
        editor.setSelectionRegion(finalStart.line, finalStart.column, finalEnd.line, finalEnd.column)
        editor.ensurePositionVisible(finalStart.line, finalStart.column)
        
        return true
    }

    /**
     * 计算选中的文本将要被移动到的目标绝对索引（以删除前的内容状态作为参照）
     *
     * @param editor 目标 CodeEditor 实例
     * @param start  选中区域的起始点
     * @param end    选中区域的结束点
     * @return 新的目标绝对索引，如果触及边界不可移动则返回 -1
     */
    abstract fun calculateTargetInsertIndex(editor: CodeEditor, start: CharPosition, end: CharPosition): Int
}