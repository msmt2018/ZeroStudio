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

package com.itsaky.androidide.fragments.git

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.git24j.core.Branch
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitBranchesBinding
import com.itsaky.androidide.projects.IProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 分支管理页面。
 *
 * @author android_zero
 */
class GitBranchesFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitBranchesBinding? = null
  private val binding
    get() = _binding!!
  private val branchItems = mutableListOf<BranchItem>()
  private val adapter = BranchAdapter(branchItems)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitBranchesBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    // 实现 BaseGitPageFragment 的抽象方法
    // 添加特定于分支管理的按钮

    // 1. 刷新
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      emitGitOperation("branches", "refresh")
      loadBranches()
    }

    // 2. 新建分支
    addToolbarAction(R.drawable.ic_add_24, getString(R.string.new_branch)) {
      emitGitOperation("branches", "create_branch_dialog")
      Toast.makeText(context, "Use core:git create-branch dialog integration next", Toast.LENGTH_SHORT)
          .show()
    }

    // 3. 检出/切换 (Checkout)
    addToolbarAction(R.drawable.ic_call_split_24, getString(R.string.checkout)) {
      emitGitOperation("branches", "checkout_selected")
      Toast.makeText(context, "Select a branch to checkout (next step)", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setupRecyclerView()
    loadBranches()
  }

  private fun setupRecyclerView() {
    binding.rvBranches.layoutManager = LinearLayoutManager(context)
    binding.rvBranches.adapter = adapter
  }

  private fun loadBranches() {
    val projectDir = IProjectManager.getInstance().projectDirPath
    if (projectDir.isNullOrBlank()) {
      Toast.makeText(context, "No opened project", Toast.LENGTH_SHORT).show()
      return
    }

    viewLifecycleOwner.lifecycleScope.launch {
      val result =
          withContext(Dispatchers.IO) {
            runCatching {
              Repository.open(projectDir).use { repo ->
                val list = mutableListOf<BranchItem>()
                val iterator = Branch.Iterator.create(repo, Branch.BranchType.ALL)
                while (true) {
                  val entry = iterator.next() ?: break
                  val ref = entry.key
                  val name = Branch.name(ref).orEmpty()
                  val isHead = Branch.isHead(ref)
                  list.add(BranchItem(name = name, type = entry.value.name, isHead = isHead))
                  ref.close()
                }
                list.sortedBy { it.name.lowercase() }
              }
            }
          }

      result.onSuccess { items ->
        branchItems.clear()
        branchItems.addAll(items)
        adapter.notifyDataSetChanged()
        Toast.makeText(context, "Loaded ${items.size} branches", Toast.LENGTH_SHORT).show()
      }
      result.onFailure { err ->
        Toast.makeText(context, err.localizedMessage ?: "Failed to load branches", Toast.LENGTH_LONG)
            .show()
      }
    }
  }

  private data class BranchItem(val name: String, val type: String, val isHead: Boolean)

  private inner class BranchAdapter(private val data: List<BranchItem>) :
      RecyclerView.Adapter<BranchAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
      val view =
          LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
      return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
      val item = data[position]
      holder.title.text = if (item.isHead) "✓ ${item.name}" else item.name
      holder.subtitle.text = item.type
      holder.itemView.setOnClickListener {
        emitGitOperation("branches", "select_${item.name}")
        Toast.makeText(context, "Selected ${item.name}", Toast.LENGTH_SHORT).show()
      }
    }

    override fun getItemCount() = data.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
      val title: TextView = view.findViewById(android.R.id.text1)
      val subtitle: TextView = view.findViewById(android.R.id.text2)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
