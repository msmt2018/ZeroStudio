package com.itsaky.androidide.actions.editor.cursor

import android.graphics.drawable.Drawable
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorActionItem
import com.itsaky.androidide.actions.requireEditor
import io.github.rosemoe.sora.widget.CodeEditor
import com.itsaky.androidide.cursor.CursorHistoryManager
import com.itsaky.androidide.resources.R

/**
 * Navigate to the last cursor position Action
 * 
 * @author android_zero
 */
class CursorPreviousLocationAction : EditorActionItem {
    override val id: String = "ide.editor.cursor.prevLocation"
    override var label: String = "上一次光标位置"
    override var visible: Boolean = true
    override var enabled: Boolean = false
    override var icon: Drawable? = R.drawable.ic_editor_cursor_prev_location
    override var requiresUIThread: Boolean = true
    override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS

    override fun prepare(data: ActionData) {
        super.prepare(data)
        val editor = data.get(CodeEditor::class.java)
        enabled = editor != null && CursorHistoryManager.getTracker(editor).canGoBack()
    }

    override suspend fun execAction(data: ActionData): Any {
        val editor = data.requireEditor()
        CursorHistoryManager.getTracker(editor).goBack()
        return true
    }
}