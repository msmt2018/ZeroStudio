package com.itsaky.androidide.fragments.git.tree

import com.rk.filetree.view.AndroidTreeView
import java.util.Stack

class TreeStateManager {

  private val undoStack = Stack<String>()
  private val redoStack = Stack<String>()
  private val MAX_HISTORY_SIZE = 50

  fun saveState(treeView: AndroidTreeView) {
    val state = treeView.saveState ?: ""

    // 只有当状态真正改变时才保存
    if (undoStack.isNotEmpty() && undoStack.peek() == state) {
      return
    }

    undoStack.push(state)
    if (undoStack.size > MAX_HISTORY_SIZE) {
      undoStack.removeAt(0)
    }
    // 新操作会清空重做栈
    redoStack.clear()
  }

  fun undo(currentState: String): String? {
    if (undoStack.isEmpty()) return null

    // 将当前状态压入 Redo 栈，以便可以重做回当前状态
    redoStack.push(currentState)

    return undoStack.pop()
  }

  fun redo(currentState: String): String? {
    if (redoStack.isEmpty()) return null

    // 将当前状态压入 Undo 栈，以便可以再次撤销
    undoStack.push(currentState)

    return redoStack.pop()
  }

  fun canUndo(): Boolean = undoStack.isNotEmpty()

  fun canRedo(): Boolean = redoStack.isNotEmpty()

  fun clear() {
    undoStack.clear()
    redoStack.clear()
  }
}
