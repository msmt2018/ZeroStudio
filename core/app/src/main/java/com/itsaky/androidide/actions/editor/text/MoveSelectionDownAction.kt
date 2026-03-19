package com.itsaky.androidide.actions.editor

import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 将选中的文本向下移动
 * 
 * @author android_zero
 */
class MoveSelectionDownAction : MoveSelectionActionBase() {
    override val id: String = "ide.editor.selection.moveDown"
    override val titleResId: Int = R.string.action_move_selection_down
    override val iconResId: Int = R.drawable.ic_editor_selection_arrow_down

    override fun calculateTargetInsertIndex(editor: CodeEditor, start: CharPosition, end: CharPosition): Int {
        val targetLine = end.line + 1
        
        // 处于底行无法向下跨行移动
        if (targetLine >= editor.text.lineCount) return -1
        
        // 尽量保持在同样的列进行插入，若下方行较短，则贴紧行尾
        val targetCol = kotlin.math.min(start.column, editor.text.getColumnCount(targetLine))
        
        return editor.text.indexer.getCharIndex(targetLine, targetCol)
    }
}