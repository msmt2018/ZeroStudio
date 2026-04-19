package com.itsaky.androidide.actions.editor.cursor

import android.content.Context
import androidx.core.content.ContextCompat
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.EditorActionItem
import com.itsaky.androidide.cursor.CursorHistoryManager
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.resources.R

/**
 * Navigate to the next cursor position (Action: Redo cursor)
 *
 * @author android_zero
 */
class CursorNextLocationAction(context: Context, override val order: Int) : EditorActionItem {
  override val id: String = "ide.editor.cursor.nextLocation"
  override var label: String = ""
  override var visible: Boolean = true
  override var enabled: Boolean = false
  override var icon: android.graphics.drawable.Drawable? = null
  override var requiresUIThread: Boolean = true
  override var location: ActionItem.Location = ActionItem.Location.EDITOR_TEXT_ACTIONS

  init {
    label = context.getString(R.string.title_menus_editor_cursor_nextLocation)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_editor_cursor_next_location)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)
    val editor = data.get(IDEEditor::class.java)
    // 动态切换可点击状态
    enabled = editor != null && CursorHistoryManager.getTracker(editor).canGoForward()
  }

  override suspend fun execAction(data: ActionData): Any {
    val editor = data.get(IDEEditor::class.java) ?: return false
    CursorHistoryManager.getTracker(editor).goForward()
    return true
  }
}
