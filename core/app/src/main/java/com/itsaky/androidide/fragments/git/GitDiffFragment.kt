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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.catpuppyapp.puppygit.git.StatusTypeEntrySaver
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Index
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitDiffComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Git Diff 查看页面 —— 独立设计的 Compose UI。
 *
 * 2a2-A 之前直接套娃 `TreeToTreeChangeListScreen`, 会导致双 toolbar。
 * 现在改为独立 Compose: 直接通过 [Libgit2Helper] 读取 workdir 变更
 * (Index vs Worktree = 未暂存) 与 index 变更 (HEAD vs Index = 已暂存),
 * 用 Material3 的 LazyColumn + 卡片渲染, 工具栏由 [BaseGitPageFragment]
 * 的 mini-toolbar 提供。
 *
 * @author android_zero
 */
class GitDiffFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitDiffComposeBinding? = null
  private val binding
    get() = _binding!!

  /**
   * 工具栏按钮通过自增此值触发 Compose 重新加载。
   * 作为 fragment 字段持有, 工具栏点击与 Compose 内的 LaunchedEffect 共享。
   */
  private val refreshTrigger = mutableStateOf(0)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitDiffComposeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    // 刷新
    addToolbarAction(R.drawable.ic_refresh_24, "刷新") {
      emitGitOperation("diff", "refresh")
      triggerRefresh()
    }

    addToolbarSeparator()

    // 暂存全部 (Stage All)
    addToolbarAction(R.drawable.ic_select_all_24, "暂存全部") {
      emitGitOperation("diff", "stage_all")
      stageAll()
    }

    // 取消暂存全部 (Unstage All)
    addToolbarAction(R.drawable.ic_remove_circle_outline_24, "取消暂存全部") {
      emitGitOperation("diff", "unstage_all")
      unstageAll()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()

    val compose = setIdeContent {
      DiffScreen(
          workdir = workdir,
          refreshTrigger = refreshTrigger,
          onStageFile = ::stageFile,
          onUnstageFile = ::unstageFile,
          onRevertFile = ::revertFile,
          onAddToGitignore = ::addToGitignore,
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  /** 暂存单个文件. */
  private fun stageFile(item: StatusTypeEntrySaver) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching {
        Repository.open(workdir).use { repo ->
          Libgit2Helper.stageStatusEntryAndWriteToDisk(repo, listOf(item))
        }
      }
      withContext(Dispatchers.Main) {
        ret.onSuccess { toast("已暂存 ${item.fileName}"); triggerRefresh() }
            .onFailure { toast(it.localizedMessage ?: "暂存失败") }
      }
    }
  }

  /** 取消暂存单个文件. */
  private fun unstageFile(path: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching {
        Repository.open(workdir).use { repo ->
          Libgit2Helper.unStageItems(repo, listOf(path))
        }
      }
      withContext(Dispatchers.Main) {
        ret.onSuccess { toast("已取消暂存"); triggerRefresh() }
            .onFailure { toast(it.localizedMessage ?: "取消暂存失败") }
      }
    }
  }

  /** 撤销单个文件到 index 版本 (不影响其他文件). */
  private fun revertFile(item: StatusTypeEntrySaver) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching {
        Repository.open(workdir).use { repo ->
          Libgit2Helper.revertFilesToIndexVersion(repo, listOf(item.relativePathUnderRepo))
        }
      }
      withContext(Dispatchers.Main) {
        ret.onSuccess { toast("已撤销 ${item.fileName}"); triggerRefresh() }
            .onFailure { toast(it.localizedMessage ?: "撤销失败") }
      }
    }
  }

  /**
   * 将文件加入 .gitignore 并从 git index 移除。
   * 复刻 puppygit GitIgnoreDialog 的逻辑: 追加路径到 .gitignore 文件 + removeFromGit。
   */
  private fun addToGitignore(item: StatusTypeEntrySaver) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    val relativePath = item.relativePathUnderRepo
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching {
        Repository.open(workdir).use { repo ->
          val ignoreFilePath = Libgit2Helper.getRepoIgnoreFilePathNoEndsWithSlash(repo, createIfNonExists = true)
          // 追加路径到 .gitignore
          val ignoreFile = java.io.File(ignoreFilePath)
          val isFile = !item.relativePathUnderRepo.endsWith("/")
          val entry = if (isFile) relativePath else "$relativePath/"
          val existing = if (ignoreFile.exists()) ignoreFile.readText() else ""
          if (!existing.lines().any { it.trim() == entry.trim() }) {
            ignoreFile.writeText(
                if (existing.isNotEmpty() && !existing.endsWith("\n")) "$existing\n$entry\n"
                else "$existing$entry\n"
            )
          }
          // 从 git index 移除 (如果已跟踪)
          repo.index().use { index ->
            Libgit2Helper.removeFromGit(index, relativePath, isFile)
            index.write()
          }
        }
      }
      withContext(Dispatchers.Main) {
        ret.onSuccess { toast("已加入 .gitignore: ${item.fileName}"); triggerRefresh() }
            .onFailure { toast(it.localizedMessage ?: "加入 .gitignore 失败") }
      }
    }
  }

  private fun triggerRefresh() {
    refreshTrigger.value++
  }

  /** 暂存全部 workdir 变更, 完成后刷新列表。 */
  private fun stageAll() {
    val workdir = resolveWorkspaceDirPath()
    if (workdir == null) {
      toast("No opened project")
      return
    }
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching {
        Repository.open(workdir).use { repo ->
          val r = Libgit2Helper.stageAll(repo, repoId = "")
          if (r.hasError()) {
            throw RuntimeException(r.msg)
          }
        }
      }
      withContext(Dispatchers.Main) {
        ret.onSuccess {
          toast("已暂存全部变更")
          triggerRefresh()
        }.onFailure {
          toast(it.localizedMessage ?: "Stage all failed")
        }
      }
    }
  }

  /** 取消暂存全部已暂存条目, 完成后刷新列表。 */
  private fun unstageAll() {
    val workdir = resolveWorkspaceDirPath()
    if (workdir == null) {
      toast("No opened project")
      return
    }
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching {
        Repository.open(workdir).use { repo ->
          val (_, staged) =
              Libgit2Helper.checkIndexIsEmptyAndGetIndexList(
                  repo = repo,
                  repoId = "",
                  onlyCheckEmpty = false,
              )
          val paths = staged.orEmpty().map { it.relativePathUnderRepo }
          if (paths.isNotEmpty()) {
            Libgit2Helper.unStageItems(repo, paths)
          }
        }
      }
      withContext(Dispatchers.Main) {
        ret.onSuccess {
          toast("已取消暂存全部")
          triggerRefresh()
        }.onFailure {
          toast(it.localizedMessage ?: "Unstage all failed")
        }
      }
    }
  }

  private fun toast(msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
  }

  override fun onDestroyView() {
    binding.gitContentContainer.removeAllViews()
    super.onDestroyView()
    _binding = null
  }
}

