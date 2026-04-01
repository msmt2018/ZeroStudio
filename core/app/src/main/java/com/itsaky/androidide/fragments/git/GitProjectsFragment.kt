package com.itsaky.androidide.fragments.git

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitProjectsBinding
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.fragments.git.function.ZeroCloneDialogBottomSheetFragment
import com.itsaky.androidide.fragments.git.menu.GitBranchPopupManager
import com.itsaky.androidide.fragments.git.tree.ListProjectFilesRequestEvent
import com.itsaky.androidide.interfaces.IEditorHandler
import com.itsaky.androidide.projects.IProjectManager
import com.rk.filetree.interfaces.FileClickListener
import com.rk.filetree.interfaces.FileLongClickListener
import com.rk.filetree.interfaces.FileObject
import com.rk.filetree.model.Node
import com.rk.filetree.provider.file
import com.rk.filetree.widget.FileTree
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class GitProjectsFragment : BaseGitPageFragment(), FileClickListener, FileLongClickListener {

  private var _binding: FragmentGitProjectsBinding? = null
  private val binding
    get() = _binding!!

  private var fileTreeView: FileTree? = null
  private var loadingJob: Job? = null
  private var tvCurrentBranch: android.widget.TextView? = null
  private var branchPopupManager: GitBranchPopupManager? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitProjectsBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onStart() {
    super.onStart()
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
  }

  override fun onStop() {
    super.onStop()
    if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
  }

  override fun setupToolbar() {
    val ctx = context ?: return

    val branchView = LayoutInflater.from(ctx).inflate(R.layout.item_git_toolbar_branch, null)
    tvCurrentBranch = branchView.findViewById(R.id.tv_current_branch)
    updateCurrentBranchName("main")
    tvCurrentBranch?.setOnClickListener {
      if (branchPopupManager == null) {
        branchPopupManager = GitBranchPopupManager(ctx) { name -> updateCurrentBranchName(name) }
      }
      branchPopupManager?.show(it)
    }
    addToolbarCustomView(branchView)

    addToolbarAction(R.drawable.ic_target_positioning_24dp, "Locate File") { locateCurrentFile() }

    addToolbarAction(R.drawable.ic_refresh_file_24dp, getString(R.string.refresh)) { refreshFileTree() }

    addToolbarAction(R.drawable.ic_git_clone_24dp, getString(R.string.git_clone)) {
      ZeroCloneDialogBottomSheetFragment.newInstance(repoId = "")
          .show(childFragmentManager, "GitProjectsCloneBottomSheet")
    }
    addToolbarAction(R.drawable.ic_arrow_downward_24, getString(R.string.pull)) {
      Toast.makeText(context, R.string.pull, Toast.LENGTH_SHORT).show()
    }
    addToolbarAction(R.drawable.ic_arrow_upward_24, getString(R.string.push)) {
      Toast.makeText(context, R.string.push, Toast.LENGTH_SHORT).show()
    }
  }

  private fun updateCurrentBranchName(name: String) {
    tvCurrentBranch?.text = name
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    view.post { listProjectFiles() }
  }

  private fun refreshFileTree() {
    listProjectFiles()
  }

  private fun locateCurrentFile() {
    val currentFile = IEditorHandler.getEditorFile()
    if (currentFile == null || !currentFile.exists()) {
      Toast.makeText(context, "No active file", Toast.LENGTH_SHORT).show()
      return
    }
    Toast.makeText(context, "Located: ${currentFile.name}", Toast.LENGTH_SHORT).show()
  }

  private fun listProjectFiles() {
    if (loadingJob?.isActive == true) return

    loadingJob =
        CoroutineScope(Dispatchers.Main).launch {
          setLoading(true)
          val root =
              withContext(Dispatchers.IO) {
                IProjectManager.getInstance().projectDirPath?.takeIf { it.isNotBlank() }?.let(::File)
              }

          if (root == null || !root.exists()) {
            binding.treeContainer.removeAllViews()
            binding.tvEmpty.isVisible = true
            binding.tvEmpty.text = "No project opened"
            setLoading(false)
            return@launch
          }

          binding.tvEmpty.isVisible = false
          setupFileTree(requireContext(), root)
          setLoading(false)
        }
  }

  private fun setupFileTree(ctx: Context, projectRoot: File) {
    val tree =
        (fileTreeView ?: FileTree(ctx).also { fileTreeView = it }).apply {
          setOnFileClickListener(this@GitProjectsFragment)
          setOnFileLongClickListener(this@GitProjectsFragment)
          loadFiles(file(projectRoot), true)
        }

    if (tree.parent == null) {
      binding.treeContainer.removeAllViews()
      binding.treeContainer.addView(
          tree,
          ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
          ),
      )
    }
  }

  private fun setLoading(loading: Boolean) {
    binding.loading.isVisible = loading
    binding.horizontalScroll.isVisible = !loading
  }

  override fun onClick(node: Node<FileObject>) {
    val target = (node.value as? file)?.getNativeFile() ?: return
    if (target.isFile) {
      EventBus.getDefault().post(FileClickEvent(target))
    }
  }

  override fun onLongClick(node: Node<FileObject>) {
    val target = (node.value as? file)?.getNativeFile() ?: return
    EventBus.getDefault().post(FileLongClickEvent(target))
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onListProjectFilesRequest(event: ListProjectFilesRequestEvent?) {
    listProjectFiles()
  }

  override fun onDestroyView() {
    loadingJob?.cancel()
    loadingJob = null
    fileTreeView = null
    _binding = null
    super.onDestroyView()
  }
}
