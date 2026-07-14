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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.git.StatusTypeEntrySaver
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitConflictsComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 冲突解决页面 —— 独立设计的 Compose UI。
 *
 * UI 结构 (参考 core/git/UI设计概念图/冲突解决+协作.svg):
 * - 有冲突时: 顶部红色警告条 + 冲突文件卡片列表, 每个卡片有
 *   "保留当前 (Yours)" / "保留传入 (Theirs)" 两个按钮
 * - 无冲突时: 绿色"无冲突"空状态
 *
 * git core 调用一比一复刻 puppygit:
 * - [Libgit2Helper.getWorkdirStatusList] + [Libgit2Helper.getWorktreeChangeList]
 *   过滤 `changeType == Cons.gitStatusConflict` 得冲突文件
 * - [Libgit2Helper.mergeAccept] 解决冲突 (acceptTheirs=true 保留传入, false 保留当前)
 *
 * @author android_zero
 */
class GitConflictsFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitConflictsComposeBinding? = null
  private val binding
    get() = _binding!!

  private val refreshTrigger = mutableStateOf(0)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitConflictsComposeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_download_24, getString(R.string.accept_theirs)) {
      emitGitOperation("conflicts", "accept_theirs_all")
      acceptAll(acceptTheirs = true)
    }
    addToolbarAction(R.drawable.ic_arrow_upward_24, getString(R.string.accept_ours)) {
      emitGitOperation("conflicts", "accept_ours_all")
      acceptAll(acceptTheirs = false)
    }
    addToolbarSeparator()
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      emitGitOperation("conflicts", "refresh")
      triggerRefresh()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()
    val compose = setIdeContent {
      ConflictsContent(
          workdir = workdir,
          refreshTrigger = refreshTrigger,
          onRefresh = ::triggerRefresh,
          onAcceptFile = ::acceptFile,
          onAcceptAll = ::acceptAll,
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  private fun triggerRefresh() {
    refreshTrigger.value++
  }

  /** 解决单个文件的冲突. */
  private fun acceptFile(path: String, acceptTheirs: Boolean) {
    withRepo { repo ->
      val ret = Libgit2Helper.mergeAccept(repo, listOf(path), acceptTheirs)
      if (ret.hasError()) throw RuntimeException(ret.msg)
    }
  }

  /** 解决全部冲突. */
  private fun acceptAll(acceptTheirs: Boolean) {
    withRepo { repo ->
      // 先加载当前冲突列表
      val conflicts = loadConflictList(repo)
      if (conflicts.isEmpty()) {
        throw RuntimeException("No conflicts")
      }
      val pathSpec = conflicts.map { it.relativePathUnderRepo }
      val ret = Libgit2Helper.mergeAccept(repo, pathSpec, acceptTheirs)
      if (ret.hasError()) throw RuntimeException(ret.msg)
    }
  }

  private fun withRepo(action: (Repository) -> Unit) {
    val projectDir = resolveWorkspaceDirPath()
    if (projectDir == null) {
      Toast.makeText(context, "No opened project", Toast.LENGTH_SHORT).show()
      return
    }

    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching { Repository.open(projectDir).use(action) }
      withContext(Dispatchers.Main) {
        ret.onSuccess {
          Toast.makeText(context, "冲突已解决", Toast.LENGTH_SHORT).show()
          triggerRefresh()
        }
        ret.onFailure {
          Toast.makeText(
                  context,
                  it.localizedMessage ?: "Conflict operation failed",
                  Toast.LENGTH_LONG,
              )
              .show()
        }
      }
    }
  }

  override fun onDestroyView() {
    binding.gitContentContainer.removeAllViews()
    super.onDestroyView()
    _binding = null
  }
}

// ===================== 独立设计的 Compose UI =====================

private sealed interface ConflictsUiState {
  data object Loading : ConflictsUiState

  data object NoProject : ConflictsUiState

  data class Error(val message: String) : ConflictsUiState

  data class Loaded(val conflicts: List<StatusTypeEntrySaver>) : ConflictsUiState
}

@Composable
private fun ConflictsContent(
    workdir: String?,
    refreshTrigger: State<Int>,
    onRefresh: () -> Unit,
    onAcceptFile: (String, Boolean) -> Unit,
    onAcceptAll: (Boolean) -> Unit,
) {
  if (workdir == null) {
    GitEmptyState("未打开项目")
    return
  }

  var uiState by remember { mutableStateOf<ConflictsUiState>(ConflictsUiState.Loading) }

  LaunchedEffect(workdir, refreshTrigger.value) {
    uiState = ConflictsUiState.Loading
    uiState = loadConflicts(workdir)
  }

  when (val state = uiState) {
    ConflictsUiState.Loading -> GitLoadingState()
    ConflictsUiState.NoProject -> GitEmptyState("未打开项目")
    is ConflictsUiState.Error -> GitErrorState(state.message, onRetry = onRefresh)
    is ConflictsUiState.Loaded -> {
      if (state.conflicts.isEmpty()) {
        // 无冲突: 绿色友好提示
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
          Icon(
              imageVector = Icons.Outlined.CheckCircle,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
          )
          Spacer(Modifier.height(12.dp))
          Text(
              text = "没有冲突",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          )
        }
      } else {
        ConflictsList(
            conflicts = state.conflicts,
            onAcceptFile = onAcceptFile,
            onAcceptAll = onAcceptAll,
        )
      }
    }
  }
}

