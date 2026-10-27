package com.itsaky.androidide.fragments.git

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.zero.studio.view.filetree.interfaces.FileClickListener
import android.zero.studio.view.filetree.interfaces.FileLongClickListener
import android.zero.studio.view.filetree.interfaces.FileObject
import android.zero.studio.view.filetree.model.Node
import android.zero.studio.view.filetree.provider.file
import android.zero.studio.view.filetree.widget.FileTree
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.git24j.core.Repository
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.eventbus.events.filetree.FileClickEvent
import com.itsaky.androidide.eventbus.events.filetree.FileLongClickEvent
import com.itsaky.androidide.fragments.git.tree.FileTreeGitOps
import com.itsaky.androidide.preferences.internal.GeneralPreferences
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.provider.IDEFileIconProvider
import com.itsaky.androidide.ui.screen.editor.bottomsheet.DrawerWidthBreakpoint
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus

/**
 * 遵循 M3 Canonical Layouts 规范重构的 Git 与工程文件树一体化全屏页面
 * 1. 彻底去除每一个子页面顶部的固定伪标题栏；
 * 2. 宽度自适应：在手机上为单列满宽；在宽屏/折叠屏上裂变为 Supporting Pane 协同流式双栏。
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

  // 异步拉取 Git 元数据与工程目录
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

  // 响应式探测可用视口边界（决不写死宽度）
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val breakpoint = when {
      maxWidth < 600.dp -> DrawerWidthBreakpoint.COMPACT
      maxWidth < 840.dp -> DrawerWidthBreakpoint.MEDIUM
      else -> DrawerWidthBreakpoint.EXPANDED
    }

    Column(modifier = Modifier.fillMaxSize()) {
      // 极简快捷操作条（代替旧式厚重 TitleBar，高度仅 30dp）
      CompactGitActionBar(
          branchName = branchName,
          branches = branches,
          isBranchMenuOpen = isBranchMenuOpen,
          onBranchMenuToggle = { isBranchMenuOpen = it },
          onBranchSelected = { branch ->
            scope.launch(Dispatchers.IO) {
              FileTreeGitOps.switchBranch(branch)
              withContext(Dispatchers.Main) {
                branchName = branch
                fileTreeInstance?.reloadFileTreeSilently()
              }
            }
          },
          onRefresh = { fileTreeInstance?.reloadFileTreeSilently() },
          onLocate = {
            activity.getCurrentEditor()?.file?.let {
              fileTreeInstance?.locateFileAndScroll(it.absolutePath)
            }
          },
          onCollapseAll = { fileTreeInstance?.collapseAll() },
          onExpandAll = { fileTreeInstance?.expandAll() },
          isGitMenuOpen = isGitMenuOpen,
          onGitMenuToggle = { isGitMenuOpen = it },
          onGitAction = { op ->
            scope.launch(Dispatchers.IO) { op() }
          },
      )

      // 2. 核心主内容区：依据断点实现 Canonical 规范排版
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (isLoading) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
          }
        } else if (projectRoot == null) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未打开任何工程", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
          }
        } else {
          // 自适应分流：平板双栏 Supporting Pane；手机单栏 100% 满屏
          if (breakpoint == DrawerWidthBreakpoint.COMPACT) {
            FileTreeContainer(
                projectRoot = projectRoot!!,
                activity = activity,
                onInstanceReady = { fileTreeInstance = it },
                modifier = Modifier.fillMaxSize(),
            )
          } else {
            // M3 Canonical Supporting Pane: 双栏协同，宽度永不挤压
            Row(modifier = Modifier.fillMaxSize()) {
              // 主栏：文件树
              FileTreeContainer(
                  projectRoot = projectRoot!!,
                  activity = activity,
                  onInstanceReady = { fileTreeInstance = it },
                  modifier = Modifier.weight(1.2f).fillMaxHeight(),
              )

              // 边栏分隔线（12dp 物理槽间距体系）
              Box(
                  modifier = Modifier
                      .width(1.dp)
                      .fillMaxHeight()
                      .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
              )

              // 辅栏 (Supporting Pane)：实时 Git 状态与改动面板
              SupportingGitPanel(
                  activity = activity,
                  modifier = Modifier.weight(0.8f).fillMaxHeight().padding(12.dp),
              )
            }
          }
        }
      }
    }
  }
}

/**
 * 嵌入 AndroidView 的文件树隔离容器
 */
