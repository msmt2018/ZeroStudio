package com.itsaky.androidide.fragments.git.tree

import android.zero.studio.view.filetree.widget.FileTree
import java.util.Stack

/**
 * 高级状态管理器：实现文件树节点展开/折叠的 撤销(Undo) 和 重做(Redo)。
 *
 * @author android_zero
 */
class TreeStateManager {
  data class NodeAction(val path: String, val expandedAfterAction: Boolean)

  private val undoStack = Stack<NodeAction>()
  private val redoStack = Stack<NodeAction>()
  private val MAX_HISTORY_SIZE = 50

  fun recordAction(path: String, expandedAfterAction: Boolean) {
    val action = NodeAction(path = path, expandedAfterAction = expandedAfterAction)
    if (undoStack.isNotEmpty() && undoStack.peek() == action) return
    undoStack.push(action)
    if (undoStack.size > MAX_HISTORY_SIZE) undoStack.removeAt(0)
    redoStack.clear()
  }

  fun undo(treeView: FileTree) {
    if (undoStack.isEmpty()) return
    val action = undoStack.pop()
    if (action.expandedAfterAction) {
      treeView.collapseByPath(action.path)
    } else {
      treeView.expandByPath(action.path)
    }
    redoStack.push(action)
  }

  fun redo(treeView: FileTree) {
    if (redoStack.isEmpty()) return
    val action = redoStack.pop()
    if (action.expandedAfterAction) {
      treeView.expandByPath(action.path)
    } else {
      treeView.collapseByPath(action.path)
    }
    undoStack.push(action)
  }
}
