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
import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.git.StatusTypeEntrySaver
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.projects.IProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 冲突解决页面。 显示当前存在冲突的文件列表。 */
class GitConflictsFragment : BaseGitPageFragment() {

  private val conflicts = mutableListOf<StatusTypeEntrySaver>()
  private val adapter = ConflictAdapter(conflicts)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return inflater.inflate(R.layout.fragment_git_branches, container, false)
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_download_24, getString(R.string.accept_theirs)) {
      emitGitOperation("conflicts", "accept_theirs_all")
      acceptAll(true)
    }

    addToolbarAction(R.drawable.ic_arrow_upward_24, getString(R.string.accept_ours)) {
      emitGitOperation("conflicts", "accept_ours_all")
      acceptAll(false)
    }

    addToolbarAction(R.drawable.ic_warning_24, getString(R.string.abort_merge)) {
      emitGitOperation("conflicts", "abort_merge")
      abortMerge()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    view.findViewById<RecyclerView>(R.id.rv_branches)?.apply {
      layoutManager = LinearLayoutManager(context)
      adapter = this@GitConflictsFragment.adapter
    }
    loadConflicts()
  }

  private fun loadConflicts() {
    withRepo {
      val list =
          Libgit2Helper.getWorktreeChangeList(it, Libgit2Helper.getWorkdirStatusList(it), "")
              .filter { row -> row.changeType == Cons.gitStatusConflict }
      conflicts.clear()
      conflicts.addAll(list)
    }
  }

  private fun acceptAll(acceptTheirs: Boolean) {
    withRepo { repo ->
      val pathSpec = conflicts.map { it.relativePathUnderRepo }
      val ret = Libgit2Helper.mergeAccept(repo, pathSpec, acceptTheirs)
      if (ret.hasError()) {
        throw RuntimeException(ret.msg)
      }
    }
  }

  private fun abortMerge() {
    withRepo { repo ->
      val ret = Libgit2Helper.resetHardToHead(repo)
      if (ret.hasError()) {
        throw RuntimeException(ret.msg)
      }
      Libgit2Helper.cleanRepoState(repo, cancelIfHasConflicts = false)
    }
  }

  private fun withRepo(action: (Repository) -> Unit) {
    val projectDir = IProjectManager.getInstance().projectDirPath
    if (projectDir.isNullOrBlank()) {
      Toast.makeText(context, "No opened project", Toast.LENGTH_SHORT).show()
      return
    }

    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching { Repository.open(projectDir).use(action) }
      withContext(Dispatchers.Main) {
        ret.onSuccess {
          adapter.notifyDataSetChanged()
          Toast.makeText(context, "Conflict operation completed", Toast.LENGTH_SHORT).show()
          loadConflicts()
        }
        ret.onFailure {
          Toast.makeText(context, it.localizedMessage ?: "Conflict operation failed", Toast.LENGTH_LONG)
              .show()
        }
      }
    }
  }

  private inner class ConflictAdapter(private val data: List<StatusTypeEntrySaver>) :
      RecyclerView.Adapter<ConflictAdapter.VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
      val view =
          LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
      return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
      val item = data[position]
      holder.title.text = item.relativePathUnderRepo
      holder.subtitle.text = item.changeType.orEmpty()
    }

    override fun getItemCount() = data.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
      val title: TextView = view.findViewById(android.R.id.text1)
      val subtitle: TextView = view.findViewById(android.R.id.text2)
    }
  }
}
