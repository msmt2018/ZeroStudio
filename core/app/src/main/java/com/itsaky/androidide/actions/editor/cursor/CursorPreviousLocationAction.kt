package com.itsaky.androidide.actions.editor.cursor

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorActionItem
import com.itsaky.androidide.actions.requireEditor
import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.widget.CodeEditor
import com.itsaky.androidide.cursor.CursorHistoryManager

/**
 * Navigate to the last cursor position Action
 * @author android_zero
 */
class CursorPreviousLocationAction(context: Context, override val order: Int) : EditorActionItem {
    override val id: String = "ide.editor.cursor.prevLocation"
    override var label: String = ""
    override var visible: Boolean = true
    override var enabled: Boolean = false
    override var icon: android.graphics.drawable.Drawable? = null
    override var requiresUIThread: Boolean = true
    override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS

    // 缓存 Editor
    private var currentEditor: CodeEditor? = null

    init {
        label = context.getString(R.string.title_menus_editor_cursor_prevLocation)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_editor_cursor_prev_location)
    }

    override fun prepare(data: ActionData) {
        super.prepare(data)
        val editor = data.get(CodeEditor::class.java)
        if (editor != null) {
            currentEditor = editor
            enabled = CursorHistoryManager.getTracker(editor).canGoBack()
        } else {
            enabled = false
        }
    }

    override suspend fun execAction(data: ActionData): Any {
        val editor = currentEditor ?: return false
        CursorHistoryManager.getTracker(editor).goBack()
        return true
    }
}