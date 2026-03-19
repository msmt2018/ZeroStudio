// by android_zero
package com.itsaky.androidide.actions.menu

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.*
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.viewmodel.EditorViewModel
import java.io.File
import com.itsaky.androidide.actions.code.CodeActionsMenu

import com.itsaky.androidide.actions.editor.cursor.*
import com.itsaky.androidide.actions.editor.text.*

/**
 * An [ActionMenu] that consolidates all "Edit" menu operations for the text editor.
 *
 * This action serves as a container for a sub-menu that includes operations like
 * copy, cut, delete, duplicate, change case, manage indentation, toggle comments,
 * jump to a specific line, navigate cursor history, and toggle read-only mode.
 *
 * @param context The application context, used for retrieving resources.
 * @param order The order of this action in menus or toolbars.
 */
class EditorEditLineMenuAction(context: Context, override val order: Int) : EditorRelatedAction(), ActionMenu {

    /**
     * A mutable set holding all the child [ActionItem]s for this menu.
     */
    override val children: MutableSet<ActionItem> = mutableSetOf()

    /**
     * Initializes the menu action by setting its label and icon, and registering all
     * its child actions.
     */
    init {
        label = context.getString(R.string.edit)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_editor_text)
      var order = 0

        // Register all line and navigation operations as children of this ActionMenu
        addAction(CopyLineAction(context, order++))
        addAction(CutLineAction(context, order++))
        addAction(DeleteLineAction(context, order++))
        addAction(BackspaceContentAction(context, order++))
        addAction(EmptyLineAction(context, order++))
        addAction(ReplaceLineAction(context, order++))
        addAction(DuplicateLineAction(context, order++))
        addAction(ConvertUppercaseAction(context, order++))
        addAction(ConvertLowercaseAction(context, order++))
        addAction(IncreaseIndentAction(context, order++))
        addAction(DecreaseIndentAction(context, order++))
        addAction(ToggleCommentAction(context, order++))
        
        //光标上一个和下一个位置
        addAction(CursorPreviousLocationAction(context, order++))
        addAction(CursorNextLocationAction(context, order++))
        //与上下行相互交换
        addAction(SwapLineUpAction(context, order++))
        addAction(SwapLineDownAction(context, order++))
        //选中文本上下左右四个菜单功能
        addAction(MoveSelectionUpAction(context, order++)) //上
        addAction(MoveSelectionDownAction(context, order++)) //下
        addAction(MoveSelectionLeftAction(context, order++)) //左
        addAction(MoveSelectionRightAction(context, order++)) //右

    }

    /**
     * The unique identifier for the action.
     */
    override val id: String = "ide.editor.code.text.edit_menu"

    /**
     * Prepares the action by updating its state based on the current context.
     * This action is made invisible if no editor is available.
     *
     * @param data The [ActionData] object containing data required for the action.
     */
    override fun prepare(data: ActionData) {
        super<EditorRelatedAction>.prepare(data) // Call super for EditorRelatedAction
        super<ActionMenu>.prepare(data) // Call super for ActionMenu

        if (!visible) {
            return
        }

        val editor = data.getEditor() ?: run {
            markInvisible()
            return
        }

        // The "Edit" menu itself should be visible if an editor exists.
        // Child actions will handle their own enabled state.
        enabled = true
    }

    /**
     * Executes the action. For an [ActionMenu], this method is a no-op as the framework
     * is responsible for displaying the sub-menu.
     *
     * @return `true` to indicate the action was handled.
     */
    override suspend fun execAction(data: ActionData): Boolean {
        Log.d("EditorEditLineMenuAction", "execAction called. Framework should handle sub-menu display.")
        return true
    }
}
