package com.itsaky.androidide.actions.editor.text

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 将选中的文本向左移动
 * 
 * 功能描述：计算将选中文本向左移动一个字符（或一个完整代理对/Emoji）时的目标插入索引。
 * @author android_zero
 */
class MoveSelectionLeftAction(context: Context, override val order: Int) : MoveSelectionActionBase() {
    override val id: String = "ide.editor.selection.moveLeft"

    init {
        label = context.getString(R.string.title_menus_editor_selection_moveLeft)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_editor_selection_arrow_left)
    }

    override fun calculateTargetInsertIndex(editor: CodeEditor, start: CharPosition, end: CharPosition): Int {
        // 已在文件头部，无法左移
        if (start.index <= 0) return -1
        
        var step = 1
        val text = editor.text
        
        if (start.index >= 2 && 
            Character.isLowSurrogate(text[start.index - 1]) && 
            Character.isHighSurrogate(text[start.index - 2])
        ) {
            step = 2
        }
        
        return start.index - step
    }
}