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
package android.zero.studio.retreeview.base

import android.zero.studio.retreeview.TreeNode

interface BaseTreeAction<D> {
    fun expandAll()

    fun expandNode(treeNode: TreeNode<D>)

    fun expandLevel(level: Int)

    fun collapseAll()

    fun collapseNode(treeNode: TreeNode<D>)

    fun collapseLevel(level: Int)

    fun toggleNode(treeNode: TreeNode<D>)

    fun deleteNode(node: TreeNode<D>)

    fun addNode(parent: TreeNode<D>, treeNode: TreeNode<D>)

    fun getAllNodes(): List<TreeNode<D>>

    // 1.add node at position
    // 2.add slide delete or other operations
}
