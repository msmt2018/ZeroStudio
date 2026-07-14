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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    val compose = setIdeContent { DiffScreen(workdir, refreshTrigger) }
    binding.gitContentContainer.addView(compose)
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
private fun DiffScreen(workdir: String?, refreshTrigger: MutableState<Int>) {
  val tick = refreshTrigger.value
  var uiState by remember(workdir, tick) { mutableStateOf<DiffUiState>(DiffUiState.Loading) }

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
            // diff 内容查看后续实现, 这里暂时只 Toast 提示文件路径
            Toast.makeText(context, path, Toast.LENGTH_SHORT).show()
          },
      )
    }
  }
}

@Composable
private fun DiffList(
    unstaged: List<StatusTypeEntrySaver>,
    staged: List<StatusTypeEntrySaver>,
    onFileClick: (String) -> Unit,
) {
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
  ) {
    if (unstaged.isNotEmpty()) {
      item(key = "header_unstaged") { SectionHeader("未暂存变更 (${unstaged.size})") }
      items(items = unstaged, key = { "u_" + it.relativePathUnderRepo }) { entry ->
        ChangeItemCard(entry, onFileClick)
      }
    }
    if (staged.isNotEmpty()) {
      item(key = "header_staged") { SectionHeader("已暂存变更 (${staged.size})") }
      items(items = staged, key = { "s_" + it.relativePathUnderRepo }) { entry ->
        ChangeItemCard(entry, onFileClick)
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
private fun ChangeItemCard(entry: StatusTypeEntrySaver, onClick: (String) -> Unit) {
  val path = entry.relativePathUnderRepo
  val (badge, color) = changeTypeMeta(entry.changeType)
  val slashIndex = path.lastIndexOf('/')
  val fileName = if (slashIndex >= 0) path.substring(slashIndex + 1) else path
  val dirPart = if (slashIndex > 0) path.substring(0, slashIndex + 1) else ""

  Card(
      modifier = Modifier.fillMaxWidth().clickable { onClick(path) },
      shape = RoundedCornerShape(GitSpacing.cardCorner),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      // 变更类型色块 + 单字符标识
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
