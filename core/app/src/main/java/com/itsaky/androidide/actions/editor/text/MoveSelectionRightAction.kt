package com.itsaky.androidide.actions.editor

import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 将选中的文本向右移动
 * 
 * @author android_zero
 */
class MoveSelectionRightAction : MoveSelectionActionBase() {
    override val id: String = "ide.editor.selection.moveRight"
    override val titleResId: Int = R.string.action_move_selection_right
    override val iconResId: Int = R.drawable.ic_editor_selection_arrow_right

    override fun calculateTargetInsertIndex(editor: CodeEditor, start: CharPosition, end: CharPosition): Int {
        val text = editor.text
        // 已在文件尾部，无法右移
        if (end.index >= text.length) return -1
        
        var step = 1
        // 兼容 Surrogate Pair (双字节字符或Emoji)
        if (end.index + 1 < text.length && Character.isHighSurrogate(text.charAt(end.index)) && Character.isLowSurrogate(text.charAt(end.index + 1))) {
            step = 2
        }
        // 向右移动相当于将文本插入到紧邻的下一个字符之后
        return end.index + step
    }
}