package com.itsaky.androidide.actions.editor

import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 将选中的文本向左移动
 * 
 * @author android_zero
 */
class MoveSelectionLeftAction : MoveSelectionActionBase() {
    override val id: String = "ide.editor.selection.moveLeft"
    override val titleResId: Int = R.string.action_move_selection_left
    override val iconResId: Int = R.drawable.ic_editor_selection_arrow_left

    override fun calculateTargetInsertIndex(editor: CodeEditor, start: CharPosition, end: CharPosition): Int {
        // 已在文件头部，无法左移
        if (start.index <= 0) return -1
        
        var step = 1
        val text = editor.text
        // 兼容 Surrogate Pair (双字节字符或Emoji)
        if (start.index >= 2 && Character.isLowSurrogate(text.charAt(start.index - 1)) && Character.isHighSurrogate(text.charAt(start.index - 2))) {
            step = 2
        }
        return start.index - step
    }
}