@Composable
private fun FileTreeContainer(
    projectRoot: File,
    activity: EditorHandlerActivity,
    onInstanceReady: (FileTree) -> Unit,
    modifier: Modifier = Modifier,
) {
  AndroidView(
      factory = { ctx ->
        FileTree(ctx).apply {
          layoutParams = FrameLayout.LayoutParams(
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
          loadFiles(file(projectRoot), true)
          onInstanceReady(this)
        }
      },
      modifier = modifier,
  )
}

/**
 * 极简动作条（无大标题，高度极致压缩至 30dp）
 */
@Composable
private fun CompactGitActionBar(
    branchName: String,
    branches: List<String>,
    isBranchMenuOpen: Boolean,
    onBranchMenuToggle: (Boolean) -> Unit,
    onBranchSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onLocate: () -> Unit,
    onCollapseAll: () -> Unit,
    onExpandAll: () -> Unit,
    isGitMenuOpen: Boolean,
    onGitMenuToggle: (Boolean) -> Unit,
    onGitAction: (suspend () -> Unit) -> Unit,
) {
  Surface(
      modifier = Modifier.fillMaxWidth().height(30.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      tonalElevation = 1.dp,
  ) {
    Row(
        modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box {
        TextButton(
            onClick = { onBranchMenuToggle(true) },
            modifier = Modifier.height(24.dp),
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
            onDismissRequest = { onBranchMenuToggle(false) },
        ) {
          branches.forEach { branch ->
            DropdownMenuItem(
                text = { Text(branch, fontSize = 12.sp) },
                onClick = {
                  onBranchMenuToggle(false)
                  onBranchSelected(branch)
                },
            )
          }
        }
      }

      Spacer(Modifier.width(2.dp))

      IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(14.dp))
      }
      IconButton(onClick = onLocate, modifier = Modifier.size(24.dp)) {
        Icon(Icons.Default.GpsFixed, contentDescription = "Locate", modifier = Modifier.size(13.dp))
      }
      IconButton(onClick = onCollapseAll, modifier = Modifier.size(24.dp)) {
        Icon(Icons.Default.ChevronRight, contentDescription = "Collapse All", modifier = Modifier.size(14.dp))
      }
      IconButton(onClick = onExpandAll, modifier = Modifier.size(24.dp)) {
        Icon(Icons.Default.ExpandMore, contentDescription = "Expand All", modifier = Modifier.size(14.dp))
      }

      Box {
        IconButton(onClick = { onGitMenuToggle(true) }, modifier = Modifier.size(24.dp)) {
          Icon(Icons.Default.MoreVert, contentDescription = "Git Ops", modifier = Modifier.size(14.dp))
        }
        DropdownMenu(
            expanded = isGitMenuOpen,
            onDismissRequest = { onGitMenuToggle(false) },
        ) {
          DropdownMenuItem(text = { Text("Stage All (暂存全部)", fontSize = 12.sp) }, onClick = { onGitMenuToggle(false); onGitAction { FileTreeGitOps.stageAll() } })
          DropdownMenuItem(text = { Text("Unstage All (取消暂存)", fontSize = 12.sp) }, onClick = { onGitMenuToggle(false); onGitAction { FileTreeGitOps.unstageAll() } })
          DropdownMenuItem(text = { Text("Fetch 远程拉取", fontSize = 12.sp) }, onClick = { onGitMenuToggle(false); onGitAction { FileTreeGitOps.fetch() } })
          DropdownMenuItem(text = { Text("Pull 合并更新", fontSize = 12.sp) }, onClick = { onGitMenuToggle(false); onGitAction { FileTreeGitOps.pull() } })
          DropdownMenuItem(text = { Text("Push 远程推送", fontSize = 12.sp) }, onClick = { onGitMenuToggle(false); onGitAction { FileTreeGitOps.push() } })
        }
      }
    }
  }
}

/**
 * 宽屏专属的 Supporting Pane：展示当前分支状态概览
 */
@Composable
private fun SupportingGitPanel(
    activity: EditorHandlerActivity,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier = modifier.clip(RoundedCornerShape(12.dp)),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
      Text(
          text = "Git 工作区概览",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.height(8.dp))
      Text(
          text = "宽屏协同模式已激活。在此视口下，文件树与工作区变动保持并行可见，避免跨 Tab 频繁翻找。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 11.sp,
          lineHeight = 15.sp,
      )
      Spacer(Modifier.height(12.dp))
      TextButton(
          onClick = { com.itsaky.androidide.fragments.git.GitScreenOpener.openGitUi(activity) },
          modifier = Modifier.align(Alignment.End),
      ) {
        Text("打开完整 Git 管理看板 →", fontSize = 11.sp)
      }
    }
  }
}