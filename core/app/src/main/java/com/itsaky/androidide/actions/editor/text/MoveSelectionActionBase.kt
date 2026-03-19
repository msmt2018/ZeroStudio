package com.itsaky.androidide.actions.editor

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorActionItem
import com.itsaky.androidide.actions.requireContext
import com.itsaky.androidide.actions.requireEditor
import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * The base execution class for moving selected content

*
* Design guidelines:

* 1. Unified management of action resource bindings (titles and icons are loaded via R.class).

* 2. Handle state checks in the prepare() phase: hide and disable text when it is not selected.

* 3. Abstract the movement logic as: calculate the target insertion point -> remove the original selected text -> insert text at the adjusted target position -> restore the selected state.

*
 * @author android_zero
 */
abstract class MoveSelectionActionBase : EditorActionItem {

    // 由子类提供的资源 ID
    abstract val titleResId: Int
    abstract val iconResId: Int

    override var label: String = ""
    override var visible: Boolean = false
    override var enabled: Boolean = false
    override var icon: Drawable? = null
    override var requiresUIThread: Boolean = true
    override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS

    override fun prepare(data: ActionData) {
        super.prepare(data)
        val context = data.requireContext()
        val editor = data.get(CodeEditor::class.java)

        // 绑定资源 (仅在尚未绑定时加载)
        if (label.isEmpty() && titleResId != 0) {
            label = context.getString(titleResId)
        }
        if (icon == null && iconResId != 0) {
            icon = ContextCompat.getDrawable(context, iconResId)
        }

        // 仅在有文本选中的情况下显示并生效
        val isSelected = editor?.cursor?.isSelected ?: false
        visible = isSelected
        enabled = isSelected
    }

    override suspend fun execAction(data: ActionData): Any {
        val editor = data.requireEditor()
        if (!editor.cursor.isSelected) return false

        val text = editor.text
        val cursor = editor.cursor
        val start = cursor.left().fromThis()
        val end = cursor.right().fromThis()
        
        // 1. 获取选中的文本内容
        val selectedText = text.subSequence(start.index, end.index).toString()
        val selectionLength = end.index - start.index

        // 2. 让子类计算目标插入的绝对索引（以当前未被删除的文本状态为基准计算）
        val targetInsertIndex = calculateTargetInsertIndex(editor, start, end)
        if (targetInsertIndex == -1) return false // 处于边缘无法移动

        text.beginBatchEdit()

        // 3. 先移除原有的选中文本
        text.delete(start.index, end.index)

        // 4. 偏移修正：如果目标插入点在被删除文本的后面，删除操作会导致后面的索引向左塌陷，必须扣除选中长度
        val finalInsertIndex = if (targetInsertIndex > start.index) {
            targetInsertIndex - selectionLength
        } else {
            targetInsertIndex
        }

        // 5. 在最终目标位置插入之前选中的文本
        val insertPos = text.indexer.getCharPosition(finalInsertIndex)
        text.insert(insertPos.line, insertPos.column, selectedText)

        text.endBatchEdit()

        // 6. 重新选中移动后的文本，以便允许用户连续点击移动
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