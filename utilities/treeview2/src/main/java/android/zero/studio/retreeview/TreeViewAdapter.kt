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

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import androidx.recyclerview.widget.RecyclerView
import android.zero.studio.retreeview.base.BaseNodeViewBinder
import android.zero.studio.retreeview.base.BaseNodeViewFactory
import android.zero.studio.retreeview.base.CheckableNodeViewBinder
import android.zero.studio.retreeview.helper.TreeHelper

internal class TreeViewAdapter<D>(
    private val context: Context,
    private val root: TreeNode<D>,
    private val baseNodeViewFactory: BaseNodeViewFactory<D>
) : RecyclerView.Adapter<BaseNodeViewBinder<D>>() {

    private val expandedNodeList: MutableList<TreeNode<D>> = ArrayList()

    private var treeView: TreeView<D>? = null

    init {
        buildExpandedNodeList()
    }

    private fun buildExpandedNodeList() {
        expandedNodeList.clear()

        for (child in root.getChildren()) {
            insertNode(expandedNodeList, child)
        }
    }

    private fun insertNode(nodeList: MutableList<TreeNode<D>>, treeNode: TreeNode<D>) {
        nodeList.add(treeNode)

        if (!treeNode.hasChild()) {
            return
        }
        if (treeNode.isExpanded) {
            for (child in treeNode.getChildren()) {
                insertNode(nodeList, child)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        // return expandedNodeList.get(position).getLevel(); // this old code row used to always return the level
        val treeNode = expandedNodeList[position]
        return this.baseNodeViewFactory.getViewType(treeNode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, level: Int): BaseNodeViewBinder<D> {
        val view = LayoutInflater.from(context).inflate(baseNodeViewFactory.getNodeLayoutId(level), parent, false)

        val nodeViewBinder = baseNodeViewFactory.getNodeViewBinder(view, level)
        val tv = treeView
        if (tv != null) {
            nodeViewBinder.bindTreeView(tv)
        }
        return nodeViewBinder
    }

    override fun onBindViewHolder(holder: BaseNodeViewBinder<D>, position: Int) {
        val nodeView = holder.itemView
        val treeNode = expandedNodeList[position]
        val viewBinder = holder

        if (viewBinder.getToggleTriggerViewId() != 0) {
            val triggerToggleView = nodeView.findViewById<View>(viewBinder.getToggleTriggerViewId())

            triggerToggleView?.let {
                it.setOnClickListener {
                    onNodeToggled(treeNode)
                    viewBinder.onNodeToggled(treeNode, treeNode.isExpanded)
                }

                it.setOnLongClickListener { view ->
                    viewBinder.onNodeLongClicked(view, treeNode, treeNode.isExpanded)
                }
            }
        } else if (treeNode.itemClickEnable) {
            nodeView.setOnClickListener {
                onNodeToggled(treeNode)
                viewBinder.onNodeToggled(treeNode, treeNode.isExpanded)
            }

            nodeView.setOnLongClickListener { view ->
                viewBinder.onNodeLongClicked(view, treeNode, treeNode.isExpanded)
            }
        }

        if (viewBinder is CheckableNodeViewBinder<D>) {
            setupCheckableItem(nodeView, treeNode, viewBinder)
        }

        viewBinder.bindView(treeNode)
    }

    private fun setupCheckableItem(
        nodeView: View,
        treeNode: TreeNode<D>,
        viewBinder: CheckableNodeViewBinder<D>
    ) {
        val view = nodeView.findViewById<View>(viewBinder.getCheckableViewId())

        if (view is Checkable) {
            val checkableView = view
            checkableView.isChecked = treeNode.isSelected

            view.setOnClickListener {
                val checked = checkableView.isChecked
                selectNode(checked, treeNode)
                viewBinder.onNodeSelectedChanged(treeNode, checked)
            }
        } else {
            throw ClassCastException("The getCheckableViewId() must return a CheckBox's id")
        }
    }

    internal fun selectNode(checked: Boolean, treeNode: TreeNode<D>) {
        treeNode.isSelected = checked

        selectChildren(treeNode, checked)
        selectParentIfNeed(treeNode, checked)
    }

    private fun selectChildren(treeNode: TreeNode<D>, checked: Boolean) {
        val impactedChildren = TreeHelper.selectNodeAndChild(treeNode, checked)
        val index = expandedNodeList.indexOf(treeNode)
        if (index != -1 && impactedChildren.size > 0) {
            notifyItemRangeChanged(index, impactedChildren.size + 1)
        }
    }

    private fun selectParentIfNeed(treeNode: TreeNode<D>, checked: Boolean) {
        val impactedParents = TreeHelper.selectParentIfNeedWhenNodeSelected(treeNode, checked)
        if (impactedParents.size > 0) {
            for (parent in impactedParents) {
                val position = expandedNodeList.indexOf(parent)
                if (position != -1) notifyItemChanged(position)
            }
        }
    }

    fun onNodeToggled(treeNode: TreeNode<D>) {
        treeNode.isExpanded = !treeNode.isExpanded

        if (treeNode.isExpanded) {
            // 懒加载：在展开前检查是否需要加载子节点
            if (!treeNode.isChildrenLoaded) {
                baseNodeViewFactory.onLoadChildren(treeNode)
            }

            expandNode(treeNode)

            // expand folders recursively
            if (!treeNode.isLeaf() && treeNode.getChildren().size == 1) {
                val subNode = treeNode.getChildren()[0]

                if (!subNode.isLeaf() && !subNode.isExpanded) {
                    onNodeToggled(subNode)
                }
            }

        } else {
            collapseNode(treeNode)
        }
    }

    override fun getItemCount(): Int {
        return expandedNodeList.size
    }

    /**
     * Refresh all,this operation is only used for refreshing list when a large of nodes have
     * changed value or structure because it take much calculation.
     */
    @SuppressLint("NotifyDataSetChanged")
    internal fun refreshView() {
        buildExpandedNodeList()
        notifyDataSetChanged()
    }

    // Insert a node list after index.
    private fun insertNodesAtIndex(index: Int, additionNodes: List<TreeNode<D>>?) {
        if (index < 0 || index > expandedNodeList.size - 1 || additionNodes == null) {
            return
        }
        expandedNodeList.addAll(index + 1, additionNodes)
        notifyItemRangeInserted(index + 1, additionNodes.size)
    }

    //Remove a node list after index.
    private fun removeNodesAtIndex(index: Int, removedNodes: List<TreeNode<D>>?) {
        if (index < 0 || index > expandedNodeList.size - 1 || removedNodes == null) {
            return
        }
        expandedNodeList.removeAll(removedNodes.toSet())
        notifyItemRangeRemoved(index + 1, removedNodes.size)
    }

    /**
     * Expand node. This operation will keep the structure of children(not expand children)
     */
    internal fun expandNode(treeNode: TreeNode<D>?) {
        if (treeNode == null) {
            return
        }
        val additionNodes = TreeHelper.expandNode(treeNode, false)
        val index = expandedNodeList.indexOf(treeNode)

        insertNodesAtIndex(index, additionNodes)
    }

    /**
     * Collapse node. This operation will keep the structure of children(not collapse children)
     */
    internal fun collapseNode(treeNode: TreeNode<D>?) {
        if (treeNode == null) {
            return
        }
        val removedNodes = TreeHelper.collapseNode(treeNode, false)
        val index = expandedNodeList.indexOf(treeNode)

        removeNodesAtIndex(index, removedNodes)
    }

    /**
     * Delete a node from list.This operation will also delete its children.
     */
    internal fun deleteNode(node: TreeNode<D>?) {
        if (node == null || node.parent == null) {
            return
        }
        val allNodes = TreeHelper.getAllNodes(root)
        if (allNodes.contains(node)) {
            node.parent?.removeChild(node)
        }

        //remove children form list before delete
        collapseNode(node)

        val index = expandedNodeList.indexOf(node)
        if (index != -1) {
            expandedNodeList.remove(node)
        }
        notifyItemRemoved(index)
    }

    internal fun setTreeView(treeView: TreeView<D>) {
        this.treeView = treeView
    }
}
