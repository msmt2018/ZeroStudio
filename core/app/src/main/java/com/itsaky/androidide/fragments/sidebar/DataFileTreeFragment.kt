package com.itsaky.androidide.fragments.sidebar

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileLongClickListener
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.provider.file
import android.zero.studio.view.filetree.widget.FileTree
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.blankj.utilcode.util.SizeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.itsaky.androidide.databinding.LayoutDataFileTreeBinding
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.events.ExpandTreeNodeRequestEvent
import com.itsaky.androidide.events.ListProjectFilesRequestEvent
import com.itsaky.androidide.utils.ClassResourceMonitor
import com.itsaky.androidide.utils.doOnApplyWindowInsets
import com.itsaky.androidide.viewmodel.DataFileTreeViewModel
import java.io.File
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import org.slf4j.LoggerFactory

/**
 * 用于访问特殊权限才能访问的私有目录
 *
 * @author android_zero
 */
class DataFileTreeFragment : BottomSheetDialogFragment(), FileClickListener, FileLongClickListener {

  private var binding: LayoutDataFileTreeBinding? = null
  private var fileTreeView: FileTree? = null
  private val viewModel by viewModels<DataFileTreeViewModel>(ownerProducer = { requireActivity() })

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
    ClassResourceMonitor.trace(log) {
        super.onViewCreated(view, savedInstanceState)
        loadInternalApplicationData()
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    EventBus.getDefault().unregister(this)
    saveTreeState()
    binding = null
    fileTreeView = null
  }

  private fun loadInternalApplicationData() =
      ClassResourceMonitor.trace(log) {
        if (binding == null) return@trace

    binding!!.horizontalCroll.removeAllViews()
    binding!!.horizontalCroll.visibility = View.GONE
    binding!!.loading.visibility = View.VISIBLE

    val roots = mutableListOf<FileObject>()

    // 内部数据目录 (Internal Data)
    val internalDataDir =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          requireContext().dataDir
        } else {
          requireContext().filesDir.parentFile
        }
    if (internalDataDir?.exists() == true) {
      roots.add(RenamedFileObject(internalDataDir, "Internal Data"))
    }

    // 外部数据目录 (Android Data)
    val externalDataDir = requireContext().getExternalFilesDir(null)?.parentFile
    if (externalDataDir?.exists() == true) {
      roots.add(RenamedFileObject(externalDataDir, "Android Data"))
    }

    // OBB 目录 (Android OBB)
    val obbDir = requireContext().obbDir
    if (obbDir?.exists() == true) {
      roots.add(RenamedFileObject(obbDir, "Android OBB"))
    }

    // 用户加密存储 (User DE Data)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val deDataDir = requireContext().createDeviceProtectedStorageContext().dataDir
      if (deDataDir != null && deDataDir.absolutePath != internalDataDir?.absolutePath) {
        roots.add(RenamedFileObject(deDataDir, "User DE Data"))
      }
    }

    // 内部存储根目录 (Sdcard)
    val sdcard = Environment.getExternalStorageDirectory()
    if (sdcard.exists()) {
      roots.add(RenamedFileObject(sdcard, "Internal Storage"))
    }

    // 系统根目录 (Root /)
    roots.add(RenamedFileObject(File("/"), "Device Root (/)"))

    // 系统目录 (System)
    roots.add(RenamedFileObject(File("/system"), "System"))

    val virtualRoot = VirtualRootFileObject(roots)

    binding!!.loading.visibility = View.GONE
    binding!!.horizontalCroll.visibility = View.VISIBLE

    val tree =
        FileTree(requireContext()).apply {
          setIconProvider(IDEFileIconProvider(requireContext()))
          setOnFileClickListener(this@DataFileTreeFragment)
          setOnFileLongClickListener(this@DataFileTreeFragment)
          loadFiles(virtualRoot, false) // false 意味着隐藏顶级 "Root" 虚拟节点，直接展示盘符
          fileTreeView = this
        }

    binding!!
        .horizontalCroll
        .addView(
            tree,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

    tree.post { tree.restoreState(viewModel.savedState) }
      }

  // 虚拟根目录包装类 (用于容纳多个磁盘入口)
  private class VirtualRootFileObject(private val children: List<FileObject>) : FileObject {
    override fun listFiles(): List<FileObject> = children

    override fun isDirectory(): Boolean = true

    override fun isFile(): Boolean = false

    override fun getName(): String = "Root"

    override fun getParentFile(): FileObject? = null

    override fun getAbsolutePath(): String = "/"
  }

  // 重命名文件包装类 (为 / 目录赋予更友好的名字)
  class RenamedFileObject(val file: File, val displayName: String) : FileObject {
    override fun listFiles(): List<FileObject> {
      return file.listFiles()?.map { file(it) } ?: emptyList()
    }

    override fun isDirectory(): Boolean = file.isDirectory

    override fun isFile(): Boolean = file.isFile

    override fun getName(): String = displayName

    override fun getParentFile(): FileObject? = file.parentFile?.let { file(it) }

    override fun getAbsolutePath(): String = file.absolutePath
  }

  fun saveTreeState() {
    viewModel.saveState(fileTreeView)
  }

  override fun onClick(node: Node<FileObject>) =
      ClassResourceMonitor.trace(log) {
        val fObj = node.value
    // 解析出实际的 java.io.File 对象
    val targetFile =
        (fObj as? file)?.getNativeFile() ?: (fObj as? RenamedFileObject)?.file ?: return

    if (!targetFile.exists()) {
      Log.w(TAG, "File does not exist: ${targetFile.path}")
    }

    if (targetFile.isFile) {
      val event = FileClickEvent(targetFile)
      event.put(Context::class.java, requireContext())
      EventBus.getDefault().post(event)
    }
      }

  override fun onLongClick(node: Node<FileObject>) =
      ClassResourceMonitor.trace(log) {
        val fObj = node.value
    val targetFile =
        (fObj as? file)?.getNativeFile() ?: (fObj as? RenamedFileObject)?.file ?: return

    val event = FileLongClickEvent(targetFile)
    event.put(Context::class.java, requireContext())
    event.put(Node::class.java, node)
    EventBus.getDefault().post(event)
      }

  @Subscribe(threadMode = MAIN)
  fun onGetListFilesRequested(event: ListProjectFilesRequestEvent?) {
    if (!isVisible || context == null) return
    loadInternalApplicationData()
  }

  @Subscribe(threadMode = MAIN)
  fun onGetExpandTreeNodeRequest(event: ExpandTreeNodeRequestEvent) {
    if (!isVisible || context == null) return
    fileTreeView?.expandNode(event.node)
  }

  companion object {
    const val TAG = "editor.datafileTree"
    private val log = LoggerFactory.getLogger(DataFileTreeFragment::class.java)

    @JvmStatic fun newInstance(): DataFileTreeFragment = DataFileTreeFragment()
  }
}
