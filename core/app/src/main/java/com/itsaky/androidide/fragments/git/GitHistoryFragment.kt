/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.fragments.git

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitHistoryBinding
import com.itsaky.androidide.projects.IProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Git 历史记录页面。 */
class GitHistoryFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitHistoryBinding? = null
  private val binding
    get() = _binding!!

  private val commits = mutableListOf<CommitRow>()
  private val adapter = CommitAdapter(commits)
  private var selectedCommit: CommitRow? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitHistoryBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_cloud_download_24, getString(R.string.fetch)) {
      emitGitOperation("history", "fetch_placeholder")
      Toast.makeText(context, "Fetch UI is pending backend binding", Toast.LENGTH_SHORT).show()
    }

    addToolbarAction(R.drawable.ic_filter_list_24, getString(R.string.filter)) {
      emitGitOperation("history", "filter_latest_200")
      loadCommits(limit = 200)
    }

    addToolbarAction(R.drawable.ic_content_copy_24, getString(R.string.copy_hash)) {
      emitGitOperation("history", "copy_hash")
      copySelectedHash()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.rvHistory.layoutManager = LinearLayoutManager(context)
    binding.rvHistory.adapter = adapter
    loadCommits(limit = 100)
  }

  private fun loadCommits(limit: Int) {
    withRepo { repo ->
      val headOid = repo.head()?.id() ?: return@withRepo
      val rw = Libgit2Helper.createRevwalk(repo, headOid) ?: return@withRepo
      val settings = SettingsUtil.getSettingsSnapshot()

      val list = mutableListOf<CommitRow>()
      var count = 0
      var next = rw.next()
      while (next != null && count < limit) {
        val dto = Libgit2Helper.getSingleCommitSimple(repo, repoId = "", commitOidStr = next.toString(), settings)
        list.add(
            CommitRow(
                hash = dto.oidStr,
                shortHash = dto.shortOidStr,
                subject = dto.shortMsg,
                author = dto.author,
            ))
        next = rw.next()
        count++
      }

      commits.clear()
      commits.addAll(list)
      selectedCommit = commits.firstOrNull()
    }
  }

  private fun copySelectedHash() {
    val commit = selectedCommit
    if (commit == null) {
      Toast.makeText(context, "Select a commit first", Toast.LENGTH_SHORT).show()
      return
    }

    val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("git-hash", commit.hash))
    Toast.makeText(context, "Copied ${commit.shortHash}", Toast.LENGTH_SHORT).show()
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
          Toast.makeText(context, "History loaded", Toast.LENGTH_SHORT).show()
        }
        ret.onFailure {
          Toast.makeText(context, it.localizedMessage ?: "Load history failed", Toast.LENGTH_LONG)
              .show()
        }
      }
    }
  }

  private data class CommitRow(
      val hash: String,
      val shortHash: String,
      val subject: String,
      val author: String,
  )

  private inner class CommitAdapter(private val data: List<CommitRow>) :
      RecyclerView.Adapter<CommitAdapter.VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
      val view =
          LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
      return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
      val item = data[position]
      val active = selectedCommit?.hash == item.hash
      holder.title.text = if (active) "✓ ${item.shortHash} ${item.subject}" else "${item.shortHash} ${item.subject}"
      holder.subtitle.text = item.author
      holder.itemView.setOnClickListener {
        selectedCommit = item
        notifyDataSetChanged()
      }
    }

    override fun getItemCount(): Int = data.size

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
