/*
 *  This file is part of AndroidIDE.
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
import com.catpuppyapp.puppygit.git.StashDto
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitStashBinding
import com.itsaky.androidide.projects.IProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Git Stash 管理页面。 */
class GitStashFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitStashBinding? = null
  private val binding
    get() = _binding!!

  private val stashItems = mutableListOf<StashDto>()
  private val adapter = StashAdapter(stashItems)
  private var selected: StashDto? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitStashBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      emitGitOperation("stash", "refresh")
      loadStashList()
    }

    addToolbarAction(R.drawable.ic_add_24, getString(R.string.stash)) {
      emitGitOperation("stash", "push")
      createStash()
    }

    addToolbarAction(R.drawable.ic_check_24, "Apply") {
      emitGitOperation("stash", "apply")
      applySelected(pop = false)
    }

    addToolbarAction(R.drawable.ic_call_split_24, "Pop") {
      emitGitOperation("stash", "pop")
      applySelected(pop = true)
    }

    addToolbarAction(R.drawable.ic_delete_sweep_24, getString(R.string.clear_all)) {
      emitGitOperation("stash", "clear_all")
      clearAllStash()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.rvStash.layoutManager = LinearLayoutManager(context)
    binding.rvStash.adapter = adapter
    loadStashList()
  }

  private fun loadStashList() {
    withRepo { repo ->
      val loaded = Libgit2Helper.stashList(repo, mutableListOf())
      stashItems.clear()
      stashItems.addAll(loaded)
      selected = selected?.let { old -> stashItems.firstOrNull { it.index == old.index } }
    }
  }

  private fun createStash() {
    withRepo { repo ->
      val (username, email) = Libgit2Helper.getGitUsernameAndEmail(repo)
      if (username.isBlank() || email.isBlank()) {
        throw RuntimeException("Please set git username and email first")
      }
      val settings = SettingsUtil.getSettingsSnapshot()
      val signature = Libgit2Helper.createSignature(username, email, settings)
      Libgit2Helper.stashSave(repo, signature, Libgit2Helper.stashGenMsg())
    }
  }

  private fun applySelected(pop: Boolean) {
    val item = selected
    if (item == null) {
      Toast.makeText(context, "Select a stash first", Toast.LENGTH_SHORT).show()
      return
    }

    withRepo { repo ->
      if (pop) {
        Libgit2Helper.stashPop(repo, item.index)
      } else {
        Libgit2Helper.stashApply(repo, item.index)
      }
    }
  }

  private fun clearAllStash() {
    withRepo { repo ->
      val all = Libgit2Helper.stashList(repo, mutableListOf())
      all.sortedByDescending { it.index }.forEach { Libgit2Helper.stashDrop(repo, it.index) }
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
          loadStashList()
          Toast.makeText(context, "Stash operation completed", Toast.LENGTH_SHORT).show()
        }
        ret.onFailure {
          Toast.makeText(context, it.localizedMessage ?: "Stash operation failed", Toast.LENGTH_LONG)
              .show()
        }
      }
    }
  }

  private inner class StashAdapter(private val data: List<StashDto>) :
      RecyclerView.Adapter<StashAdapter.VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
      val view =
          LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
      return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
      val item = data[position]
      val isSelected = selected?.index == item.index
      holder.title.text = if (isSelected) "✓ stash@{${item.index}}" else "stash@{${item.index}}"
      holder.subtitle.text = item.getCachedOneLineMsg()
      holder.itemView.setOnClickListener {
        selected = item
        notifyDataSetChanged()
      }
      holder.itemView.setOnLongClickListener {
        withRepo { repo -> Libgit2Helper.stashDrop(repo, item.index) }
        true
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
