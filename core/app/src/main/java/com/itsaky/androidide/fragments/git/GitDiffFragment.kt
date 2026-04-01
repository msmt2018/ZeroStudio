/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.fragments.git

import android.graphics.Color
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
import com.itsaky.androidide.databinding.FragmentGitDiffBinding
import com.itsaky.androidide.projects.IProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Diff 查看器页面。 */
class GitDiffFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitDiffBinding? = null
  private val binding
    get() = _binding!!

  private val changedFiles = mutableListOf<StatusTypeEntrySaver>()
  private var currentIndex = 0
  private val lines = mutableListOf<DiffLine>()
  private val adapter = DiffAdapter(lines)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitDiffBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_add_24, getString(R.string.stage)) {
      stageCurrentFile()
    }

    addToolbarAction(R.drawable.ic_undo_24, getString(R.string.revert)) {
      revertCurrentFile()
    }

    addToolbarAction(R.drawable.ic_chevron_left_24, getString(R.string.previous_page)) {
      navigateDiff(-1)
    }

    addToolbarAction(R.drawable.ic_chevron_right_24, getString(R.string.next_page)) {
      navigateDiff(1)
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.rvDiffLines.layoutManager = LinearLayoutManager(context)
    binding.rvDiffLines.adapter = adapter
    reloadChangedFilesAndDiff()
  }

  private fun reloadChangedFilesAndDiff() {
    withRepo { repo ->
      val statusList = Libgit2Helper.getWorkdirStatusList(repo)
      val loaded = Libgit2Helper.getWorktreeChangeList(repo, statusList, repoId = "")
      changedFiles.clear()
      changedFiles.addAll(loaded)
      currentIndex = currentIndex.coerceIn(0, (changedFiles.size - 1).coerceAtLeast(0))
      loadCurrentDiff(repo)
    }
  }

  private fun loadCurrentDiff(repo: Repository) {
    if (changedFiles.isEmpty()) {
      lines.clear()
      lines.add(DiffLine(-1, -1, "No changed files", DiffType.HUNK_HEADER))
      return
    }

    val file = changedFiles[currentIndex]
    val diffItem =
        Libgit2Helper.getSingleDiffItem(
            repo = repo,
            relativePathUnderRepo = file.relativePathUnderRepo,
            fromTo = Cons.gitDiffFromIndexToWorktree,
            loadChannel = null,
            checkChannelLinesLimit = 200,
            checkChannelSizeLimit = 1024 * 64,
        )

    val rendered = mutableListOf<DiffLine>()
    rendered.add(DiffLine(-1, -1, "File: ${file.relativePathUnderRepo}", DiffType.HUNK_HEADER))
    diffItem.hunks.forEach { hunk ->
      rendered.add(DiffLine(-1, -1, hunk.hunk.cachedNoLineBreakHeader(), DiffType.HUNK_HEADER))
      hunk.lines.forEach { ln ->
        val type =
            when {
              ln.originType.contains("ADD", ignoreCase = true) -> DiffType.ADD
              ln.originType.contains("DEL", ignoreCase = true) -> DiffType.DELETE
              else -> DiffType.CONTEXT
            }
        rendered.add(
            DiffLine(
                oldLine = ln.oldLineNum,
                newLine = ln.newLineNum,
                content = ln.getContentNoLineBreak(),
                type = type,
            ))
      }
    }

    lines.clear()
    lines.addAll(rendered)
  }

  private fun stageCurrentFile() {
    val target = changedFiles.getOrNull(currentIndex) ?: return
    withRepo { repo -> Libgit2Helper.stageStatusEntryAndWriteToDisk(repo, listOf(target)) }
  }

  private fun revertCurrentFile() {
    val target = changedFiles.getOrNull(currentIndex) ?: return
    withRepo { repo ->
      Libgit2Helper.revertFilesToIndexVersion(repo, listOf(target.relativePathUnderRepo), force = true)
      if (target.changeType == Cons.gitStatusUntracked) {
        Libgit2Helper.rmUntrackedFiles(listOf(target.canonicalPath))
      }
    }
  }

  private fun navigateDiff(delta: Int) {
    if (changedFiles.isEmpty()) {
      Toast.makeText(context, "No changed files", Toast.LENGTH_SHORT).show()
      return
    }
    currentIndex = (currentIndex + delta).coerceIn(0, changedFiles.lastIndex)
    withRepo { repo -> loadCurrentDiff(repo) }
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
        ret.onSuccess { adapter.notifyDataSetChanged() }
        ret.onFailure {
          Toast.makeText(context, it.localizedMessage ?: "Diff operation failed", Toast.LENGTH_LONG)
              .show()
        }
      }
    }
  }

  enum class DiffType {
    ADD,
    DELETE,
    CONTEXT,
    HUNK_HEADER,
  }

  data class DiffLine(val oldLine: Int, val newLine: Int, val content: String, val type: DiffType)

  inner class DiffAdapter(private val data: List<DiffLine>) :
      RecyclerView.Adapter<DiffAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val v = LayoutInflater.from(parent.context).inflate(R.layout.item_git_diff_line, parent, false)
      return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val item = data[position]
      holder.tvContent.text = item.content
      holder.tvLineOld.text = if (item.oldLine > 0) item.oldLine.toString() else ""
      holder.tvLineNew.text = if (item.newLine > 0) item.newLine.toString() else ""

      when (item.type) {
        DiffType.ADD -> {
          holder.itemView.setBackgroundColor(Color.parseColor("#1A4CAF50"))
          holder.tvContent.setTextColor(Color.parseColor("#A5D6A7"))
        }
        DiffType.DELETE -> {
          holder.itemView.setBackgroundColor(Color.parseColor("#1AF44336"))
          holder.tvContent.setTextColor(Color.parseColor("#EF9A9A"))
        }
        DiffType.HUNK_HEADER -> {
          holder.itemView.setBackgroundColor(Color.parseColor("#2C2C2C"))
          holder.tvContent.setTextColor(Color.parseColor("#90CAF9"))
        }
        else -> {
          holder.itemView.setBackgroundColor(Color.TRANSPARENT)
          holder.tvContent.setTextColor(Color.parseColor("#C4C4C4"))
        }
      }
    }

    override fun getItemCount() = data.size

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
      val tvLineOld: TextView = v.findViewById(R.id.tv_line_old)
      val tvLineNew: TextView = v.findViewById(R.id.tv_line_new)
      val tvContent: TextView = v.findViewById(R.id.tv_content)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
