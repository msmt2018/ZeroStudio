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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
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
import androidx.fragment.app.Fragment
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
import com.itsaky.androidide.resources.R as ResR
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
 * Git 项目侧边栏 (文件树 + 顶部 git 操作 toolbar).
 *
 * 自包含 fragment, 不依赖任何基类。所有 toolbar 构建、git 分支切换、
 * 文件树加载、EventBus 监听等逻辑全部内联在本类中。
 *
 * @author android_zero
 */
class GitProjectsFragment :
    Fragment(), FileClickListener, FileLongClickListener {

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

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setupToolbar()
    view.post { listProjectFiles() }
    updateCurrentBranchName()
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

  override fun onDestroyView() {
    loadingJob?.cancel()
    loadingJob = null
    fileTreeView = null
    branchPopupManager?.dismiss()
    branchPopupManager = null
    tvBranchName = null
    findToolbarContainer()?.removeAllViews()
    _binding = null
    super.onDestroyView()
  }

  // ============================================================
  // Toolbar 构建 (从原 BaseGitPageFragment 迁出并 inline)
  // ============================================================

  /**
   * 配置工具栏按钮: 顶部 git 操作 + 文件树操作。
   *
   * Git 上下文: 当前分支名 (可点击切换)
   * 文件树操作: Refresh / Locate / Collapse All / Expand All / Undo / Redo
   */
  private fun setupToolbar() {
    val ctx = context ?: return

    // ===== Git 上下文: 当前分支显示 + 快速切换 =====
    branchPopupManager =
        GitBranchPopupManager(ctx) { branchName -> switchBranch(branchName) }

    // 当前分支名按钮 (可点击切换分支)
    val branchView =
        TextView(ctx).apply {
          layoutParams =
              LinearLayout.LayoutParams(
                  LinearLayout.LayoutParams.WRAP_CONTENT,
                  resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_size),
              ).apply {
                val margin = 6.dpToPx()
                marginStart = margin
                marginEnd = margin
              }
          text = ""
          // 使用 primary 颜色 (与 toolbar 图标着色方式一致)
          val typedValue = android.util.TypedValue()
          ctx.theme.resolveAttribute(
              ResR.attr.colorPrimary,
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
    addToolbarAction(ResR.drawable.ic_refresh_file_24dp, getString(R.string.refresh)) {
      if (GeneralPreferences.treeRememberExpandedState) {
        fileTreeView?.reloadFileTreeSilently()
      } else {
        listProjectFiles()
      }
      Toast.makeText(context, "Refreshed silently", Toast.LENGTH_SHORT).show()
    }

    addToolbarAction(ResR.drawable.ic_target_positioning_24dp, "Locate Current File") {
      val activity = context as? EditorHandlerActivity
      val currentFile = activity?.getCurrentEditor()?.file
      if (currentFile != null && currentFile.exists()) {
        fileTreeView?.locateFileAndScroll(currentFile.absolutePath)
      } else {
        Toast.makeText(context, "No active file in editor", Toast.LENGTH_SHORT).show()
      }
    }

    val btnCollapse =
        addToolbarAction(ResR.drawable.ic_chevron_right, "Collapse All") {
          fileTreeView?.let { it.collapseAll() }
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

    addToolbarAction(ResR.drawable.ic_chevron_down, "Expand All") {
      fileTreeView?.let { it.expandAll() }
    }

    addToolbarAction(ResR.drawable.ic_undo, "Undo Node Action") {
      fileTreeView?.let { stateManager.undo(it) }
    }
    addToolbarAction(ResR.drawable.ic_redo, "Redo Node Action") {
      fileTreeView?.let { stateManager.redo(it) }
    }
  }

  /** 向工具栏添加一个图标按钮。 */
  private fun addToolbarAction(iconRes: Int, tooltip: String, onClick: () -> Unit): View {
    val context = requireContext()

    val button =
        ImageButton(context).apply {
          layoutParams =
              LinearLayout.LayoutParams(
                  resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_size),
                  resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_size),
              )
          setImageResource(iconRes)
          val outValue = android.util.TypedValue()
          context.theme.resolveAttribute(
              android.R.attr.selectableItemBackgroundBorderless,
              outValue,
              true,
          )
          setBackgroundResource(outValue.resourceId)

          contentDescription = tooltip
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            tooltipText = tooltip
          }
          setOnClickListener { onClick() }

          val padding = resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_padding)
          setPadding(padding, padding, padding, padding)

          val typedValue = android.util.TypedValue()
          context.theme.resolveAttribute(
              ResR.attr.colorOnSurface,
              typedValue,
              true,
          )
          setColorFilter(typedValue.data)
        }

    findToolbarContainer()?.addView(button)
    return button
  }

  private fun addToolbarSeparator() {
    val context = requireContext()
    val separator = View(context).apply {
      layoutParams =
          LinearLayout.LayoutParams(
              1.dpToPx(),
              resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_size),
          ).apply {
            val margin = 4.dpToPx()
            marginStart = margin
            marginEnd = margin
          }
      setBackgroundColor(
          androidx.core.content.ContextCompat.getColor(context, ResR.color.git_toolbar_separator)
      )
    }
    findToolbarContainer()?.addView(separator)
  }

  private fun addToolbarSectionLabel(text: String) {
    val context = requireContext()
    val label =
        TextView(context).apply {
          layoutParams =
              LinearLayout.LayoutParams(
                  LinearLayout.LayoutParams.WRAP_CONTENT,
                  resources.getDimensionPixelSize(ResR.dimen.git_toolbar_icon_size),
              ).apply {
                val margin = 8.dpToPx()
                marginStart = margin
                marginEnd = margin
              }
          setText(text)
          setTextColor(
              androidx.core.content.ContextCompat.getColor(context, ResR.color.git_toolbar_label)
          )
          textSize = 10f
          gravity = Gravity.CENTER_VERTICAL
        }
    findToolbarContainer()?.addView(label)
  }

  private fun addToolbarCustomView(view: View) {
    findToolbarContainer()?.addView(view)
  }

  private fun Int.dpToPx(): Int {
    return (this * resources.displayMetrics.density).toInt()
  }

  private fun findToolbarContainer(): LinearLayout? {
    val rootView = view ?: return null
    val scrollView = rootView.findViewById<HorizontalScrollView>(R.id.git_mini_toolbar_scroll)
    return scrollView?.findViewById(R.id.git_mini_toolbar_container)
  }

  /**
   * 解析当前打开的工程目录绝对路径。
   *
   * 优先从 workspace 拿 (最常见的已打开工程路径); 拿不到再回退到 projectDir。
   * 两者都是 nullable, 可以用干净 null chain。
   *
   * @return 工程目录绝对路径; 当前没有打开工程时返回 `null`
   */
  private fun resolveWorkspaceDirPath(): String? {
    val projectManager = IProjectManager.getInstance()
    return projectManager.getWorkspace()?.getProjectDir()?.path?.takeIf { it.isNotBlank() }
        ?: projectManager.projectDirPath?.takeIf { it.isNotBlank() }
  }

  /** 打开外部链接 (浏览器), 失败时显示 toast。 */
  private fun openExternalLink(url: String, errorTip: String = "No browser available") {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(requireContext(), errorTip, Toast.LENGTH_SHORT).show()
    }
  }

  /** 打开 web 链接, 没有则弹 toast。 */
  private fun openWebLinkOrToast(url: String?, emptyMsg: String = "No link available") {
    if (url.isNullOrBlank()) {
      Toast.makeText(requireContext(), emptyMsg, Toast.LENGTH_SHORT).show()
      return
    }
    openExternalLink(url)
  }

  // ============================================================
  // 文件树逻辑
  // ============================================================

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

  /**
   * 快速切换分支: 执行 checkout 后刷新文件树。
   *
   * 复刻 puppygit 的调用方式: 先尝试本地分支, 失败则尝试远程分支。
   */
  private fun switchBranch(branchName: String) {
    val projectDir = resolveWorkspaceDirPath() ?: return
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
}
