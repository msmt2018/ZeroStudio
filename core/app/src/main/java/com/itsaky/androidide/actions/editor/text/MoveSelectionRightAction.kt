package com.itsaky.androidide.actions.editor.text

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 将选中的文本向右移动
 * 
 * 功能描述：计算将选中文本向右移动一个字符（或一个完整代理对/Emoji）时的目标插入索引。
 * @author android_zero
 */
class MoveSelectionRightAction(context: Context, override val order: Int) : MoveSelectionActionBase() {
    override val id: String = "ide.editor.selection.moveRight"

    init {
        label = context.getString(R.string.title_menus_editor_selection_moveRight)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_editor_selection_arrow_right)
    }

    override fun calculateTargetInsertIndex(editor: CodeEditor, start: CharPosition, end: CharPosition): Int {
        val text = editor.text
        
        // 已在文件尾部，无法右移
        if (end.index >= text.length) return -1
        
        var step = 1
        
        if (end.index + 1 < text.length && 
            Character.isHighSurrogate(text[end.index]) && 
            Character.isLowSurrogate(text[end.index + 1])
        ) {
            step = 2
        }
        
        // 向右移动相当于将文本插入到紧邻的下一个字符（或双字节字符）之后
        return end.index + step
    }
}