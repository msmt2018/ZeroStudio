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

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import android.zero.studio.retreeview.TreeNode
import android.zero.studio.retreeview.TreeView

abstract class BaseNodeViewBinder<D>(itemView: View) : RecyclerView.ViewHolder(itemView) {
    /**
     * This reference of TreeView make BaseNodeViewBinder has the ability
     * to expand node or select node.
     */
    protected var treeView: TreeView<D>? = null

    /**
     * Attach TreeView instance to this binder.
     */
    fun bindTreeView(treeView: TreeView<D>) {
        this.treeView = treeView
    }

    /**
     * Bind your data to view,you can get the data from treeNode by getValue()
     *
     * @param treeNode Node data
     */
    abstract fun bindView(treeNode: TreeNode<D>)

    /**
     * if you do not want toggle the node when click whole item view,then you can assign a view to
     * trigger the toggle action
     *
     * @return The assigned view id to trigger expand or collapse.
     */
    open fun getToggleTriggerViewId(): Int {
        return 0
    }

    /**
     * Callback when a toggle action happened (only by clicked)
     *
     * @param treeNode The toggled node
     * @param expand   Expanded or collapsed
     */
    open fun onNodeToggled(treeNode: TreeNode<D>, expand: Boolean) {
        //empty
    }

    /**
     * Callback when a node is long clicked.
     */
    open fun onNodeLongClicked(view: View, treeNode: TreeNode<D>, expanded: Boolean): Boolean {
        return false
    }
}
