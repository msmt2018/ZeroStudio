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

package com.itsaky.androidide.fragments.git.menu

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.itsaky.androidide.R
import java.util.ArrayList
import java.util.Locale

/**
 * 分支切换弹出窗口管理器。
 *
 * @author android_zero
 */
class GitBranchPopupManager(
    private val context: Context,
    private val onBranchSelected: (String) -> Unit,
) {

  private var popupWindow: PopupWindow? = null
  private var adapter: BranchAdapter? = null

  // 模拟数据
  private val allBranches =
      listOf(
          BranchModel("feature/ui-v2", isCurrent = true, isLocal = true),
          BranchModel("main", isCurrent = false, isLocal = true),
          BranchModel("dev", isCurrent = false, isLocal = true),
          BranchModel("origin/main", isCurrent = false, isLocal = false),
          BranchModel("origin/feature/ui-v2", isCurrent = false, isLocal = false),
      )

  fun show(anchor: View) {
    val view = LayoutInflater.from(context).inflate(R.layout.layout_git_branch_popup, null)

    val rvBranches = view.findViewById<RecyclerView>(R.id.rv_branch_list)
    val etSearch = view.findViewById<EditText>(R.id.et_search_branch)
    val btnNew = view.findViewById<TextView>(R.id.btn_new_branch)

    adapter =
        BranchAdapter(allBranches) { branch ->
          onBranchSelected(branch.name)
          popupWindow?.dismiss()
        }
    rvBranches.layoutManager = LinearLayoutManager(context)
    rvBranches.adapter = adapter

    etSearch.addTextChangedListener(
        object : TextWatcher {
          override fun afterTextChanged(s: Editable?) {}

          override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

          override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            adapter?.filter(s.toString())
          }
        }
    )

    btnNew.setOnClickListener {
      Toast.makeText(context, "Use Branches page to create branch", Toast.LENGTH_SHORT).show()
      popupWindow?.dismiss()
    }

    popupWindow =
        PopupWindow(
                view,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            )
            .apply {
              setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
              elevation = 16f
              isOutsideTouchable = true
            }

    popupWindow?.showAsDropDown(anchor, 0, 12)
  }

  data class BranchModel(val name: String, val isCurrent: Boolean, val isLocal: Boolean)

  private inner class BranchAdapter(
      private val originalList: List<BranchModel>,
      private val onClick: (BranchModel) -> Unit,
  ) : RecyclerView.Adapter<BranchAdapter.ViewHolder>() {

    private var displayList: List<BranchModel> = ArrayList(originalList)

    fun filter(query: String) {
      val lowerQuery = query.lowercase(Locale.getDefault())
      displayList =
          if (lowerQuery.isEmpty()) {
            originalList
          } else {
            originalList.filter { it.name.lowercase(Locale.getDefault()).contains(lowerQuery) }
          }
      notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val v =
          LayoutInflater.from(parent.context).inflate(R.layout.item_git_branch_popup, parent, false)
      return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val item = displayList[position]

      holder.tvName.text = item.name

      if (item.isCurrent) {
        // 选中状态：使用 Secondary Container 颜色
        val activeBg =
            MaterialColors.getColor(
                holder.itemView,
                com.google.android.material.R.attr.colorSecondaryContainer,
            )
        val activeText =
            MaterialColors.getColor(
                holder.itemView,
                com.google.android.material.R.attr.colorOnSecondaryContainer,
            )

        holder.container.setBackgroundColor(activeBg)
        holder.ivCheck.visibility = View.VISIBLE
        holder.tvName.setTextColor(activeText)
        holder.ivCheck.setColorFilter(activeText)
      } else {
        // 未选中：直接设为透明背景，避免 ResourceNotFoundException
        holder.container.setBackgroundColor(Color.TRANSPARENT)

        // 如果需要点击涟漪，可以在 XML 布局中给 container 设置 android:background="?attr/selectableItemBackground"

        val defaultText =
            MaterialColors.getColor(
                holder.itemView,
                com.google.android.material.R.attr.colorOnSurface,
            )

        holder.ivCheck.visibility = View.GONE
        holder.tvName.setTextColor(defaultText)
      }

      // 分组 Header 逻辑
      val showHeader =
          if (position == 0) {
            true
          } else {
            item.isLocal != displayList[position - 1].isLocal
          }

      if (showHeader) {
        holder.tvHeader.visibility = View.VISIBLE
        holder.tvHeader.text = if (item.isLocal) "LOCAL" else "REMOTE"
      } else {
        holder.tvHeader.visibility = View.GONE
      }

      holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = displayList.size

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
      val tvHeader: TextView = v.findViewById(R.id.tv_header)
      val container: View = v.findViewById(R.id.container_item)
      val tvName: TextView = v.findViewById(R.id.tv_branch_name)
      val ivCheck: ImageView = v.findViewById(R.id.iv_check)
    }
  }
}
