/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.fragments.editor.filetree

import android.zero.studio.view.filetree.widget.FileTree
import java.util.Stack

/**
 * 高级状态管理器:实现文件树节点展开/折叠的 撤销(Undo) 和 重做(Redo)。
 *
 * 文件树核心组件 — IDE 文件树支持 Cmd+Z 撤销展开, Cmd+Shift+Z 重做展开。
 *
 * 恢复自 fragments/git/tree/TreeStateManager.kt (commit 4b81f60c 删除),
 * 迁移到 fragments/editor/filetree/ 路径以统一文件树相关代码组织。
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
