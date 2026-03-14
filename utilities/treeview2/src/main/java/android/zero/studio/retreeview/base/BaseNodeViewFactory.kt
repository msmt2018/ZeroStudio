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
import android.zero.studio.retreeview.TreeNode

abstract class BaseNodeViewFactory<D> {

    /**
     * The default implementation below behaves as in previous version when TreeViewAdapter.getItemViewType always returned the level,
     * but you can override it if you want some other viewType value to become the parameter to the method getNodeViewBinder.
     */
    open fun getViewType(treeNode: TreeNode<D>): Int {
        return treeNode.level
    }

    /**
     * If you want build a tree view,you must implement this factory method
     *
     * @param view  The parameter for BaseNodeViewBinder's constructor, do not use this for other
     *              purpose!
     * @param viewType The viewType value is the treeNode level in the default implementation.
     * @return BaseNodeViewBinder
     */
    abstract fun getNodeViewBinder(view: View, viewType: Int): BaseNodeViewBinder<D>

    /**
     * If you want build a tree view,you must implement this factory method
     *
     * @param level Level of view, returned from {@link #getViewType}
     * @return node layout id
     */
    abstract fun getNodeLayoutId(level: Int): Int

    /**
     * 懒加载回调：在节点展开前调用，用于加载子节点
     * 子类可以重写此方法实现懒加载逻辑
     *
     * @param treeNode 要展开的节点
     * @return true 表示需要刷新视图，false 表示不需要
     */
    open fun onLoadChildren(treeNode: TreeNode<D>): Boolean {
        return false
    }
}
