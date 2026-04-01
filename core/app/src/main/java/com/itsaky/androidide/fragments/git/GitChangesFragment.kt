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
import com.catpuppyapp.puppygit.git.StatusTypeEntrySaver
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitChangesBinding
import com.itsaky.androidide.projects.IProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 变更与提交页面。 */
class GitChangesFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitChangesBinding? = null
  private val binding
    get() = _binding!!

  private val rows = mutableListOf<ChangeRow>()
  private val adapter = ChangeAdapter(rows)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitChangesBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_check_24, getString(R.string.commit)) {
      emitGitOperation("changes", "commit")
      commitChanges()
    }

    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      emitGitOperation("changes", "refresh")
      loadChanges()
    }

    addToolbarAction(R.drawable.ic_select_all_24, getString(R.string.stage_all)) {
      emitGitOperation("changes", "stage_all")
      stageAll()
    }

    addToolbarAction(R.drawable.ic_remove_circle_outline_24, getString(R.string.unstage)) {
      emitGitOperation("changes", "unstage_all")
      unstageAll()
    }

    addToolbarAction(R.drawable.ic_delete_sweep_24, getString(R.string.revert)) {
      emitGitOperation("changes", "discard_all")
      discardAll()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.rvChanges.layoutManager = LinearLayoutManager(context)
    binding.rvChanges.adapter = adapter
    loadChanges()
  }

  private fun loadChanges() {
    val projectDir = IProjectManager.getInstance().projectDirPath
    if (projectDir.isNullOrBlank()) {
      Toast.makeText(context, "No opened project", Toast.LENGTH_SHORT).show()
      return
    }

    viewLifecycleOwner.lifecycleScope.launch {
      val ret =
          withContext(Dispatchers.IO) {
            runCatching {
              Repository.open(projectDir).use { repo ->
                val statusList = Libgit2Helper.getWorkdirStatusList(repo)
                val unstaged = Libgit2Helper.getWorktreeChangeList(repo, statusList, repoId = "")
                val (_, staged) =
                    Libgit2Helper.checkIndexIsEmptyAndGetIndexList(
                        repo = repo,
                        repoId = "",
                        onlyCheckEmpty = false,
                    )

                buildRows(staged.orEmpty(), unstaged)
              }
            }
          }

      ret.onSuccess {
        rows.clear()
        rows.addAll(it)
        adapter.notifyDataSetChanged()
      }
      ret.onFailure {
        Toast.makeText(context, it.localizedMessage ?: "Failed to load changes", Toast.LENGTH_LONG)
            .show()
      }
    }
  }

  private fun stageAll() {
    withRepo { repo ->
      val ret = Libgit2Helper.stageAll(repo, repoId = "")
      if (ret.hasError()) {
        throw RuntimeException(ret.msg)
      }
    }
  }

  private fun unstageAll() {
    withRepo { repo ->
      val (_, staged) =
          Libgit2Helper.checkIndexIsEmptyAndGetIndexList(repo = repo, repoId = "", onlyCheckEmpty = false)
      val paths = staged.orEmpty().map { it.relativePathUnderRepo }
      if (paths.isEmpty()) {
        throw RuntimeException("No staged file")
      }
      Libgit2Helper.unStageItems(repo, paths)
    }
  }

  private fun discardAll() {
    withRepo { repo ->
      val ret = Libgit2Helper.resetHardToHead(repo)
      if (ret.hasError()) {
        throw RuntimeException(ret.msg)
      }
    }
  }

  private fun commitChanges() {
    val msg = binding.etCommitMessage.text.toString().trim()
    if (msg.isBlank()) {
      Toast.makeText(context, getString(R.string.please_input_commit_msg), Toast.LENGTH_SHORT).show()
      return
    }

    withRepo { repo ->
      val (username, email) = Libgit2Helper.getGitUsernameAndEmail(repo)
      if (username.isBlank() || email.isBlank()) {
        throw RuntimeException("Please set git username and email first")
      }

      val settings = SettingsUtil.getSettingsSnapshot()
      val ret =
          Libgit2Helper.createCommit(
              repo = repo,
              msg = msg,
              username = username,
              email = email,
              amend = binding.cbAmend.isChecked,
              cleanRepoStateIfSuccess = true,
              settings = settings,
          )
      if (ret.hasError()) {
        throw RuntimeException(ret.msg)
      }
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
          loadChanges()
          Toast.makeText(context, "Git operation completed", Toast.LENGTH_SHORT).show()
        }
        ret.onFailure {
          Toast.makeText(context, it.localizedMessage ?: "Git operation failed", Toast.LENGTH_LONG)
              .show()
        }
      }
    }
  }

  private fun buildRows(
      staged: List<StatusTypeEntrySaver>,
      unstaged: List<StatusTypeEntrySaver>,
  ): List<ChangeRow> {
    val list = mutableListOf<ChangeRow>()
    list.add(ChangeRow.Header("Staged (${staged.size})"))
    list.addAll(staged.map { ChangeRow.Entry(it, true) })
    list.add(ChangeRow.Header("Unstaged (${unstaged.size})"))
    list.addAll(unstaged.map { ChangeRow.Entry(it, false) })
    return list
  }

  private sealed class ChangeRow {
    data class Header(val title: String) : ChangeRow()

    data class Entry(val item: StatusTypeEntrySaver, val staged: Boolean) : ChangeRow()
  }

  private inner class ChangeAdapter(private val data: List<ChangeRow>) :
      RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int = if (data[position] is ChangeRow.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      return if (viewType == 0) {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
        HeaderVH(view)
      } else {
        val view =
            LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
        ItemVH(view)
      }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      when (val row = data[position]) {
        is ChangeRow.Header -> (holder as HeaderVH).title.text = row.title
        is ChangeRow.Entry -> (holder as ItemVH).bind(row)
      }
    }

    override fun getItemCount(): Int = data.size

    private inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
      val title: TextView = view.findViewById(android.R.id.text1)
    }

    private inner class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
      private val title: TextView = view.findViewById(android.R.id.text1)
      private val subtitle: TextView = view.findViewById(android.R.id.text2)

      fun bind(row: ChangeRow.Entry) {
        val item = row.item
        title.text = item.relativePathUnderRepo
        val state = if (row.staged) "Staged" else "Unstaged"
        subtitle.text = "$state · ${item.changeType.orEmpty()}"

        itemView.setOnClickListener {
          if (row.staged) {
            withRepo { repo -> Libgit2Helper.unStageItems(repo, listOf(item.relativePathUnderRepo)) }
          } else {
            withRepo { repo -> Libgit2Helper.stageStatusEntryAndWriteToDisk(repo, listOf(item)) }
          }
        }
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
