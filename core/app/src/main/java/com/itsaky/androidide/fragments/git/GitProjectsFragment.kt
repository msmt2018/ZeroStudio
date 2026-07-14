package com.itsaky.androidide.fragments.git

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileLongClickListener
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.provider.file
import android.zero.studio.view.filetree.widget.FileTree
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.databinding.FragmentGitProjectsBinding
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.fragments.git.menu.GitBranchPopupManager
import com.itsaky.androidide.fragments.git.tree.ListProjectFilesRequestEvent
import com.itsaky.androidide.fragments.git.tree.TreeStateManager
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import com.itsaky.androidide.provider.IDEFileIconProvider
import com.itsaky.androidide.viewmodel.FileTreeViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Git 项目侧边栏。
 *
 * @author android_zero
 */
class GitProjectsFragment : BaseGitPageFragment(), FileClickListener, FileLongClickListener {

  private var _binding: FragmentGitProjectsBinding? = null
  private val binding
    get() = _binding!!

  private var fileTreeView: FileTree? = null
  private var loadingJob: Job? = null
  private var branchPopupManager: GitBranchPopupManager? = null
  private var tvBranchName: TextView? = null

  private val viewModel: FileTreeViewModel by viewModels({ requireActivity() })
  private var stateManager = TreeStateManager()

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
    // 返回文件树 tab 时刷新分支名 (可能在其他 tab 切换了分支)
    updateCurrentBranchName()
  }

  override fun onStop() {
    super.onStop()
    if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
    // 自动保存状态
    if (GeneralPreferences.treeRememberExpandedState) {
      fileTreeView?.let { viewModel.saveState(it) }
    }
  }

  override fun setupToolbar() {
    val ctx = context ?: return

    // ===== Git 上下文: 当前分支显示 + 快速切换 =====
    // 文件树 tab 保留最小化 git 上下文: 显示当前分支名, 点击可快速切换分支.
    // 完整分支管理 -> GitBranchesFragment (分支 tab)
    branchPopupManager =
        GitBranchPopupManager(ctx) { branchName ->
          switchBranch(branchName)
        }

    // 当前分支名按钮 (可点击切换分支)
    val branchView =
        TextView(ctx).apply {
          layoutParams =
              LinearLayout.LayoutParams(
                  LinearLayout.LayoutParams.WRAP_CONTENT,
                  resources.getDimensionPixelSize(R.dimen.git_toolbar_icon_size),
              ).apply {
                val margin = 6.dpToPx()
                marginStart = margin
                marginEnd = margin
              }
          text = "branch"
          // 使用 primary 颜色 (与 BaseGitPageFragment 的图标着色方式一致)
          val typedValue = android.util.TypedValue()
          ctx.theme.resolveAttribute(
              com.google.android.material.R.attr.colorPrimary,
              typedValue,
              true,
          )
          setTextColor(typedValue.data)
          textSize = 12f
          setTypeface(typeface, Typeface.BOLD)
          gravity = Gravity.CENTER_VERTICAL
          maxLines = 1
          val outValue = android.util.TypedValue()
          ctx.theme.resolveAttribute(
              android.R.attr.selectableItemBackgroundBorderless,
              outValue,
              true,
          )
          setBackgroundResource(outValue.resourceId)
          val padding = 6.dpToPx()
          setPadding(padding, 0, padding, 0)
          contentDescription = "Switch Branch"
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            tooltipText = "Switch Branch"
          }
          setOnClickListener { branchPopupManager?.show(it) }
        }
    tvBranchName = branchView
    addToolbarCustomView(branchView)

    addToolbarSeparator()

    // ===== 文件树操作 =====
    addToolbarAction(R.drawable.ic_refresh_file_24dp, getString(R.string.refresh)) {
      if (GeneralPreferences.treeRememberExpandedState) {
        fileTreeView?.reloadFileTreeSilently()
      } else {
        listProjectFiles()
      }
      Toast.makeText(context, "Refreshed silently", Toast.LENGTH_SHORT).show()
    }

    addToolbarAction(R.drawable.ic_target_positioning_24dp, "Locate Current File") {
      val activity = context as? EditorHandlerActivity
      val currentFile = activity?.getCurrentEditor()?.file
      if (currentFile != null && currentFile.exists()) {
        fileTreeView?.locateFileAndScroll(currentFile.absolutePath)
      } else {
        Toast.makeText(context, "No active file in editor", Toast.LENGTH_SHORT).show()
      }
    }

    val btnCollapse =
        addToolbarAction(R.drawable.ic_chevron_right, "Collapse All") {
          fileTreeView?.let {
            it.collapseAll()
          }
        }
    btnCollapse.setOnLongClickListener {
      fileTreeView?.let {
        stateManager = TreeStateManager()
        it.collapseAll()
        viewModel.treeState.value = ""
      }
      Toast.makeText(context, "Cleared memory and collapsed all", Toast.LENGTH_SHORT).show()
      true
    }

    addToolbarAction(R.drawable.ic_chevron_down, "Expand All") {
      fileTreeView?.let {
        it.expandAll()
      }
    }

    addToolbarAction(R.drawable.ic_undo, "Undo Node Action") {
      fileTreeView?.let { stateManager.undo(it) }
    }
    addToolbarAction(R.drawable.ic_redo, "Redo Node Action") {
      fileTreeView?.let { stateManager.redo(it) }
    }
  }

  /** 加载当前分支名并更新 toolbar 显示. */
  private fun updateCurrentBranchName() {
    val projectDir = resolveWorkspaceDirPath() ?: run {
      tvBranchName?.text = ""
      return
    }
    CoroutineScope(Dispatchers.IO).launch {
      val branchName =
          runCatching {
                Repository.open(projectDir).use { repo ->
                  if (repo.headDetached()) {
                    "HEAD (detached)"
                  } else {
                    repo.head()?.shorthand()?.removePrefix("refs/heads/") ?: ""
                  }
                }
              }
              .getOrDefault("")
              .ifBlank { "" }
      withContext(Dispatchers.Main) {
        if (isAdded && view != null) {
          tvBranchName?.text = if (branchName.isNotEmpty()) branchName else ""
        }
      }
    }
  }

  /** 快速切换分支: 执行 checkout 后刷新文件树.
   *  复刻 puppygit 的调用方式: 先尝试本地分支, 失败则尝试远程分支.
   */
  private fun switchBranch(branchName: String) {
    val projectDir = resolveWorkspaceDirPath() ?: return
    emitGitOperation("projects", "switch_branch:$branchName")
    CoroutineScope(Dispatchers.IO).launch {
      val ret =
          runCatching {
            Repository.open(projectDir).use { repo ->
              // 先尝试本地分支 checkout
              var checkoutRet = Libgit2Helper.checkoutLocalBranchThenUpdateHead(repo, branchName)
              if (checkoutRet.hasError()) {
                // 本地分支失败, 尝试远程分支 (会变成 detached HEAD)
                checkoutRet = Libgit2Helper.checkoutRemoteBranchThenDetachHead(repo, branchName)
              }
              if (checkoutRet.hasError()) {
                throw RuntimeException(checkoutRet.msg)
              }
            }
          }
      withContext(Dispatchers.Main) {
        ret.onSuccess {
          Toast.makeText(context, "Switched to $branchName", Toast.LENGTH_SHORT).show()
          updateCurrentBranchName()
          // 刷新文件树
          if (GeneralPreferences.treeRememberExpandedState) {
            fileTreeView?.reloadFileTreeSilently()
          } else {
            listProjectFiles()
          }
        }
        ret.onFailure {
          Toast.makeText(
                  context,
                  it.localizedMessage ?: "Failed to switch branch",
                  Toast.LENGTH_LONG,
              )
              .show()
        }
      }
    }
  }

  private fun Int.dpToPx(): Int {
    return (this * resources.displayMetrics.density).toInt()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    view.post { listProjectFiles() }
    updateCurrentBranchName()
  }

  private fun listProjectFiles() {
    if (loadingJob?.isActive == true) return

    loadingJob =
        CoroutineScope(Dispatchers.Main).launch {
          setLoading(true)
          val root =
              withContext(Dispatchers.IO) {
                IProjectManager.getInstance()
                    .projectDirPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
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
          setIconProvider(IDEFileIconProvider(ctx))
          setOnFileClickListener(this@GitProjectsFragment)
          setOnFileLongClickListener(this@GitProjectsFragment)
          setAutoExpandSingleChildFolders(GeneralPreferences.treeAutoExpandSingleChild)
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

      // 恢复状态
      if (GeneralPreferences.treeRememberExpandedState) {
        tree.post { tree.restoreState(viewModel.savedState) }
      }
    }
  }

  private fun setLoading(loading: Boolean) {
    binding.loading.isVisible = loading
    binding.horizontalScroll.isVisible = !loading
  }

  override fun onClick(node: Node<FileObject>) {
    if (node.value.isDirectory()) {
      stateManager.recordAction(node.value.getAbsolutePath(), node.isExpand)
    }

    val target = IDEFileIconProvider.extractNativeFile(node.value) ?: return
    if (target.isFile) {
      val event = FileClickEvent(target)
      event.put(Context::class.java, requireContext())
      EventBus.getDefault().post(event)
    }
  }

  override fun onLongClick(node: Node<FileObject>) {
    val target = IDEFileIconProvider.extractNativeFile(node.value) ?: return
    val event = FileLongClickEvent(target)
    event.put(Context::class.java, requireContext())
    event.put(Node::class.java, node)
    EventBus.getDefault().post(event)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onListProjectFilesRequest(event: ListProjectFilesRequestEvent?) {
    if (GeneralPreferences.treeRememberExpandedState) {
      fileTreeView?.reloadFileTreeSilently()
    } else {
      listProjectFiles()
    }
  }

  override fun onDestroyView() {
    loadingJob?.cancel()
    loadingJob = null
    fileTreeView = null
    branchPopupManager?.dismiss()
    branchPopupManager = null
    tvBranchName = null
    _binding = null
    super.onDestroyView()
  }
}
