package com.itsaky.androidide.actions.editor

import android.content.Context
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.BaseEditorAction
import com.itsaky.androidide.actions.EditorActivityAction
import com.itsaky.androidide.fragments.ColorQueryDialogFragment
import com.itsaky.androidide.resources.R

private fun resolveSelection(data: ActionData): String? {
  val editor = data.get(com.itsaky.androidide.editor.ui.IDEEditor::class.java) ?: return null
  if (!editor.cursor.isSelected) return null
  val left = editor.cursor.left().index
  val right = editor.cursor.right().index
  if (right <= left) return null
  return editor.text.subSequence(left, right).toString()
}

class ColorQueryTextAction(context: Context, override val order: Int) : BaseEditorAction() {
  override val id: String = "ide.editor.color.query.text"

  init {
    label = context.getString(R.string.action_color_query)
    location = ActionItem.Location.EDITOR_TEXT_ACTIONS
  }

  override suspend fun execAction(data: ActionData): Boolean {
    val ctx = getContext(data) ?: return false
    val fm = (ctx as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager ?: return false
    ColorQueryDialogFragment.newInstance(resolveSelection(data)).show(fm, "color_query")
    return true
  }
}

class ColorQueryToolbarAction(private val context: Context, override val order: Int) : EditorActivityAction() {
  override val id: String = "ide.editor.color.query.toolbar"

  init {
    label = context.getString(R.string.action_color_query)
    location = ActionItem.Location.EDITOR_TOOLBAR
  }

  override suspend fun execAction(data: ActionData): Any {
    val activity = data.getActivity() ?: return false
    val query = activity.getCurrentEditor()?.editor?.let {
      if (it.cursor.isSelected) {
        val left = it.cursor.left().index
        val right = it.cursor.right().index
        if (right > left) it.text.subSequence(left, right).toString() else null
      } else null
    }
    ColorQueryDialogFragment.newInstance(query).show(activity.supportFragmentManager, "color_query")
    return true
  }
}
