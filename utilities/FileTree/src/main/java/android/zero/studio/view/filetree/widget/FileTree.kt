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
package android.zero.studio.view.filetree.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.zero.studio.view.filetree.adapters.FileTreeAdapter
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileIconProvider
import android.zero.studio.view.filetree.interfaces.FileItemTrailingProvider
import android.zero.studio.view.filetree.interfaces.FileLongClickListener
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.provider.DefaultFileIconProvider
import android.zero.studio.view.filetree.util.Sorter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * A custom RecyclerView widget that displays a hierarchical file structure. This view allows users
 * to interact with files in a tree-like format, supporting both click and long-click events on
 * individual file nodes.
 *
 * @author android_zero
 */
class FileTree : RecyclerView {

  var fileTreeAdapter: FileTreeAdapter
    private set

  private lateinit var rootFileObject: FileObject

  private var isTreeInitialized = false
  private var isRootNodeVisible: Boolean = true
  private var autoExpandSingleChildFolders: Boolean = false

  constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

  constructor(
      context: Context,
      attrs: AttributeSet?,
      defStyleAttr: Int,
  ) : super(context, attrs, defStyleAttr)

  // Initialization block
  init {
    setItemViewCacheSize(100)
    layoutManager = LinearLayoutManager(context)
    fileTreeAdapter = FileTreeAdapter(context, this)
    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }

  fun setAutoExpandSingleChildFolders(enabled: Boolean) {
    autoExpandSingleChildFolders = enabled
  }

  /**
   * Sets a custom icon provider to supply icons for different file types.
   *
   * @param fileIconProvider The implementation of the FileIconProvider interface that will provide
   *   icons for file objects.
   */
  fun setIconProvider(fileIconProvider: FileIconProvider) {
    fileTreeAdapter.iconProvider = fileIconProvider
  }

  /**
   * Sets a listener that will be notified when a file node is clicked.
   *
   * @param clickListener The implementation of the FileClickListener interface that will handle
   *   click events on file nodes.
   */
  fun setOnFileClickListener(clickListener: FileClickListener) {
    fileTreeAdapter.onClickListener = clickListener
  }

  /**
   * Sets a listener that will be notified when a file node is long-clicked.
   *
   * @param longClickListener The implementation of the FileLongClickListener interface that will
   *   handle long-click events on file nodes.
   */
  fun setOnFileLongClickListener(longClickListener: FileLongClickListener) {
    fileTreeAdapter.onLongClickListener = longClickListener
  }

  /**
   * Sets a provider that controls an optional trailing widget (icon/button) rendered at
   * the right edge of each row. When the provider returns `null` for a node, the widget
   * is hidden for that node.
   */
  fun setTrailingProvider(provider: FileItemTrailingProvider?) {
    fileTreeAdapter.trailingProvider = provider
  }

  private var init = false
  private var showRootNode: Boolean = true

  /**
   * Loads the file tree starting from the specified root file.
   *
   * @param file The FileObject representing the root directory to be displayed.
   * @param showRootNodeX Optional parameter to determine whether the root node should be displayed.
   *   If null or true, the root node will be shown.
   */
  fun loadFiles(file: FileObject, showRootNodeX: Boolean? = null) {
    rootFileObject = file

    showRootNodeX?.let { isRootNodeVisible = it }

    val nodes: List<Node<FileObject>> =
        if (isRootNodeVisible) {
          mutableListOf<Node<FileObject>>().apply { add(Node(file)) }
        } else {
          Sorter.sort(file)
        }

    if (!isTreeInitialized) {
      if (fileTreeAdapter.iconProvider == null) {
        fileTreeAdapter.iconProvider = DefaultFileIconProvider(context)
      }
      adapter = fileTreeAdapter
      isTreeInitialized = true
    }

    fileTreeAdapter.submitList(nodes)
    if (isRootNodeVisible && nodes.isNotEmpty()) {
      fileTreeAdapter.expandNode(nodes[0])
    }
  }

  fun reloadFileTreeSilently() {
    // 记忆滚动状态
    val layoutManager = this.layoutManager as LinearLayoutManager
    val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
    val topOffset = layoutManager.findViewByPosition(firstVisiblePosition)?.top ?: 0
    val savedState = getSaveState()

    // 重新加载根节点数据
    val nodes: List<Node<FileObject>> =
        if (isRootNodeVisible) {
          mutableListOf<Node<FileObject>>().apply { add(Node(rootFileObject)) }
        } else {
          Sorter.sort(rootFileObject)
        }

    fileTreeAdapter.submitList(nodes) {
      // 恢复展开状态
      restoreState(savedState)
      // 恢复 Y 轴滚动位置
      post { layoutManager.scrollToPositionWithOffset(firstVisiblePosition, topOffset) }
    }
  }

  fun expandNode(node: Node<FileObject>) {
    if (!node.isExpand) {
      fileTreeAdapter.expandNode(node)
      if (autoExpandSingleChildFolders) {
        autoExpandUntilBranch(node)
      }
    }
  }

