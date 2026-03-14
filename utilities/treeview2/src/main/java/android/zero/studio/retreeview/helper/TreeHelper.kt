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

package android.zero.studio.retreeview.helper

import android.zero.studio.retreeview.TreeNode

object TreeHelper {

    fun <D> expandAll(node: TreeNode<D>?) {
        if (node == null) {
            return
        }
        expandNode(node, true)
    }

    /**
     * Expand node and calculate the visible addition nodes.
     *
     * @param treeNode     target node to expand
     * @param includeChild should expand child
     * @return the visible addition nodes
     */
    fun <D> expandNode(treeNode: TreeNode<D>?, includeChild: Boolean): List<TreeNode<D>> {
        val expandChildren = ArrayList<TreeNode<D>>()

        if (treeNode == null) {
            return expandChildren
        }

        treeNode.isExpanded = true

        if (!treeNode.hasChild()) {
            return expandChildren
        }

        for (child in treeNode.getChildren()) {
            expandChildren.add(child)

            if (includeChild || child.isExpanded) {
                expandChildren.addAll(expandNode(child, includeChild))
            }
        }

        return expandChildren
    }

    /**
     * Expand the same deep(level) nodes.
     *
     * @param root  the tree root
     * @param level the level to expand
     */
    fun <D> expandLevel(root: TreeNode<D>?, level: Int) {
        if (root == null) {
            return
        }

        for (child in root.getChildren()) {
            if (child.level == level) {
                expandNode(child, false)
            } else {
                expandLevel(child, level)
            }
        }
    }

    fun <D> collapseAll(node: TreeNode<D>?) {
        if (node == null) {
            return
        }
        for (child in node.getChildren()) {
            performCollapseNode(child, true)
        }
    }

    /**
     * Collapse node and calculate the visible removed nodes.
     *
     * @param node         target node to collapse
     * @param includeChild should collapse child
     * @return the visible addition nodes before remove
     */
    fun <D> collapseNode(node: TreeNode<D>?, includeChild: Boolean): List<TreeNode<D>> {
        val treeNodes = performCollapseNode(node, includeChild)
        node?.isExpanded = false
        return treeNodes
    }

    private fun <D> performCollapseNode(node: TreeNode<D>?, includeChild: Boolean): List<TreeNode<D>> {
        val collapseChildren = ArrayList<TreeNode<D>>()

        if (node == null) {
            return collapseChildren
        }
        if (includeChild) {
            node.isExpanded = false
        }
        for (child in node.getChildren()) {
            collapseChildren.add(child)

            if (child.isExpanded) {
                collapseChildren.addAll(performCollapseNode(child, includeChild))
            } else if (includeChild) {
                performCollapseNodeInner(child)
            }
        }

        return collapseChildren
    }

    /**
     * Collapse all children node recursive
     *
     * @param node target node to collapse
     */
    private fun <D> performCollapseNodeInner(node: TreeNode<D>?) {
        if (node == null) {
            return
        }
        node.isExpanded = false
        for (child in node.getChildren()) {
            performCollapseNodeInner(child)
        }
    }

    fun <D> collapseLevel(root: TreeNode<D>?, level: Int) {
        if (root == null) {
            return
        }
        for (child in root.getChildren()) {
            if (child.level == level) {
                collapseNode(child, false)
            } else {
                collapseLevel(child, level)
            }
        }
    }

    fun <D> getAllNodes(root: TreeNode<D>): List<TreeNode<D>> {
        val allNodes = ArrayList<TreeNode<D>>()

        fillNodeList(allNodes, root)
        allNodes.remove(root)

        return allNodes
    }

    private fun <D> fillNodeList(treeNodes: MutableList<TreeNode<D>>, treeNode: TreeNode<D>) {
        treeNodes.add(treeNode)

        if (treeNode.hasChild()) {
            for (child in treeNode.getChildren()) {
                fillNodeList(treeNodes, child)
            }
        }
    }

    /**
     * Select the node and node's children,return the visible nodes
     */
    fun <D> selectNodeAndChild(treeNode: TreeNode<D>?, select: Boolean): List<TreeNode<D>> {
        val expandChildren = ArrayList<TreeNode<D>>()

        if (treeNode == null) {
            return expandChildren
        }

        treeNode.isSelected = select

        if (!treeNode.hasChild()) {
            return expandChildren
        }

        if (treeNode.isExpanded) {
            for (child in treeNode.getChildren()) {
                expandChildren.add(child)

                if (child.isExpanded) {
                    expandChildren.addAll(selectNodeAndChild(child, select))
                } else {
                    selectNodeInner(child, select)
                }
            }
        } else {
            selectNodeInner(treeNode, select)
        }
        return expandChildren
    }

    private fun <D> selectNodeInner(treeNode: TreeNode<D>?, select: Boolean) {
        if (treeNode == null) {
            return
        }
        treeNode.isSelected = select
        if (treeNode.hasChild()) {
            for (child in treeNode.getChildren()) {
                selectNodeInner(child, select)
            }
        }
    }

    /**
     * Select parent when all the brothers have been selected, otherwise deselect parent,
     * and check the grand parent recursive.
     */
    fun <D> selectParentIfNeedWhenNodeSelected(treeNode: TreeNode<D>?, select: Boolean): List<TreeNode<D>> {
        val impactedParents = ArrayList<TreeNode<D>>()
        if (treeNode == null) {
            return impactedParents
        }

        //ensure that the node's level is bigger than 1(first level is 1)
        val parent = treeNode.parent
        if (parent == null || parent.parent == null) {
            return impactedParents
        }

        val brothers = parent.getChildren()
        var selectedBrotherCount = 0
        for (brother in brothers) {
            if (brother.isSelected) selectedBrotherCount++
        }

        if (select && selectedBrotherCount == brothers.size) {
            parent.isSelected = true
            impactedParents.add(parent)
            impactedParents.addAll(selectParentIfNeedWhenNodeSelected(parent, true))
        } else if (!select && selectedBrotherCount == brothers.size - 1) {
            // only the condition that the size of selected's brothers
            // is one less than total count can trigger the deselect
            parent.isSelected = false
            impactedParents.add(parent)
            impactedParents.addAll(selectParentIfNeedWhenNodeSelected(parent, false))
        }
        return impactedParents
    }

    /**
     * Get the selected nodes under current node, include itself
     */
    fun <D> getSelectedNodes(treeNode: TreeNode<D>?): List<TreeNode<D>> {
        val selectedNodes = ArrayList<TreeNode<D>>()
        if (treeNode == null) {
            return selectedNodes
        }

        if (treeNode.isSelected && treeNode.parent != null) selectedNodes.add(treeNode)

        for (child in treeNode.getChildren()) {
            selectedNodes.addAll(getSelectedNodes(child))
        }
        return selectedNodes
    }

    /**
     * Return true when the node has one selected child(recurse all children) at least,
     * otherwise return false
     */
    fun <D> hasOneSelectedNodeAtLeast(treeNode: TreeNode<D>?): Boolean {
        if (treeNode == null || treeNode.getChildren().size == 0) {
            return false
        }
        val children = treeNode.getChildren()
        for (child in children) {
            if (child.isSelected || hasOneSelectedNodeAtLeast(child)) {
                return true
            }
        }
        return false
    }
}
