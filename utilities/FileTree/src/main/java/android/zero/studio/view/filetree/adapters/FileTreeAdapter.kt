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
package android.zero.studio.view.filetree.adapters

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.zero.studio.view.filetree.R
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileIconProvider
import android.zero.studio.view.filetree.interfaces.FileItemTrailingProvider
import android.zero.studio.view.filetree.interfaces.FileLongClickListener
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.model.TreeViewModel
import android.zero.studio.view.filetree.util.Sorter
import android.zero.studio.view.filetree.widget.FileTree
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
  val expandView: ImageView = v.findViewById(R.id.expand)
  val fileView: ImageView = v.findViewById(R.id.file_view)
  val textView: TextView = v.findViewById(R.id.text_view)
  val trailingView: ImageView = v.findViewById(R.id.trailing_view)
  val rootContainer: View = v
}

class NodeDiffCallback : DiffUtil.ItemCallback<Node<FileObject>>() {
  override fun areItemsTheSame(oldItem: Node<FileObject>, newItem: Node<FileObject>): Boolean {
    return oldItem.value.getAbsolutePath() == newItem.value.getAbsolutePath()
  }

  override fun areContentsTheSame(oldItem: Node<FileObject>, newItem: Node<FileObject>): Boolean {
    return oldItem == newItem && oldItem.isHighlighted == newItem.isHighlighted
  }
}

class FileTreeAdapter(private val context: Context, val fileTree: FileTree) :
    ListAdapter<Node<FileObject>, ViewHolder>(NodeDiffCallback()) {

  var onClickListener: FileClickListener? = null
  var onLongClickListener: FileLongClickListener? = null
  var iconProvider: FileIconProvider? = null
  var trailingProvider: FileItemTrailingProvider? = null

  private var animator = fileTree.itemAnimator

  private val highlightColor: Int by lazy {
    val typedValue = TypedValue()
    if (context.theme.resolveAttribute(android.R.attr.colorControlHighlight, typedValue, true)) {
      typedValue.data
    } else {
      Color.parseColor("#40888888")
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view: View =
        LayoutInflater.from(context).inflate(R.layout.recycler_view_item, parent, false)
    val holder = ViewHolder(view)

    val clickListener = View.OnClickListener {
      val adapterPosition = holder.adapterPosition
      if (adapterPosition != RecyclerView.NO_POSITION) {
        val clickedNode = getItem(adapterPosition)

        if (clickedNode.value.isDirectory()) {
          if (!clickedNode.isExpand) {
            fileTree.itemAnimator = animator
            expandNode(clickedNode)
          } else {
            fileTree.itemAnimator = null
            collapseNode(clickedNode)
          }
          notifyItemChanged(adapterPosition)
        }
        onClickListener?.onClick(clickedNode)
      }
    }

    holder.itemView.setOnClickListener(clickListener)

    holder.itemView.setOnLongClickListener {
      val adapterPosition = holder.adapterPosition
      if (adapterPosition != RecyclerView.NO_POSITION) {
        val clickedNode = getItem(adapterPosition)
        onLongClickListener?.onLongClick(clickedNode)
      }
      true
    }

    holder.expandView.setOnClickListener(clickListener)
    holder.fileView.setPadding(0, 0, 0, 0)

    holder.trailingView.setOnClickListener {
      val adapterPosition = holder.adapterPosition
      if (adapterPosition != RecyclerView.NO_POSITION) {
        val clickedNode = getItem(adapterPosition)
        trailingProvider?.onTrailingClick(clickedNode)
      }
    }
    return holder
  }

  private fun dpToPx(dpValue: Float): Int {
    val scale: Float = context.resources.displayMetrics.density
    return (dpValue * scale + 0.5f).toInt()
  }

  @SuppressLint("SetTextI18n")
  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val node = getItem(position)
    val isDir = node.value.isDirectory()
    val expandView = holder.expandView
    val fileView = holder.fileView

    // Reset padding and margins to avoid accumulation
    holder.itemView.setPadding(0, 0, 0, 0)
    val layoutParams = fileView.layoutParams as ViewGroup.MarginLayoutParams
    layoutParams.setMargins(0, 0, 0, 0)
    fileView.layoutParams = layoutParams

    // Set padding based on node level
    holder.itemView.minimumWidth = max(fileTree.width, fileTree.measuredWidth)
    holder.itemView.setPadding(node.level * dpToPx(17f), dpToPx(5f), dpToPx(8f), dpToPx(5f))
    fileView.setImageDrawable(iconProvider?.getIcon(node))

    val icChevronRight = iconProvider?.getChevronRight()

    if (isDir) {
      expandView.visibility = View.VISIBLE
      if (!node.isExpand) {
        expandView.setImageDrawable(icChevronRight)
      } else {
        expandView.setImageDrawable(iconProvider?.getExpandMore())
      }
    } else {
      layoutParams.setMargins(icChevronRight!!.intrinsicWidth + dpToPx(10f), 0, 0, 0)
      fileView.layoutParams = layoutParams
      expandView.visibility = View.GONE
    }

    holder.textView.text = node.value.getName()

    // Render the optional trailing widget (e.g. info badge)
    val trailing = trailingProvider?.getTrailingDrawable(node)
    if (trailing != null) {
      holder.trailingView.visibility = View.VISIBLE
      holder.trailingView.setImageDrawable(trailing)
    } else {
      holder.trailingView.visibility = View.GONE
      holder.trailingView.setImageDrawable(null)
    }

    // 处理定位高亮效果 (闪烁动画)
    if (node.isHighlighted) {
      val animator =
          ObjectAnimator.ofObject(
              holder.rootContainer,
              "backgroundColor",
              ArgbEvaluator(),
              Color.TRANSPARENT,
              highlightColor,
              Color.TRANSPARENT,
          )
      animator.duration = 1200 // 1.2s 闪烁一次
      animator.repeatCount = 1
      animator.start()
    } else {
      holder.rootContainer.setBackgroundColor(Color.TRANSPARENT)
    }
  }

  fun expandNode(clickedNode: Node<FileObject>) {
    val tempData = currentList.toMutableList()
    val index = tempData.indexOf(clickedNode)
    val children = Sorter.sort(clickedNode.value)
    tempData.addAll(index + 1, children)
    TreeViewModel.add(clickedNode, children)
    clickedNode.isExpand = true
    submitList(tempData)
  }

  private fun collapseNode(clickedNode: Node<FileObject>) {
    val tempData = currentList.toMutableList()
    val children = TreeViewModel.getChildren(clickedNode)
    tempData.removeAll(children.toSet())
    TreeViewModel.remove(clickedNode, clickedNode.child)
    clickedNode.isExpand = false
    submitList(tempData)
  }
}
