/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.fragments.git

import android.view.ViewGroup
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileLongClickListener
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.provider.file
import android.zero.studio.view.filetree.widget.FileTree
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.fragments.git.tree.FileTreeGitOps
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.provider.IDEFileIconProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus

/**
 * 纯 Compose 版项目文件树与 Git 工具栏页面。
 * 彻底消除 Fragment 生命周期，实现秒级挂载与 0 冗余内存开销。
 */
@Composable
fun GitProjectsScreen(activity: EditorHandlerActivity) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var branchName by remember { mutableStateOf("") }
  var isBranchMenuOpen by remember { mutableStateOf(false) }
  var isGitMenuOpen by remember { mutableStateOf(false) }
  var branches by remember { mutableStateOf<List<String>>(emptyList()) }
  var isLoading by remember { mutableStateOf(true) }
  var fileTreeInstance by remember { mutableStateOf<FileTree?>(null) }
  var projectRoot by remember { mutableStateOf<File?>(null) }

  // 加载分支与项目目录
  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      val dirPath = IProjectManager.getInstance().projectDirPath
      val root = dirPath?.let { File(it) }?.takeIf { it.exists() }
      projectRoot = root

      if (root != null) {
        val currentBranch = runCatching {
          Repository.open(root.absolutePath).use { repo ->
            if (repo.headDetached()) "HEAD (detached)"
            else repo.head()?.shorthand()?.removePrefix("refs/heads/") ?: "main"
          }
        }.getOrDefault("main")

        val allBranches = runCatching {
          FileTreeGitOps.listLocalBranches()
        }.getOrNull()

        withContext(Dispatchers.Main) {
          branchName = currentBranch
          branches = (allBranches as? FileTreeGitOps.GitOpResult.Ok)?.data?.map { it.shortName }.orEmpty()
          isLoading = false
        }
      } else {
        withContext(Dispatchers.Main) { isLoading = false }
      }
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // 顶部 Git & 文件树快捷工具栏
    Surface(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
      Row(
          modifier = Modifier
              .fillMaxSize()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        // 当前分支选择器
        Box {
          TextButton(
              onClick = { isBranchMenuOpen = true },
              modifier = Modifier.height(28.dp),
          ) {
            Text(
                text = branchName.ifBlank { "Git" },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
          }
          DropdownMenu(
              expanded = isBranchMenuOpen,
              onDismissRequest = { isBranchMenuOpen = false },
          ) {
            branches.forEach { branch ->
              DropdownMenuItem(
                  text = { Text(branch, fontSize = 12.sp) },
                  onClick = {
                    isBranchMenuOpen = false
                    scope.launch(Dispatchers.IO) {
                      FileTreeGitOps.switchBranch(branch)
                      withContext(Dispatchers.Main) {
                        branchName = branch
                        fileTreeInstance?.reloadFileTreeSilently()
                      }
                    }
                  },
              )
            }
          }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 刷新
        IconButton(
            onClick = { fileTreeInstance?.reloadFileTreeSilently() },
            modifier = Modifier.size(28.dp),
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
        }

        // 定位当前文件
        IconButton(
            onClick = {
              val current = activity.getCurrentEditor()?.file
              if (current != null) fileTreeInstance?.locateFileAndScroll(current.absolutePath)
            },
            modifier = Modifier.size(28.dp),
        ) {
          Icon(Icons.Default.GpsFixed, contentDescription = "Locate", modifier = Modifier.size(15.dp))
        }

        // 全部折叠
        IconButton(
            onClick = { fileTreeInstance?.collapseAll() },
            modifier = Modifier.size(28.dp),
        ) {
          Icon(Icons.Default.ChevronRight, contentDescription = "Collapse All", modifier = Modifier.size(16.dp))
        }

        // 全部展开
        IconButton(
            onClick = { fileTreeInstance?.expandAll() },
            modifier = Modifier.size(28.dp),
        ) {
          Icon(Icons.Default.ExpandMore, contentDescription = "Expand All", modifier = Modifier.size(16.dp))
        }

        // Git 仓库高级操作菜单
        Box {
          IconButton(
              onClick = { isGitMenuOpen = true },
              modifier = Modifier.size(28.dp),
          ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Git Menu", modifier = Modifier.size(16.dp))
          }
          DropdownMenu(
              expanded = isGitMenuOpen,
              onDismissRequest = { isGitMenuOpen = false },
          ) {
            DropdownMenuItem(
                text = { Text("Stage All (暂存全部)", fontSize = 12.sp) },
                onClick = {
                  isGitMenuOpen = false
                  scope.launch(Dispatchers.IO) { FileTreeGitOps.stageAll() }
                },
            )
            DropdownMenuItem(
                text = { Text("Unstage All (取消暂存)", fontSize = 12.sp) },
                onClick = {
                  isGitMenuOpen = false
                  scope.launch(Dispatchers.IO) { FileTreeGitOps.unstageAll() }
                },
            )
            DropdownMenuItem(
                text = { Text("Fetch 远程更新", fontSize = 12.sp) },
                onClick = {
                  isGitMenuOpen = false
                  scope.launch(Dispatchers.IO) { FileTreeGitOps.fetch() }
                },
            )
            DropdownMenuItem(
                text = { Text("Pull 拉取合并", fontSize = 12.sp) },
                onClick = {
                  isGitMenuOpen = false
                  scope.launch(Dispatchers.IO) { FileTreeGitOps.pull() }
                },
            )
            DropdownMenuItem(
                text = { Text("Push 推送提交", fontSize = 12.sp) },
                onClick = {
                  isGitMenuOpen = false
                  scope.launch(Dispatchers.IO) { FileTreeGitOps.push() }
                },
            )
          }
        }
      }
    }

    // 文件树内容展示
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
      } else if (projectRoot == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("未打开任何工程", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
      } else {
        AndroidView(
            factory = { ctx ->
              FileTree(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setIconProvider(IDEFileIconProvider(ctx))
                setAutoExpandSingleChildFolders(GeneralPreferences.treeAutoExpandSingleChild)
                setOnFileClickListener(
                    object : FileClickListener {
                      override fun onClick(node: Node<FileObject>) {
                        val nativeFile = IDEFileIconProvider.extractNativeFile(node.value) ?: return
                        if (nativeFile.isFile) {
                          EventBus.getDefault().post(FileClickEvent(nativeFile).apply { put(Context::class.java, activity) })
                        }
                      }
                    }
                )
                setOnFileLongClickListener(
                    object : FileLongClickListener {
                      override fun onLongClick(node: Node<FileObject>) {
                        val nativeFile = IDEFileIconProvider.extractNativeFile(node.value) ?: return
                        EventBus.getDefault().post(FileLongClickEvent(nativeFile).apply {
                          put(Context::class.java, activity)
                          put(Node::class.java, node)
                        })
                      }
                    }
                )
                loadFiles(file(projectRoot!!), true)
                fileTreeInstance = this
              }
            },
            modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}