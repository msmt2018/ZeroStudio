package com.itsaky.androidide.actions.code

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.*
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.actions.code.jumpsymbol.GoToSymbolAction
import com.itsaky.androidide.actions.code.javatokotlin.JavaToKotlinAction
import com.itsaky.androidide.actions.code.OrganizeImportsAction
/**
 * @param context The application context, used for retrieving resources.
 * @param order The order of this action in menus or toolbars.
 */
class CodeActionsMenu(context: Context, override val order: Int) : EditorRelatedAction(), ActionMenu {
  
    override val children: MutableSet<ActionItem> = mutableSetOf()
    override val id: String = "ide.editor.code.text.code_menu"
    
    /**
     * Initializes the menu action by setting its label and icon, and registering all
     * its child actions.
     */
    init {
        label = context.getString(R.string.edit)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_code)
        var order = 0

        addAction(JumpToLineAction(context, order++))
        addAction(OverrideMethodsAction(context, order++))
        addAction(JavaToKotlinAction(context, order++))
        addAction(GoToSymbolAction(context, order++))
        addAction(OrganizeImportsAction(context, order++))
        
        addAction(FormatCodeAction(context, order++))
        addAction(ToggleReadOnlyAction(context, order++))
        addAction(ShowSnippetsAction(context, order++))
        addAction(SwitchToIfElseAction(context, order++))
        addAction(IfElseToSwitchAction(context, order++))
    }

    
    override fun prepare(data: ActionData) {
        super<EditorRelatedAction>.prepare(data)
        super<ActionMenu>.prepare(data)

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