// ===================== 独立设计的 Compose UI =====================

/** Diff 页面 UI 状态。 */
private sealed class DiffUiState {
  object Loading : DiffUiState()

  object Empty : DiffUiState()

  class Error(val message: String) : DiffUiState()

  class Success(
      val unstaged: List<StatusTypeEntrySaver>,
      val staged: List<StatusTypeEntrySaver>,
  ) : DiffUiState()
}

private data class DiffSnapshot(
    val unstaged: List<StatusTypeEntrySaver>,
    val staged: List<StatusTypeEntrySaver>,
)

/**
 * 一比一复刻 puppygit git core 调用:
 * - workdir status list -> 未暂存变更 (Index vs Worktree)
 * - index list -> 已暂存变更 (HEAD vs Index)
 */
private fun loadDiffSnapshot(workdir: String): DiffSnapshot {
  return Repository.open(workdir).use { repo ->
    val statusList = Libgit2Helper.getWorkdirStatusList(repo)
    val unstaged = Libgit2Helper.getWorktreeChangeList(repo, statusList, repoId = "")
    val (_, staged) =
        Libgit2Helper.checkIndexIsEmptyAndGetIndexList(
            repo = repo,
            repoId = "",
            onlyCheckEmpty = false,
        )
    DiffSnapshot(unstaged = unstaged, staged = staged.orEmpty())
  }
}

