package com.itsaky.androidide.actions.editor

import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 将选中的文本向上移动
 * 
 * @author android_zero
 */
class MoveSelectionUpAction : MoveSelectionActionBase() {
    override val id: String = "ide.editor.selection.moveUp"
    override val titleResId: Int = R.string.action_move_selection_up
    override val iconResId: Int = R.drawable.ic_editor_selection_arrow_up

    override fun calculateTargetInsertIndex(editor: CodeEditor, start: CharPosition, end: CharPosition): Int {
        // 处于顶行无法向上跨行移动
        if (start.line == 0) return -1
        
        val targetLine = start.line - 1
        val targetCol = kotlin.math.min(start.column, editor.text.getColumnCount(targetLine))
        
        return editor.text.indexer.getCharIndex(targetLine, targetCol)
    }
}