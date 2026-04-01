package com.itsaky.androidide.fragments.sidebar

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.blankj.utilcode.util.SizeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.itsaky.androidide.databinding.LayoutDataFileTreeBinding
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.events.ExpandTreeNodeRequestEvent
import com.itsaky.androidide.events.ListProjectFilesRequestEvent
import com.itsaky.androidide.fragments.sidebar.datatree.FileTreeViewHolder
import com.itsaky.androidide.resources.R.drawable
import com.itsaky.androidide.tasks.TaskExecutor
import com.itsaky.androidide.tasks.callables.FileTreeCallable.SortFileName
import com.itsaky.androidide.tasks.callables.FileTreeCallable.SortFolder
import com.itsaky.androidide.utils.doOnApplyWindowInsets
import com.itsaky.androidide.viewmodel.FileTreeViewModel
import com.rk.filetree.model.TreeNode
import com.rk.filetree.model.TreeNode.TreeNodeClickListener
import com.rk.filetree.model.TreeNode.TreeNodeLongClickListener
import com.rk.filetree.view.AndroidTreeView
import java.io.File
import java.util.Arrays
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN

/** ** 用于访问特殊权限才能访问的私有目录 作者：android_zero */
class DataFileTreeFragment :
    BottomSheetDialogFragment(), TreeNodeClickListener, TreeNodeLongClickListener {

  private var binding: LayoutDataFileTreeBinding? = null
  private var fileTreeView: AndroidTreeView? = null
  private val viewModel by viewModels<FileTreeViewModel>(ownerProducer = { requireActivity() })

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }

    binding = LayoutDataFileTreeBinding.inflate(inflater, container, false)

    binding?.root?.doOnApplyWindowInsets { view, insets, _, _ ->
      insets.getInsets(WindowInsetsCompat.Type.statusBars()).apply {
        view.updatePadding(top = top + SizeUtils.dp2px(8f))
      }
    }

    binding?.selectMountDirectoryButton?.visibility = View.GONE
    binding?.mountStatus?.visibility = View.GONE

    return binding!!.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    loadInternalApplicationData()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    EventBus.getDefault().unregister(this)
    saveTreeState()
    binding = null
    fileTreeView = null
  }

  private fun loadInternalApplicationData() {
    if (binding == null) return

    binding!!.horizontalCroll.removeAllViews()
    binding!!.horizontalCroll.visibility = View.GONE
    binding!!.loading.visibility = View.VISIBLE

    val rootNode = TreeNode(File(""))
    rootNode.viewHolder = FileTreeViewHolder(requireContext())

    // 内部数据目录 (Internal Data)
    val internalDataDir =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          requireContext().dataDir
        } else {
          requireContext().filesDir.parentFile
        }
    addRootItem(rootNode, internalDataDir, "Internal Data")

    // 外部数据目录 (Android Data)
    val externalFilesDir = requireContext().getExternalFilesDir(null)
    val externalDataDir = externalFilesDir?.parentFile
    addRootItem(rootNode, externalDataDir, "Android Data")

    // OBB 目录 (Android OBB)
    val obbDir = requireContext().obbDir
    addRootItem(rootNode, obbDir, "Android OBB")

    // 用户加密存储 (User DE Data)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val deContext = requireContext().createDeviceProtectedStorageContext()
      val deDataDir = deContext.dataDir
      if (deDataDir != null && deDataDir.absolutePath != internalDataDir?.absolutePath) {
        addRootItem(rootNode, deDataDir, "User DE Data")
      }
    }

    // 内部存储根目录 (Sdcard)
    addRootItem(rootNode, Environment.getExternalStorageDirectory(), "Internal Storage")

    // 系统根目录 (Root /) - 通常只读，或者无权限访问子目录
    addRootItem(rootNode, File("/"), "Device Root (/)")

    // 系统目录 (System)
    addRootItem(rootNode, File("/system"), "System")

    // 构建视图
    binding!!.loading.visibility = View.GONE
    binding!!.horizontalCroll.visibility = View.VISIBLE

    val tree = createTreeView(rootNode)
    if (tree != null) {
      tree.setUseAutoToggle(false) // 禁用自动切换，由 onClick 手动控制
      tree.setDefaultNodeClickListener(this)
      tree.setDefaultNodeLongClickListener(this)

      tree.expandAll()

      val view = tree.view
      binding!!.horizontalCroll.addView(view)

      view.post { tryRestoreState(rootNode) }
    }
  }

  private fun addRootItem(rootNode: TreeNode, file: File?, displayName: String) {
    if (file != null && file.exists()) {
      val node = TreeNode(RenamedFile(file, displayName))
      node.viewHolder = FileTreeViewHolder(context)
      rootNode.addChild(node, false)
    }
  }

  private class RenamedFile(file: File, private val displayName: String) : File(file.absolutePath) {
    override fun getName(): String = displayName

    override fun toString(): String = displayName
  }

  override fun onClick(node: TreeNode, p2: Any) {
    val file = p2 as File

    if (!file.exists()) {
      // 对于某些受限目录，exists() 可能返回 false，但我们仍然尝试列出
      Log.w(TAG, "File does not exist: ${file.path}")
    }

    if (file.isDirectory) {
      if (node.isExpanded) {
        collapseNode(node)
      } else {
        if (node.children.isEmpty()) {
          setLoading(node)
          listNode(node) { expandNode(node) }
        } else {
          // 如果已经有子节点，直接展开
          expandNode(node)
        }
      }
    } else {
      val event = FileClickEvent(file)
      event.put(Context::class.java, requireContext())
      EventBus.getDefault().post(event)
    }
  }

  private fun listNode(node: TreeNode, whenDone: Runnable) {
    val childrenCopy = ArrayList(node.children)
    childrenCopy.forEach { node.deleteChild(it) }

    node.isExpanded = false // 重置状态

    val file = node.value as File

    TaskExecutor.executeAsync({
      val files = file.listFiles()
      if (files != null) {
        listFilesForNode(files, node)
        // 返回 true 表示成功读取
        true
      } else {
        // 如果返回 null，可能是权限不足
        // 在这里可以尝试 Root 方式（如果支持）
        // 目前简单处理：返回 false
        false
      }
    }) { success ->
      // UI Thread
      if (success == false) {
        Toast.makeText(context, "Access Denied or Empty: ${file.name}", Toast.LENGTH_SHORT).show()
      }
      whenDone.run()
    }
  }

  private fun listFilesForNode(files: Array<File>, parent: TreeNode) {
    Arrays.sort(files, SortFileName())
    Arrays.sort(files, SortFolder())
    for (file in files) {
      val node = TreeNode(file)
      node.viewHolder = FileTreeViewHolder(context)
      parent.addChild(node, false)
    }
  }

  fun saveTreeState() {
    viewModel.saveState(fileTreeView)
  }

  override fun onLongClick(node: TreeNode, value: Any): Boolean {
    val event = FileLongClickEvent((value as File))
    event.put(Context::class.java, requireContext())
    event.put(TreeNode::class.java, node)
    EventBus.getDefault().post(event)
    return true
  }

  @Subscribe(threadMode = MAIN)
  fun onGetListFilesRequested(event: ListProjectFilesRequestEvent?) {
    if (!isVisible || context == null) return
    loadInternalApplicationData()
  }

  @Subscribe(threadMode = MAIN)
  fun onGetExpandTreeNodeRequest(event: ExpandTreeNodeRequestEvent) {
    if (!isVisible || context == null) return
    expandNode(event.node)
  }

  private fun updateChevron(node: TreeNode) {
    (node.viewHolder as? FileTreeViewHolder)?.updateChevron(node.isExpanded)
  }

  private fun expandNode(node: TreeNode, animate: Boolean = true) {
    if (fileTreeView == null) return
    if (animate) {
      TransitionManager.beginDelayedTransition(binding!!.root, ChangeBounds())
    }
    fileTreeView!!.expandNode(node)
    updateChevron(node)
  }

  private fun collapseNode(node: TreeNode, animate: Boolean = true) {
    if (fileTreeView == null) return
    if (animate) {
      TransitionManager.beginDelayedTransition(binding!!.root, ChangeBounds())
    }
    fileTreeView!!.collapseNode(node)
    updateChevron(node)
  }

  private fun setLoading(node: TreeNode) {
    (node.viewHolder as? FileTreeViewHolder)?.setLoading(true)
  }

  private fun createTreeView(node: TreeNode): AndroidTreeView? {
    return if (context == null) {
      null
    } else AndroidTreeView(requireContext(), node, drawable.bg_ripple).also { fileTreeView = it }
  }

  private fun tryRestoreState(rootNode: TreeNode, state: String? = viewModel.savedState) {
    if (!state.isNullOrEmpty() && fileTreeView != null) {
      val openNodes =
          state
              .split(AndroidTreeView.NODES_PATH_SEPARATOR.toRegex())
              .dropLastWhile { it.isEmpty() }
              .toHashSet()
      restoreNodeState(rootNode, openNodes)
    }
  }

  private fun restoreNodeState(root: TreeNode, openNodes: Set<String>) {
    val children = root.children
    for (node in children) {
      if (openNodes.contains(node.path)) {
        listNode(node) {
          expandNode(node, false)
          restoreNodeState(node, openNodes)
        }
      }
    }
  }

  companion object {
    const val TAG = "editor.datafileTree"

    @JvmStatic fun newInstance(): DataFileTreeFragment = DataFileTreeFragment()
  }
}