@Composable
private fun DiffScreen(
    workdir: String?,
    refreshTrigger: MutableState<Int>,
    onStageFile: (StatusTypeEntrySaver) -> Unit,
    onUnstageFile: (String) -> Unit,
    onRevertFile: (StatusTypeEntrySaver) -> Unit,
    onAddToGitignore: (StatusTypeEntrySaver) -> Unit,
) {
  val tick = refreshTrigger.value
  var uiState by remember(workdir, tick) { mutableStateOf<DiffUiState>(DiffUiState.Loading) }
  var confirmRevert by remember { mutableStateOf<StatusTypeEntrySaver?>(null) }

  LaunchedEffect(workdir, tick) {
    if (workdir == null) {
      uiState = DiffUiState.Empty
      return@LaunchedEffect
    }
    uiState = DiffUiState.Loading
    val result = withContext(Dispatchers.IO) { runCatching { loadDiffSnapshot(workdir) } }
    uiState = result.fold(
        onSuccess = { snapshot ->
          if (snapshot.unstaged.isEmpty() && snapshot.staged.isEmpty()) {
            DiffUiState.Empty
          } else {
            DiffUiState.Success(snapshot.unstaged, snapshot.staged)
          }
        },
        onFailure = { DiffUiState.Error(it.localizedMessage ?: "加载变更失败") },
    )
  }

  when (val state = uiState) {
    is DiffUiState.Loading -> GitLoadingState()
    is DiffUiState.Empty -> GitEmptyState(message = "工作区干净，没有变更")
    is DiffUiState.Error ->
        GitErrorState(message = state.message, onRetry = { refreshTrigger.value++ })
    is DiffUiState.Success -> {
      val context = LocalContext.current
      DiffList(
          unstaged = state.unstaged,
          staged = state.staged,
          onFileClick = { path ->
            Toast.makeText(context, path, Toast.LENGTH_SHORT).show()
          },
          onStageFile = onStageFile,
          onUnstageFile = onUnstageFile,
          onRevertFileRequest = { confirmRevert = it },
          onAddToGitignore = onAddToGitignore,
      )
    }
  }

  confirmRevert?.let { item ->
    AlertDialog(
        onDismissRequest = { confirmRevert = null },
        title = { Text("确认撤销") },
        text = { Text("撤销 ${item.fileName} 的修改?\n该文件的未暂存变更将丢失!") },
        confirmButton = {
          TextButton(onClick = {
            confirmRevert = null
            onRevertFile(item)
          }) { Text("撤销") }
        },
        dismissButton = {
          TextButton(onClick = { confirmRevert = null }) { Text("取消") }
        },
    )
  }
}

@Composable
private fun DiffList(
    unstaged: List<StatusTypeEntrySaver>,
    staged: List<StatusTypeEntrySaver>,
    onFileClick: (String) -> Unit,
    onStageFile: (StatusTypeEntrySaver) -> Unit,
    onUnstageFile: (String) -> Unit,
    onRevertFileRequest: (StatusTypeEntrySaver) -> Unit,
    onAddToGitignore: (StatusTypeEntrySaver) -> Unit,
) {
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
  ) {
    if (unstaged.isNotEmpty()) {
      item(key = "header_unstaged") { SectionHeader("未暂存变更 (${unstaged.size})") }
      items(items = unstaged, key = { "u_" + it.relativePathUnderRepo }) { entry ->
        ChangeItemCard(
            entry = entry,
            isStaged = false,
            onClick = onFileClick,
            onStage = onStageFile,
            onUnstage = onUnstageFile,
            onRevertRequest = onRevertFileRequest,
            onAddToGitignore = onAddToGitignore,
        )
      }
    }
    if (staged.isNotEmpty()) {
      item(key = "header_staged") { SectionHeader("已暂存变更 (${staged.size})") }
      items(items = staged, key = { "s_" + it.relativePathUnderRepo }) { entry ->
        ChangeItemCard(
            entry = entry,
            isStaged = true,
            onClick = onFileClick,
            onStage = onStageFile,
            onUnstage = onUnstageFile,
            onRevertRequest = onRevertFileRequest,
            onAddToGitignore = onAddToGitignore,
        )
      }
    }
  }
}

