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
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import android.zero.studio.retreeview.base.BaseNodeViewFactory
import android.zero.studio.retreeview.base.SelectableTreeAction
import android.zero.studio.retreeview.helper.TreeHelper

class TreeView<D>(
    private val context: Context,
    private var root: TreeNode<D>
) : SelectableTreeAction<D> {

    interface OnTreeNodeClickListener<D> {
        fun onTreeNodeClicked(treeNode: TreeNode<D>, expand: Boolean)
    }

    private var rootView: RecyclerView? = null
    private var adapter: TreeViewAdapter<D>? = null
    private var baseNodeViewFactory: BaseNodeViewFactory<D>? = null

    var itemSelectable: Boolean = true

    fun getView(): View {
        if (rootView == null) {
            this.rootView = buildRootView()
        }

        return rootView!!
    }

    fun getRoot(): TreeNode<D>? {
        val allNodes = getAllNodes()
        if (allNodes.isEmpty()) {
            return null
        }
        return allNodes[0]
    }

    private fun buildRootView(): RecyclerView {
        val recyclerView = RecyclerView(context)

        recyclerView.isMotionEventSplittingEnabled = false // disable multi touch event to prevent terrible data set error when calculate list.
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        recyclerView.layoutManager = LinearLayoutManager(context)

        return recyclerView
    }

    fun setAdapter(baseNodeViewFactory: BaseNodeViewFactory<D>) {
        this.baseNodeViewFactory = baseNodeViewFactory

        adapter = TreeViewAdapter(context, root, baseNodeViewFactory)
        adapter?.setTreeView(this)

        // 确保 RecyclerView 已创建后再设置 adapter
        val rv = getView() as RecyclerView
        rv.adapter = adapter
    }

    override fun expandAll() {
        TreeHelper.expandAll(root)

        refreshTreeView()
    }

    fun refreshTreeView() {
        rootView?.let {
            (it.adapter as? TreeViewAdapter<*>)?.refreshView()
        }
    }

    fun refreshTreeView(root: TreeNode<D>) {
        this.root = root

        baseNodeViewFactory?.let { setAdapter(it) }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateTreeView() {
        rootView?.adapter?.notifyDataSetChanged()
    }

    override fun expandNode(treeNode: TreeNode<D>) {
        adapter?.expandNode(treeNode)
    }

    override fun expandLevel(level: Int) {
        TreeHelper.expandLevel(root, level)

        refreshTreeView()
    }

    override fun collapseAll() {
        TreeHelper.collapseAll(root)

        refreshTreeView()
    }

    override fun collapseNode(treeNode: TreeNode<D>) {
        adapter?.collapseNode(treeNode)
    }

    override fun collapseLevel(level: Int) {
        TreeHelper.collapseLevel(root, level)

        refreshTreeView()
    }

    override fun toggleNode(treeNode: TreeNode<D>) {
        if (treeNode.isExpanded) {
            collapseNode(treeNode)
        } else {
            expandNode(treeNode)
        }
    }

    override fun deleteNode(node: TreeNode<D>) {
        adapter?.deleteNode(node)
    }

    override fun addNode(parent: TreeNode<D>, treeNode: TreeNode<D>) {
        parent.addChild(treeNode)

        refreshTreeView()
    }

    override fun getAllNodes(): List<TreeNode<D>> {
        return TreeHelper.getAllNodes(root)
    }

    override fun selectNode(treeNode: TreeNode<D>) {
        adapter?.selectNode(true, treeNode)
    }

    override fun deselectNode(treeNode: TreeNode<D>) {
        adapter?.selectNode(false, treeNode)
    }

    override fun selectAll() {
        TreeHelper.selectNodeAndChild(root, true)

        refreshTreeView()
    }

    override fun deselectAll() {
        TreeHelper.selectNodeAndChild(root, false)

        refreshTreeView()
    }

    override fun getSelectedNodes(): List<TreeNode<D>> {
        return TreeHelper.getSelectedNodes(root)
    }
}