@Composable
private fun ConflictsList(
    conflicts: List<StatusTypeEntrySaver>,
    onAcceptFile: (String, Boolean) -> Unit,
    onAcceptAll: (Boolean) -> Unit,
) {
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // 顶部警告条
    item(key = "warning_header") {
      ConflictWarningHeader(
          count = conflicts.size,
          onAcceptAllTheirs = { onAcceptAll(true) },
          onAcceptAllOurs = { onAcceptAll(false) },
      )
    }
    // 冲突文件列表
    items(conflicts, key = { it.relativePathUnderRepo }) { entry ->
      ConflictFileCard(
          entry = entry,
          onAcceptTheirs = { onAcceptFile(entry.relativePathUnderRepo, true) },
          onAcceptOurs = { onAcceptFile(entry.relativePathUnderRepo, false) },
      )
    }
  }
}

/** 顶部红色警告条: 冲突数量 + 全部保留当前/传入按钮. */
@Composable
private fun ConflictWarningHeader(
    count: Int,
    onAcceptAllTheirs: () -> Unit,
    onAcceptAllOurs: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
          ),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Outlined.WarningAmber,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(28.dp),
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = "$count 个冲突待解决",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = "请逐个选择保留方案, 或全部接受",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
        )
      }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      TextButton(onClick = onAcceptAllOurs, modifier = Modifier.weight(1f)) {
        Text("全部保留当前")
      }
      TextButton(onClick = onAcceptAllTheirs, modifier = Modifier.weight(1f)) {
        Text("全部保留传入")
      }
    }
  }
}

/**
 * 单个冲突文件卡片: 文件名 + 路径 + 冲突标识 + 两个操作按钮.
 *
 * 蓝色 = Yours (当前/HEAD), 橙色 = Theirs (传入/远程).
 */
@Composable
private fun ConflictFileCard(
    entry: StatusTypeEntrySaver,
    onAcceptTheirs: () -> Unit,
    onAcceptOurs: () -> Unit,
) {
  val path = entry.relativePathUnderRepo
  val slashIndex = path.lastIndexOf('/')
  val fileName = if (slashIndex >= 0) path.substring(slashIndex + 1) else path
  val dirPart = if (slashIndex > 0) path.substring(0, slashIndex + 1) else ""

  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        // 冲突标识色块
        Box(
            modifier =
                Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF9C27B0)),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = "!",
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
      Spacer(Modifier.height(12.dp))
      // 两个操作按钮
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // 保留当前 (Yours / HEAD) - 蓝色
        ConflictActionButton(
            label = "保留当前",
            sublabel = "Yours / HEAD",
            color = Color(0xFF64B5F6),
            onClick = onAcceptOurs,
            modifier = Modifier.weight(1f),
        )
        // 保留传入 (Theirs / Remote) - 橙色
        ConflictActionButton(
            label = "保留传入",
            sublabel = "Theirs / Remote",
            color = Color(0xFFFFB74D),
            onClick = onAcceptTheirs,
            modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

/** 冲突解决操作按钮: 带颜色的圆角卡片按钮. */
@Composable
private fun ConflictActionButton(
    label: String,
    sublabel: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
      colors =
          CardDefaults.cardColors(
              containerColor = color.copy(alpha = 0.1f),
          ),
      border =
          androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = color,
      )
      Text(
          text = sublabel,
          style = MaterialTheme.typography.labelSmall,
          color = color.copy(alpha = 0.6f),
      )
    }
  }
}

/** 加载冲突文件列表 (IO 线程). */
private suspend fun loadConflicts(workdir: String): ConflictsUiState =
    withContext(Dispatchers.IO) {
      runCatching {
            Repository.open(workdir).use { repo -> loadConflictList(repo) }
          }
          .map { ConflictsUiState.Loaded(it) }
          .getOrElse { ConflictsUiState.Error(it.localizedMessage ?: "加载冲突列表失败") }
    }

/** 读取冲突文件列表 (过滤 changeType == Conflict). */
private fun loadConflictList(repo: Repository): List<StatusTypeEntrySaver> =
    Libgit2Helper.getWorktreeChangeList(repo, Libgit2Helper.getWorkdirStatusList(repo), "")
        .filter { it.changeType == Cons.gitStatusConflict }
