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
package com.itsaky.androidide.fragments.sidebar

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileIconProvider
import android.zero.studio.view.filetree.interfaces.FileLongClickListener
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.provider.file
import android.zero.studio.view.filetree.widget.FileTree
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.blankj.utilcode.util.SizeUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.LayoutEditorFileTreeBinding
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.events.ExpandTreeNodeRequestEvent
import com.itsaky.androidide.events.ListProjectFilesRequestEvent
import com.itsaky.androidide.models.FileExtension
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.utils.ClassResourceMonitor
import com.itsaky.androidide.utils.doOnApplyWindowInsets
import com.itsaky.androidide.viewmodel.FileTreeViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import org.slf4j.LoggerFactory

/**
 * Fragment that displays the project file tree.
 *
 * @author android_zero
 */
class FileTreeFragment : BottomSheetDialogFragment(), FileClickListener, FileLongClickListener {

  private var binding: LayoutEditorFileTreeBinding? = null
  private var fileTreeView: FileTree? = null

  private val viewModel by viewModels<FileTreeViewModel>(ownerProducer = { requireActivity() })

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    if (!EventBus.getDefault().isRegistered(this)) {
      EventBus.getDefault().register(this)
    }

    binding = LayoutEditorFileTreeBinding.inflate(inflater, container, false)
    binding?.root?.doOnApplyWindowInsets { view, insets, _, _ ->
      insets.getInsets(statusBars()).apply { view.updatePadding(top = top + SizeUtils.dp2px(8f)) }
    }
    return binding!!.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) =
      ClassResourceMonitor.trace(log) {
        super.onViewCreated(view, savedInstanceState)
        listProjectFiles()
      }

  override fun onDestroyView() {
    super.onDestroyView()
    EventBus.getDefault().unregister(this)

    saveTreeState()

    binding = null
    fileTreeView = null
  }

  fun saveTreeState() {
    viewModel.saveState(fileTreeView)
  }

  override fun onClick(node: Node<FileObject>) =
      ClassResourceMonitor.trace(log) {
        val targetFile = (node.value as? file)?.getNativeFile() ?: return@trace
        if (!targetFile.exists()) {
          return@trace
        }

        if (targetFile.isFile) {
          val event = FileClickEvent(targetFile)
          event.put(Context::class.java, requireContext())
          EventBus.getDefault().post(event)
        }
      }

  override fun onLongClick(node: Node<FileObject>) {
    val targetFile = (node.value as? file)?.getNativeFile() ?: return
    val event = FileLongClickEvent(targetFile)
    event.put(Context::class.java, requireContext())
    event.put(Node::class.java, node)
    EventBus.getDefault().post(event)
  }

  @Suppress("unused", "UNUSED_PARAMETER")
  @Subscribe(threadMode = MAIN)
  fun onGetListFilesRequested(event: ListProjectFilesRequestEvent?) {
    if (!isVisible || context == null) {
      return
    }
    listProjectFiles()
  }

  @Suppress("unused")
  @Subscribe(threadMode = MAIN)
  fun onGetExpandTreeNodeRequest(event: ExpandTreeNodeRequestEvent) {
    if (!isVisible || context == null) {
      return
    }
    fileTreeView?.expandNode(event.node)
  }

  fun listProjectFiles() =
      ClassResourceMonitor.trace(log) {
        if (binding == null) {
          return@trace
        }

        CoroutineScope(Dispatchers.Main).launch {
      binding!!.horizontalCroll.visibility = View.GONE
      binding!!.loading.visibility = View.VISIBLE

      val projectDirPath =
          withContext(Dispatchers.IO) { IProjectManager.getInstance().projectDirPath }
      val projectDir = File(projectDirPath)

      if (!projectDir.exists()) {
        binding!!.loading.visibility = View.GONE
        return@launch
      }

      val tree = createTreeView(file(projectDir))

      binding!!.horizontalCroll.removeAllViews()
      if (tree != null) {
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

          binding!!.horizontalCroll.visibility = View.VISIBLE
          binding!!.loading.visibility = View.GONE
        }
      }

  private fun createTreeView(rootObj: FileObject): FileTree? {
    val ctx = context ?: return null
    return FileTree(ctx).also {
      it.setIconProvider(IDEFileIconProvider(ctx))
      it.setOnFileClickListener(this)
      it.setOnFileLongClickListener(this)
      it.loadFiles(rootObj, true)
      fileTreeView = it
    }
  }

  companion object {
    const val TAG = "editor.fileTree"
    private val log = LoggerFactory.getLogger(FileTreeFragment::class.java)

    @JvmStatic
    fun newInstance(): FileTreeFragment {
      return FileTreeFragment()
    }
  }
}

/** 提供 IDE 专属的文件图标 */
class IDEFileIconProvider(private val context: Context) : FileIconProvider {
  private val chevronRight = ContextCompat.getDrawable(context, R.drawable.ic_chevron_right)
  private val expandMore = ContextCompat.getDrawable(context, R.drawable.ic_chevron_down)

  override fun getIcon(node: Node<FileObject>): Drawable? {
    val fileObj =
        (node.value as? file)?.getNativeFile()
            ?: return ContextCompat.getDrawable(context, R.drawable.ic_file_type_unknown)
    val iconRes =
        if (fileObj.isDirectory) {
          R.drawable.ic_folder
        } else {
          FileExtension.Factory.forFile(fileObj).icon
        }
    return ContextCompat.getDrawable(context, iconRes)
  }

  override fun getChevronRight(): Drawable? = chevronRight

  override fun getExpandMore(): Drawable? = expandMore
}