  fun collapseNode(node: Node<FileObject>) {
    try {
      val method = fileTreeAdapter.javaClass.getDeclaredMethod("collapseNode", Node::class.java)
      method.isAccessible = true
      method.invoke(fileTreeAdapter, node)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun expandByPath(path: String): Boolean {
    expandPathOnce(path)
    val node =
        fileTreeAdapter.currentList.firstOrNull { it.value.getAbsolutePath() == path }
            ?: return false
    if (!node.isExpand && node.value.isDirectory()) {
      fileTreeAdapter.expandNode(node)
    }
    return true
  }

  fun collapseByPath(path: String): Boolean {
    val node =
        fileTreeAdapter.currentList.firstOrNull { it.value.getAbsolutePath() == path }
            ?: return false
    if (node.isExpand && node.value.isDirectory()) {
      collapseNode(node)
    }
    return true
  }

  fun collapseAll() {
    val expanded =
        fileTreeAdapter.currentList.filter { it.isExpand }.sortedByDescending { it.level }
    expanded.forEach { collapseNode(it) }
  }

  fun expandAll() {
    var cursor = 0
    while (cursor < fileTreeAdapter.currentList.size) {
      val node = fileTreeAdapter.currentList[cursor]
      if (node.value.isDirectory() && !node.isExpand) {
        fileTreeAdapter.expandNode(node)
      }
      cursor++
    }
  }

  fun getSaveState(): String {
    val sb = java.lang.StringBuilder()
    fileTreeAdapter.currentList.forEach { node ->
      if (node.isExpand) {
        sb.append(node.value.getAbsolutePath()).append(";")
      }
    }
    return sb.toString().trimEnd(';')
  }

  fun restoreState(state: String?) {
    if (state.isNullOrEmpty()) return
    val pathsToExpand = state.split(";").toSet()

    val currentList = fileTreeAdapter.currentList.toList()
    for (node in currentList) {
      if (pathsToExpand.contains(node.value.getAbsolutePath()) && !node.isExpand) {
        expandNode(node)
      }
    }
  }

  /** 精准定位文件位置。通过路径逐级比对并展开父目录，最后滚动到目标。 */
  fun locateFileAndScroll(targetAbsolutePath: String) {
    post {
      expandPathOnce(targetAbsolutePath)
      val targetIndex =
          fileTreeAdapter.currentList.indexOfFirst {
            it.value.getAbsolutePath() == targetAbsolutePath
          }

      if (targetIndex != -1) {
        val lm = layoutManager as LinearLayoutManager
        val offset = (height / 2) // 居中显示
        lm.scrollToPositionWithOffset(targetIndex, offset)

        val targetNode = fileTreeAdapter.currentList[targetIndex]
        targetNode.isHighlighted = true
        fileTreeAdapter.notifyItemChanged(targetIndex)

        Handler(Looper.getMainLooper())
            .postDelayed(
                {
                  targetNode.isHighlighted = false
                  val currentPos = fileTreeAdapter.currentList.indexOf(targetNode)
                  if (currentPos != -1) {
                    fileTreeAdapter.notifyItemChanged(currentPos)
                  }
                },
                2500,
            )
      }
    }
  }

  private fun expandPathOnce(targetAbsolutePath: String) {
    val pathParts = targetAbsolutePath.split("/").filter { it.isNotEmpty() }
    if (pathParts.isEmpty()) return
    var currentNode: Node<FileObject>? =
        fileTreeAdapter.currentList.firstOrNull {
          it.value.getAbsolutePath() == rootFileObject.getAbsolutePath()
        }

    if (currentNode == null && !isRootNodeVisible) {
      currentNode =
          fileTreeAdapter.currentList.firstOrNull {
            targetAbsolutePath.startsWith(it.value.getAbsolutePath().trimEnd('/') + "/") ||
                it.value.getAbsolutePath() == targetAbsolutePath
          }
    }

    while (currentNode != null && currentNode.value.isDirectory()) {
      if (!currentNode.isExpand) {
        fileTreeAdapter.expandNode(currentNode)
      }
      val next =
          (currentNode.child ?: emptyList()).firstOrNull {
            targetAbsolutePath.startsWith(it.value.getAbsolutePath().trimEnd('/') + "/") ||
                it.value.getAbsolutePath() == targetAbsolutePath
          }
      currentNode = next
    }
  }

  private fun autoExpandUntilBranch(startNode: Node<FileObject>) {
    var current = startNode
    while (current.value.isDirectory()) {
      val children = Sorter.sort(current.value)
      if (children.size != 1) {
        break
      }
      val onlyChild = children.first()
      if (!onlyChild.value.isDirectory()) {
        break
      }
      val currentChildren = current.child ?: emptyList()
      if (currentChildren.any { it.value.getAbsolutePath() == onlyChild.value.getAbsolutePath() }) {
        val next = currentChildren.first {
          it.value.getAbsolutePath() == onlyChild.value.getAbsolutePath()
        }
        if (!next.isExpand) {
          fileTreeAdapter.expandNode(next)
        }
        current = next
      } else {
        fileTreeAdapter.expandNode(onlyChild)
        current = onlyChild
      }
    }
  }
}
