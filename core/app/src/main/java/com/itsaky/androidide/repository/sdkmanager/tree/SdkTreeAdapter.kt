package com.itsaky.androidide.repository.sdkmanager.tree

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.itsaky.androidide.R
import com.itsaky.androidide.repository.sdkmanager.models.InstallStatus
import com.itsaky.androidide.repository.sdkmanager.models.SdkTreeNode

/**
 * SDK 树形列表适配器。
 *
 * @author android_zero
 */
class SdkTreeAdapter(
    private val context: Context,
    private val onNodeCheckChanged: (SdkTreeNode) -> Unit
) : RecyclerView.Adapter<SdkTreeAdapter.ViewHolder>() {

    private val visibleNodes = mutableListOf<SdkTreeNode>()

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val indentSpace: Space = v.findViewById(R.id.indent_space)
        val ivExpand: ImageView = v.findViewById(R.id.iv_expand)
        val cbNode: MaterialCheckBox = v.findViewById(R.id.cb_node)
        val tvName: TextView = v.findViewById(R.id.tv_name)
        val tvApiLevel: TextView = v.findViewById(R.id.tv_api_level)
        val tvRevision: TextView = v.findViewById(R.id.tv_revision)
        val tvStatus: TextView = v.findViewById(R.id.tv_status)
    }

    fun submitList(rootNodes: List<SdkTreeNode>) {
        visibleNodes.clear()
        for (node in rootNodes) {
            visibleNodes.add(node)
            if (node.isExpanded) {
                addVisibleChildren(node)
            }
        }
        notifyDataSetChanged()
    }

    private fun addVisibleChildren(node: SdkTreeNode) {
        for (child in node.children) {
            visibleNodes.add(child)
            if (child.isExpanded) {
                addVisibleChildren(child)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_sdk_tree_node, parent, false)
        val holder = ViewHolder(view)

        // 展开/折叠 点击事件
        val expandClickListener = View.OnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val node = visibleNodes[pos]
                if (node.isGroup) {
                    if (node.isExpanded) {
                        collapseNode(node)
                    } else {
                        expandNode(node)
                    }
                }
            }
        }
        holder.itemView.setOnClickListener(expandClickListener)
        holder.ivExpand.setOnClickListener(expandClickListener)

        // 复选框 点击事件
        holder.cbNode.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val node = visibleNodes[pos]
                onNodeCheckChanged(node)
            }
        }

        return holder
    }

    private fun dpToPx(dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = visibleNodes[position]

        // 设置树形缩进 (每级缩进 20dp)
        val params = holder.indentSpace.layoutParams
        params.width = node.level * dpToPx(20f)
        holder.indentSpace.layoutParams = params

        // 基础文本
        holder.tvName.text = node.name
        holder.tvName.typeface = if (node.isGroup) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        holder.tvApiLevel.text = node.apiLevel
        holder.tvRevision.text = node.revision

        // 展开箭头状态
        if (node.isGroup) {
            holder.ivExpand.visibility = View.VISIBLE
            holder.ivExpand.setImageResource(if (node.isExpanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right)
        } else {
            holder.ivExpand.visibility = View.INVISIBLE
        }

        // 复选框状态 (支持三态)
        holder.cbNode.checkedState = when (node.checkedState) {
            androidx.compose.ui.state.ToggleableState.On -> MaterialCheckBox.STATE_CHECKED
            androidx.compose.ui.state.ToggleableState.Indeterminate -> MaterialCheckBox.STATE_INDETERMINATE
            androidx.compose.ui.state.ToggleableState.Off -> MaterialCheckBox.STATE_UNCHECKED
        }

        // Status 状态文本及颜色
        val statusText = when (node.status) {
            InstallStatus.NOT_INSTALLED -> "Not installed"
            InstallStatus.INSTALLED -> "Installed"
            InstallStatus.UPDATE_AVAILABLE -> "Update available"
        }
        holder.tvStatus.text = if (node.isGroup) "" else statusText
        if (node.status == InstallStatus.INSTALLED && !node.isGroup) {
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
        } else {
            holder.tvStatus.setTextColor(holder.tvRevision.currentTextColor) // Default secondary
        }
    }

    override fun getItemCount(): Int = visibleNodes.size

    private fun expandNode(node: SdkTreeNode) {
        node.isExpanded = true
        val index = visibleNodes.indexOf(node)
        var insertIndex = index + 1
        
        fun insertRecursive(parent: SdkTreeNode) {
            for (child in parent.children) {
                visibleNodes.add(insertIndex++, child)
                if (child.isExpanded) {
                    insertRecursive(child)
                }
            }
        }
        
        val prevSize = visibleNodes.size
        insertRecursive(node)
        val addedCount = visibleNodes.size - prevSize
        notifyItemRangeInserted(index + 1, addedCount)
        notifyItemChanged(index) // Update arrow
    }

    private fun collapseNode(node: SdkTreeNode) {
        node.isExpanded = false
        val index = visibleNodes.indexOf(node)
        
        var removeCount = 0
        // Find all descendants currently visible
        var nextIndex = index + 1
        while (nextIndex < visibleNodes.size && visibleNodes[nextIndex].level > node.level) {
            removeCount++
            nextIndex++
        }
        
        if (removeCount > 0) {
            visibleNodes.subList(index + 1, index + 1 + removeCount).clear()
            notifyItemRangeRemoved(index + 1, removeCount)
        }
        notifyItemChanged(index) // Update arrow
    }
}