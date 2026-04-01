package com.itsaky.androidide.fragments.git

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.zero.studio.treeview.model.TreeNode
import android.zero.studio.treeview.view.AndroidTreeView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.google.android.material.color.MaterialColors
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitProjectsBinding
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.fragments.git.menu.GitBranchPopupManager
import com.itsaky.androidide.fragments.git.tree.ExpandTreeNodeRequestEvent
import com.itsaky.androidide.fragments.git.tree.FileTreeViewHolder
import com.itsaky.androidide.fragments.git.tree.FileTreeViewModel
import com.itsaky.androidide.fragments.git.tree.ListProjectFilesRequestEvent
import com.itsaky.androidide.fragments.git.tree.TreeStateManager
import com.itsaky.androidide.fragments.git.function.ZeroCloneDialogBottomSheetFragment
import com.itsaky.androidide.interfaces.IEditorHandler
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.tasks.callables.FileTreeCallable.SortFileName
import com.itsaky.androidide.tasks.callables.FileTreeCallable.SortFolder
import java.io.File
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * 项目/文件树浏览页面 (Final Optimized Edition)
 *
 * @author android_zero
 */
class GitProjectsFragment :
    BaseGitPageFragment(), TreeNode.TreeNodeClickListener, TreeNode.TreeNodeLongClickListener {

  private var _binding: FragmentGitProjectsBinding? = null
  private val binding
    get() = _binding!!

  private val viewModel by viewModels<FileTreeViewModel>(ownerProducer = { requireActivity() })
  private val stateManager = TreeStateManager()

  private var fileTreeView: AndroidTreeView? = null
  private var rootTreeNode: TreeNode? = null
  private var projectRootNode: TreeNode? = null

  private var loadingJob: Job? = null

  // UI 锁
  private val isRendering = AtomicBoolean(false)

  // 状态缓存
  private var lastScrollY = 0
  private var isRestoringState = false

  // 撤销/重做 模式状态 (默认为撤销模式)
  private var isRedoMode = false
  private var btnUndoRedo: ImageButton? = null

  // 高亮引用
  private var currentHighlightAnimator: ValueAnimator? = null
  private var lastHighlightedNode: TreeNode? = null

  // UI Components
  private var tvCurrentBranch: TextView? = null
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

    // 分支切换
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

    // 定位文件
    addToolbarAction(R.drawable.ic_target_positioning_24dp, "Locate File") { locateCurrentFile() }

    // 刷新
    addToolbarAction(R.drawable.ic_refresh_file_24dp, getString(R.string.refresh)) {
      stateManager.clear()
      refreshFileTree()
    }

    // 收/展
    val toggleBtn =
        addToolbarAction(R.drawable.ic_unfold_more_24dp, "Expand/Collapse") {
          collapseChildrenNodes()
        }
    toggleBtn.setOnLongClickListener {
      expandAllNodesHighPerformance()
      true
    }

    // 撤销/重做
    val undoRedoView =
        addToolbarAction(R.drawable.ic_editor_revoke, "Undo/Redo") { performUndoOrRedo() }
    // 保存引用以便更新图标
    if (undoRedoView is ImageButton) {
      btnUndoRedo = undoRedoView
    }

    // 长按切换模式
    undoRedoView.setOnLongClickListener {
      toggleUndoRedoMode()
      true
    }

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

  // ============================================================================================
  //  功能: 撤销/重做 (Undo/Redo)
  // ============================================================================================

  private fun toggleUndoRedoMode() {
    isRedoMode = !isRedoMode
    updateUndoRedoButtonIcon()

    val modeName = if (isRedoMode) "Redo Mode" else "Undo Mode"
    Toast.makeText(context, "Switched to $modeName", Toast.LENGTH_SHORT).show()
  }

  private fun updateUndoRedoButtonIcon() {
    val iconRes = if (isRedoMode) R.drawable.ic_edit_redo else R.drawable.ic_editor_revoke
    btnUndoRedo?.setImageResource(iconRes)
  }

  private fun performUndoOrRedo() {
    val tree = fileTreeView ?: return
    val curState = tree.saveState ?: ""

    val newState =
        if (isRedoMode) {
          // 重做逻辑
          if (!stateManager.canRedo()) {
            Toast.makeText(context, "Nothing to redo", Toast.LENGTH_SHORT).show()
            return
          }
          stateManager.redo(curState)
        } else {
          // 撤销逻辑
          if (!stateManager.canUndo()) {
            Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show()
            return
          }
          stateManager.undo(curState)
        }

    if (newState != null) {
      // 恢复状态
      restoreStateInternal(newState)

      // 额外检查：确保根节点展开
      projectRootNode?.let { if (!it.isExpanded) tree.expandNode(it) }

      val actionName = if (isRedoMode) "Redo" else "Undo"
      Toast.makeText(context, "$actionName success", Toast.LENGTH_SHORT).show()
    }
  }

  // ============================================================================================
  //  功能: 收/展节点
  // ============================================================================================

  /** 折叠逻辑：保留项目根节点展开，仅折叠根节点下的子目录 */
  private fun collapseChildrenNodes() {
    val tree = fileTreeView ?: return
    val root = projectRootNode ?: return

    // 保存状态以便撤销
    stateManager.saveState(tree)

    // 临时禁用动画以提高性能
    val transition = ChangeBounds()
    transition.duration = 100
    TransitionManager.beginDelayedTransition(binding.root, transition)

    // 遍历根节点的直接子节点进行折叠
    for (child in root.children) {
      // 递归清理状态，确保下次展开时子文件夹是关闭的
      resetExpansionStateRecursive(child)
      tree.collapseNode(child)
    }

    // 再次确保根节点展开
    if (!root.isExpanded) tree.expandNode(root)
  }

  private fun resetExpansionStateRecursive(node: TreeNode) {
    node.isExpanded = false
    for (child in node.children) {
      resetExpansionStateRecursive(child)
    }
  }

  /** 全部展开 */
  private fun expandAllNodesHighPerformance() {
    val root = projectRootNode ?: return
    val ctx = context ?: return

    // 保存状态
    fileTreeView?.let { stateManager.saveState(it) }

    loadingJob?.cancel()
    loadingJob =
        viewLifecycleOwner.lifecycleScope.launch {
          showLoadingState()
          isRendering.set(true)

          val count = withContext(Dispatchers.IO) { loadAllChildrenRecursively(root, ctx, 0, 0) }

          hideLoadingState()
          isRendering.set(false)

          if (fileTreeView != null) {
            if (count > 2000) {
              Toast.makeText(
                      ctx,
                      "Too many files ($count), expanded partially.",
                      Toast.LENGTH_SHORT,
                  )
                  .show()
            }
            fileTreeView!!.expandAll()
            Toast.makeText(ctx, "Expanded all folders", Toast.LENGTH_SHORT).show()
          }
        }
  }

  private fun loadAllChildrenRecursively(
      node: TreeNode,
      ctx: Context,
      currentDepth: Int,
      currentCount: Int,
  ): Int {
    if (currentDepth > 15 || currentCount > 3000) return currentCount

    var newCount = currentCount

    if (node.children.isEmpty()) {
      val file = node.value as? File
      if (file != null && file.isDirectory) {
        val subFiles = file.listFiles()
        if (subFiles != null) {
          sortAndAddChildren(subFiles, node, ctx)
          newCount += subFiles.size
        }
      }
    } else {
      newCount += node.children.size
    }

    val children = ArrayList(node.children)
    for (child in children) {
      newCount = loadAllChildrenRecursively(child, ctx, currentDepth + 1, newCount)
    }
    return newCount
  }

  // ============================================================================================
  //  功能: 定位文件
  // ============================================================================================

  private fun locateCurrentFile() {
    if (isRendering.get()) return

    val handler = activity as? IEditorHandler ?: return
    val targetFile = handler.getCurrentEditor()?.file

    if (targetFile == null) {
      Toast.makeText(context, "No file opened", Toast.LENGTH_SHORT).show()
      return
    }

    val projectPath = IProjectManager.getInstance().projectDirPath
    if (!targetFile.absolutePath.startsWith(projectPath)) {
      Toast.makeText(context, "File not in project", Toast.LENGTH_SHORT).show()
      return
    }

    val relativePath =
        targetFile.absolutePath.substring(projectPath.length).trim(File.separatorChar)
    val segments = relativePath.split(File.separatorChar)

    if (segments.isEmpty() || projectRootNode == null) return
    val ctx = context ?: return

    viewLifecycleOwner.lifecycleScope.launch {
      clearHighlight()
      if (!projectRootNode!!.isExpanded) {
        fileTreeView?.expandNode(projectRootNode)
      }
      findAndLocateNode(projectRootNode!!, segments, 0, ctx)
    }
  }

  private suspend fun findAndLocateNode(
      currentNode: TreeNode,
      segments: List<String>,
      index: Int,
      ctx: Context,
  ) {
    if (index >= segments.size) return

    val targetName = segments[index]
    var foundChild = currentNode.children.find { (it.value as? File)?.name == targetName }

    if (foundChild == null && (currentNode.value as? File)?.isDirectory == true) {
      val loaded = loadNodeChildrenSuspend(currentNode, ctx)
      if (loaded) {
        fileTreeView?.expandNode(currentNode)
        foundChild = currentNode.children.find { (it.value as? File)?.name == targetName }
      }
    } else {
      if (!currentNode.isExpanded) {
        fileTreeView?.expandNode(currentNode)
      }
    }

    if (foundChild != null) {
      if (index == segments.size - 1) {
        performScrollAndHighlight(foundChild)
      } else {
        findAndLocateNode(foundChild, segments, index + 1, ctx)
      }
    }
  }

  private fun performScrollAndHighlight(node: TreeNode) {
    binding.root.post {
      val viewHolder = node.viewHolder ?: return@post
      val view = viewHolder.view ?: return@post

      val scrollView = binding.horizontalScroll

      var relativeY = 0
      var p: View = view
      while (p != scrollView) {
        relativeY += p.top
        val parent = p.parent as? View ?: break
        p = parent
      }

      val targetY = relativeY - (scrollView.height / 3)
      scrollView.smoothScrollTo(0, targetY)

      highlightItemView(view, node)
    }
  }

  private fun highlightItemView(view: View, node: TreeNode) {
    currentHighlightAnimator?.cancel()

    node.isHighlighted = true
    lastHighlightedNode = node

    val highlightColor =
        MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimaryContainer)
    val defaultColor = Color.TRANSPARENT

    val fadeIn = ValueAnimator.ofObject(ArgbEvaluator(), defaultColor, highlightColor)
    fadeIn.duration = 300
    fadeIn.addUpdateListener { animator -> view.setBackgroundColor(animator.animatedValue as Int) }
    currentHighlightAnimator = fadeIn
    fadeIn.start()

    view.postDelayed(
        {
          if (currentHighlightAnimator == fadeIn) {
            val fadeOut = ValueAnimator.ofObject(ArgbEvaluator(), highlightColor, defaultColor)
            fadeOut.duration = 500
            fadeOut.interpolator = AccelerateDecelerateInterpolator()
            fadeOut.addUpdateListener { animator ->
              view.setBackgroundColor(animator.animatedValue as Int)
            }
            currentHighlightAnimator = fadeOut
            fadeOut.start()

            node.isHighlighted = false
            lastHighlightedNode = null
          }
        },
        1500,
    )
  }

  private fun clearHighlight() {
    currentHighlightAnimator?.cancel()
    currentHighlightAnimator = null
    lastHighlightedNode?.isHighlighted = false
    lastHighlightedNode = null
  }

  private suspend fun loadNodeChildrenSuspend(node: TreeNode, ctx: Context): Boolean =
      withContext(Dispatchers.IO) {
        val file = node.value as? File ?: return@withContext false
        val files =
            try {
              file.listFiles()
            } catch (e: Exception) {
              null
            }

        if (files.isNullOrEmpty()) return@withContext false

        node.children.clear()
        sortAndAddChildren(files, node, ctx)
        return@withContext true
      }

  private fun refreshFileTree() {
    clearHighlight()
    lastScrollY = binding.horizontalScroll.scrollY
    isRestoringState = true
    fileTreeView?.let { stateManager.saveState(it) }
    listProjectFiles()
  }

  private fun listProjectFiles() {
    if (_binding == null) return
    val ctx = context ?: return

    loadingJob?.cancel()
    loadingJob =
        viewLifecycleOwner.lifecycleScope.launch {
          val projectDirPath = IProjectManager.getInstance().projectDirPath
          if (projectDirPath.isNullOrEmpty()) {
            showEmptyState("No project opened")
            return@launch
          }
          val projectDir = File(projectDirPath)

          if (!isRestoringState) showLoadingState()

          isRendering.set(true)

          val (root, projRoot) =
              withContext(Dispatchers.IO) {
                val root = TreeNode(File(""))
                root.viewHolder = FileTreeViewHolder(ctx)

                val projRoot = TreeNode.root(projectDir)
                projRoot.viewHolder = FileTreeViewHolder(ctx)

                try {
                  val files = projectDir.listFiles()
                  if (files != null) sortAndAddChildren(files, projRoot, ctx)
                } catch (e: Exception) {}

                root.addChild(projRoot, false)
                Pair(root, projRoot)
              }

          if (_binding == null) return@launch

          hideLoadingState()
          isRendering.set(false)

          rootTreeNode = root
          projectRootNode = projRoot

          val tree = createTreeView(root)
          if (tree != null) {
            tree.setUseAutoToggle(false)
            tree.setDefaultNodeClickListener(this@GitProjectsFragment)
            tree.setDefaultNodeLongClickListener(this@GitProjectsFragment)

            binding.treeContainer.removeAllViews()
            binding.treeContainer.addView(tree.view)

            if (isRestoringState) {
              tryRestoreState(root, ctx)
              binding.root.post {
                binding.horizontalScroll.scrollTo(0, lastScrollY)
                isRestoringState = false
              }
            } else {
              tree.expandNode(projRoot)
            }
          }
        }
  }

  private fun createTreeView(node: TreeNode): AndroidTreeView? {
    val ctx = context ?: return null
    return AndroidTreeView(ctx, node, 0).apply { fileTreeView = this }
  }

  private fun sortAndAddChildren(files: Array<File>, parent: TreeNode, ctx: Context) {
    try {
      Arrays.sort(files, SortFileName())
      Arrays.sort(files, SortFolder())
      for (file in files) {
        val node = TreeNode(file)
        node.viewHolder = FileTreeViewHolder(ctx)
        parent.addChild(node, false)
      }
    } catch (e: Exception) {}
  }

  override fun onClick(node: TreeNode, value: Any) {
    if (isRendering.get()) return

    val file = value as File
    if (!file.exists()) return

    // 每次点击前保存状态，用于撤销
    fileTreeView?.let { stateManager.saveState(it) }

    if (file.isDirectory) {
      // 只有操作文件夹折叠/展开时才保存状态，用于撤销
      fileTreeView?.let { stateManager.saveState(it) }

      if (node.isExpanded) {
        // 手动折叠：递归重置
        resetExpansionStateRecursive(node)

        val transition = ChangeBounds()
        transition.duration = 150
        TransitionManager.beginDelayedTransition(binding.root, transition)
        fileTreeView?.collapseNode(node)
      } else {
        setLoading(node, true)
        val ctx = context ?: return
        loadNodeChildren(node, ctx) {
          setLoading(node, false)
          val transition = ChangeBounds()
          transition.duration = 150
          TransitionManager.beginDelayedTransition(binding.root, transition)
          fileTreeView?.expandNode(node)
        }
      }
    } else {
      val event = FileClickEvent(file)
      event.put(Context::class.java, requireContext())
      EventBus.getDefault().post(event)
    }
  }

  private fun loadNodeChildren(node: TreeNode, ctx: Context, onLoaded: () -> Unit) {
    if (!node.children.isEmpty()) {
      onLoaded()
      return
    }

    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val file = node.value as File
      try {
        val files = file.listFiles()
        if (files != null) sortAndAddChildren(files, node, ctx)
      } catch (e: Exception) {}

      withContext(Dispatchers.Main) { if (_binding != null) onLoaded() }
    }
  }

  private fun tryRestoreState(rootNode: TreeNode, ctx: Context) {
    val state = viewModel.savedState
    restoreStateInternal(state)
  }

  private fun restoreStateInternal(state: String) {
    if (!TextUtils.isEmpty(state) && fileTreeView != null && rootTreeNode != null) {
      val ctx = context ?: return
      try {
        fileTreeView!!.collapseAll()
        val openNodes =
            state
                .split(AndroidTreeView.NODES_PATH_SEPARATOR.toRegex())
                .dropLastWhile { it.isEmpty() }
                .toHashSet()

        viewLifecycleOwner.lifecycleScope.launch {
          isRendering.set(true)
          withContext(Dispatchers.IO) {
            restoreNodeStateRecursively(rootTreeNode!!, openNodes, ctx)
          }
          isRendering.set(false)
          projectRootNode?.let { fileTreeView?.expandNode(it) }
        }
      } catch (e: Exception) {
        projectRootNode?.let { fileTreeView?.expandNode(it) }
      }
    } else {
      projectRootNode?.let { fileTreeView?.expandNode(it) }
    }
  }

  private fun restoreNodeStateRecursively(
      parentNode: TreeNode,
      openNodes: Set<String>,
      ctx: Context,
  ) {
    if (openNodes.contains(parentNode.path)) {
      val file = parentNode.value as? File
      val files = file?.listFiles()
      if (files != null && parentNode.children.isEmpty()) {
        sortAndAddChildren(files, parentNode, ctx)
      }

      val children = ArrayList(parentNode.children)
      for (node in children) {
        restoreNodeStateRecursively(node, openNodes, ctx)
      }

      parentNode.isExpanded = true
    }
  }

  private fun setLoading(node: TreeNode, loading: Boolean) {
    (node.viewHolder as? FileTreeViewHolder)?.setLoading(loading)
  }

  private fun showLoadingState() {
    binding.loading.isVisible = true
    binding.horizontalScroll.isVisible = false
    binding.tvEmpty.isVisible = false
  }

  private fun hideLoadingState() {
    binding.loading.isVisible = false
    binding.horizontalScroll.isVisible = true
    binding.tvEmpty.isVisible = false
  }

  private fun showEmptyState(msg: String) {
    binding.loading.isVisible = false
    binding.horizontalScroll.isVisible = false
    binding.tvEmpty.text = msg
    binding.tvEmpty.isVisible = true
  }

  override fun onDestroyView() {
    fileTreeView?.let { viewModel.saveState(it) }
    fileTreeView = null
    rootTreeNode = null
    projectRootNode = null
    loadingJob?.cancel()
    super.onDestroyView()
    _binding = null
  }

  override fun onLongClick(node: TreeNode, value: Any): Boolean {
    EventBus.getDefault()
        .post(
            FileLongClickEvent(value as File).apply {
              put(Context::class.java, requireContext())
              put(TreeNode::class.java, node)
            }
        )
    return true
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onListProjectFilesRequest(event: ListProjectFilesRequestEvent?) {
    if (isVisible && context != null) listProjectFiles()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onExpandTreeNodeRequest(event: ExpandTreeNodeRequestEvent) {
    if (isVisible && context != null) fileTreeView?.expandNode(event.node)
  }
}
