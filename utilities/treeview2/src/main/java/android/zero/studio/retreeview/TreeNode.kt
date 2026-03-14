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

package android.zero.studio.retreeview

import android.zero.studio.retreeview.helper.TreeHelper

class TreeNode<D>(
    var value: D?,
    var level: Int = 0
) {
    var parent: TreeNode<D>? = null
    private var childNodes: MutableList<TreeNode<D>> = ArrayList()
    var index: Int = 0
    var isExpanded: Boolean = false
    var isSelected: Boolean = false
    var itemClickEnable: Boolean = true

    /**
     * 标记子节点是否已加载（用于懒加载）
     * true = 已加载真实子节点
     * false = 未加载，可能有占位符子节点
     */
    var isChildrenLoaded: Boolean = true

    companion object {
        fun <D> root(): TreeNode<D> {
            return TreeNode(null, 0)
        }

        fun <D> root(children: List<TreeNode<D>>): TreeNode<D> {
            val root = root<D>()
            root.setChildren(children)
            return root
        }
    }

    fun addChild(treeNode: TreeNode<D>?) {
        if (treeNode == null) {
            return
        }
        childNodes.add(treeNode)
        treeNode.index = childNodes.size
        treeNode.parent = this
    }

    fun removeChild(treeNode: TreeNode<D>?) {
        if (treeNode == null || childNodes.size < 1) {
            return
        }
        childNodes.remove(treeNode)
    }

    fun isLeaf(): Boolean {
        return childNodes.size == 0
    }

    fun isLastChild(): Boolean {
        if (parent == null) {
            return false
        }
        val children = parent!!.childNodes
        return children.size > 0 && children.indexOf(this) == children.size - 1
    }

    fun isRoot(): Boolean {
        return parent == null
    }

    fun getContent(): D? {
        return value
    }

    fun getChildren(): List<TreeNode<D>> {
        return childNodes
    }

    fun getSelectedChildren(): List<TreeNode<D>> {
        val selectedChildren = ArrayList<TreeNode<D>>()
        for (child in childNodes) {
            if (child.isSelected) {
                selectedChildren.add(child)
            }
        }
        return selectedChildren
    }

    fun setChildren(children: List<TreeNode<D>>?) {
        if (children == null) {
            return
        }
        childNodes = ArrayList()
        for (child in children) {
            addChild(child)
        }
    }

    /**
     * Updating the list of children while maintaining the tree structure
     */
    fun updateChildren(children: List<TreeNode<D>>) {
        val expands = ArrayList<Boolean>()
        val allNodesPre = TreeHelper.getAllNodes(this)
        for (node in allNodesPre) {
            expands.add(node.isExpanded)
        }

        childNodes = children.toMutableList()
        val allNodes = TreeHelper.getAllNodes(this)
        if (allNodes.size == expands.size) {
            for (i in allNodes.indices) {
                allNodes[i].isExpanded = expands[i]
            }
        }
    }

    fun hasChild(): Boolean {
        return childNodes.size > 0
    }

    /**
     * 清空子节点列表
     */
    fun clearChildren() {
        childNodes.clear()
    }

    fun getId(): String {
        return "$level,$index"
    }
}
