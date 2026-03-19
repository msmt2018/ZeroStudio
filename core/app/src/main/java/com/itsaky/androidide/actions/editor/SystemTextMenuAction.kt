package com.itsaky.androidide.actions.editor

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorActionItem
import com.itsaky.androidide.actions.requireContext
import com.itsaky.androidide.actions.requireEditor
import io.github.rosemoe.sora.widget.CodeEditor
import com.itsaky.androidide.resources.R

/**
 * 触发系统自定义文本操作菜单的 Action（集成系统级应用扩展功能，如翻译、小爱同学）
 *
 * @author android_zero
 */
class SystemTextMenuAction(context: Context, override val order: Int) : EditorActionItem {

    override val id: String = "ide.editor.selection.system_actions"
    
    // 设置此动作所在位置，比如编辑器文本选择浮动菜单
    override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS
    
    override var label: String = "system menus"
    override var visible: Boolean = false
    override var enabled: Boolean = false
    override var icon: Drawable? = null
    override var requiresUIThread: Boolean = true

    init {
        icon = ContextCompat.getDrawable(context, R.drawable.more_vert)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        val editor = data.get(CodeEditor::class.java)
        // 仅在有文本选中的情况下显示并生效
        val isSelected = editor?.cursor?.isSelected ?: false
        visible = isSelected
        enabled = isSelected
    }

    override suspend fun execAction(data: ActionData): Any {
        val editor = data.requireEditor()
        val context = data.requireContext()

        if (!editor.cursor.isSelected) return false

        val text = editor.text
        val cursor = editor.cursor
        // 获取选中的文本
        val selectedText = text.subSequence(cursor.left().index, cursor.right().index).toString()

        // 计算弹窗显示的物理位置（光标底部）
        val leftLine = cursor.leftLine
        val leftCol = cursor.leftColumn
        
        val layoutOffset = editor.layout.getCharLayoutOffset(leftLine, leftCol)
        
        val screenPos = IntArray(2)
        editor.getLocationInWindow(screenPos)
        
        val posX = (screenPos[0] + editor.measureTextRegionOffset() + layoutOffset[1] - editor.offsetX).toInt()
        // 加上行高，使其显示在光标下方
        val posY = (screenPos[1] + layoutOffset[0] - editor.offsetY + editor.rowHeight).toInt()

        // 实例化并显示 Compose 浮窗
        val popup = SystemTextActionsPopup(context, editor, selectedText)
        popup.show(editor, posX, posY)

        return true
    }

    // 阻止点击此菜单后默认关闭外层浮动菜单，因为我们要自己展示一个新的 PopWindow
    override fun dismissOnAction(): Boolean {
        return true 
    }
}