@Composable
private fun SectionHeader(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
  )
}

@Composable
private fun ChangeItemCard(
    entry: StatusTypeEntrySaver,
    isStaged: Boolean,
    onClick: (String) -> Unit,
    onStage: (StatusTypeEntrySaver) -> Unit,
    onUnstage: (String) -> Unit,
    onRevertRequest: (StatusTypeEntrySaver) -> Unit,
    onAddToGitignore: (StatusTypeEntrySaver) -> Unit,
) {
  val path = entry.relativePathUnderRepo
  val (badge, color) = changeTypeMeta(entry.changeType)
  val slashIndex = path.lastIndexOf('/')
  val fileName = if (slashIndex >= 0) path.substring(slashIndex + 1) else path
  val dirPart = if (slashIndex > 0) path.substring(0, slashIndex + 1) else ""
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
      modifier =
          Modifier.fillMaxWidth().combinedClickable(
              onClick = { onClick(path) },
              onLongClick = { menuExpanded = true },
          ),
      shape = RoundedCornerShape(GitSpacing.cardCorner),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
          modifier =
              Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(color),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = badge,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (dirPart.isNotEmpty()) {
          Spacer(Modifier.size(2.dp))
          Text(
              text = dirPart,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
        }
      }
      DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        if (!isStaged) {
          DropdownMenuItem(
              text = { Text("暂存此文件") },
              onClick = { menuExpanded = false; onStage(entry) },
          )
          DropdownMenuItem(
              text = { Text("撤销修改") },
              onClick = { menuExpanded = false; onRevertRequest(entry) },
          )
          DropdownMenuItem(
              text = { Text("加入 .gitignore") },
              onClick = { menuExpanded = false; onAddToGitignore(entry) },
          )
        } else {
          DropdownMenuItem(
              text = { Text("取消暂存") },
              onClick = { menuExpanded = false; onUnstage(path) },
          )
        }
      }
    }
  }
}

/**
 * 变更类型 -> (单字符标识, 颜色)。
 *
 * puppygit 的 `changeType` 实际存的是全写字符串 ("Modified"/"New"/"Deleted"/"Renamed"/
 * "Conflict"/"Typechanged"); 这里同时兼容 spec 描述的单字符码 ("M"/"A"/"D"/"R"/"?"),
 * 颜色严格遵循 spec:
 * - M / Modified -> 黄色 (0xFFFFA000)
 * - A / New      -> 绿色 (0xFF4CAF50)
 * - D / Deleted  -> 红色 (0xFFF44336)
 * - R / Renamed  -> 蓝色 (0xFF2196F3)
 * - ? / Untracked-> 灰色 (0xFF9E9E9E)
 * - 其他         -> 紫色 (0xFF9C27B0)
 */
private fun changeTypeMeta(changeType: String?): Pair<String, Color> {
  val raw = changeType.orEmpty()
  return when (raw) {
    "Modified", "M" -> "M" to Color(0xFFFFA000)
    "New", "A" -> "A" to Color(0xFF4CAF50)
    "Deleted", "D" -> "D" to Color(0xFFF44336)
    "Renamed", "R" -> "R" to Color(0xFF2196F3)
    "?", "Untracked" -> "?" to Color(0xFF9E9E9E)
    "Conflict", "C" -> "C" to Color(0xFF9C27B0)
    "Typechanged", "T" -> "T" to Color(0xFF9C27B0)
    else -> (raw.firstOrNull()?.toString() ?: "?") to Color(0xFF9C27B0)
  }
}
