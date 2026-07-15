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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.catpuppyapp.puppygit.git.CommitDto
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Commit
import com.github.git24j.core.Repository
import com.github.git24j.core.Revwalk
import com.github.git24j.core.SortT
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitHistoryComposeBinding
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.OutlinedTextField

/**
 * Git 提交历史页面 —— 独立设计的 Compose UI。
 *
 * 不再套娃 puppygit 的 `CommitListScreen`（避免外层 mini-toolbar 与内层
 * puppygit TopAppBar + 返回按钮的"俄罗斯套娃"）。改为直接通过 git core
 * ([Libgit2Helper] / libgit2 绑定) 加载 HEAD 起的提交列表，用 AndroidIDE
 * 自己的 [MaterialTheme] 渲染卡片式列表。
 *
 * - 数据加载一比一复刻 puppygit：`Repository.open` → `repo.head().target()`
 *   → `Revwalk.create` + `sorting(TIME)` → `Commit.lookup`。
 * - 工具栏只放一个 Refresh 按钮，通过 [refreshTrigger] 触发 Compose 重新加载。
 * - 支持 Material3 `PullToRefreshBox` 下拉刷新。
 *
 * @author android_zero
 */
class GitHistoryFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitHistoryComposeBinding? = null
  private val binding
    get() = _binding!!

  private val refreshTrigger = mutableStateOf(0)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitHistoryComposeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      refreshTrigger.value++
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()

    val compose = setIdeContent {
      CommitHistoryContent(
          workdir = workdir,
          refreshKey = refreshTrigger,
          onRefresh = { refreshTrigger.value++ },
          onResetToCommit = ::resetToCommit,
          onCherryPick = ::cherryPickCommit,
          onCheckoutCommit = ::checkoutCommit,
          onCreateTag = ::createTagForCommit,
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  /** 硬重置到指定 commit (会丢失未提交的变更). */
  private fun resetToCommit(commitHash: String) {
    val workdir = resolveWorkspaceDirPath() ?: return
    emitGitOperation("history", "reset_to:$commitHash")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret = Libgit2Helper.resetHardToRevspec(repo, commitHash)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          Toast.makeText(requireContext(), "已重置到 $commitHash", Toast.LENGTH_SHORT).show()
          refreshTrigger.value++
        }
        result.onFailure {
          Toast.makeText(
                  requireContext(),
                  it.localizedMessage ?: "重置失败",
                  Toast.LENGTH_LONG,
              )
              .show()
        }
      }
    }
  }

  /** Cherry-pick 指定 commit 到当前分支. */
  private fun cherryPickCommit(commitHash: String) {
    val workdir = resolveWorkspaceDirPath() ?: return
    emitGitOperation("history", "cherrypick:$commitHash")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val settings = SettingsUtil.getSettingsSnapshot()
          val ret = Libgit2Helper.cherrypick(repo, commitHash, settings = settings)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          Toast.makeText(requireContext(), "Cherry-pick 完成: $commitHash", Toast.LENGTH_SHORT).show()
          refreshTrigger.value++
        }
        result.onFailure {
          Toast.makeText(
                  requireContext(),
                  it.localizedMessage ?: "Cherry-pick 失败",
                  Toast.LENGTH_LONG,
              )
              .show()
        }
      }
    }
  }

  /** Checkout 到指定 commit (detached HEAD). */
  private fun checkoutCommit(commitHash: String) {
    val workdir = resolveWorkspaceDirPath() ?: return
    emitGitOperation("history", "checkout_commit:$commitHash")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret = Libgit2Helper.checkoutCommitThenDetachHead(repo, commitHash)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          Toast.makeText(requireContext(), "已切到 commit $commitHash (detached)", Toast.LENGTH_SHORT).show()
          refreshTrigger.value++
        }
        result.onFailure {
          Toast.makeText(
                  requireContext(),
                  it.localizedMessage ?: "Checkout 失败",
                  Toast.LENGTH_LONG,
              )
              .show()
        }
      }
    }
  }

  /** 基于指定 commit 创建轻量 tag. */
  private fun createTagForCommit(commitHash: String, tagName: String) {
    if (tagName.isBlank()) return
    val workdir = resolveWorkspaceDirPath() ?: return
    emitGitOperation("history", "create_tag:$commitHash")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val oid = com.github.git24j.core.Oid.of(commitHash)
          val commit = Commit.lookup(repo, oid)
              ?: throw RuntimeException("找不到 commit $commitHash")
          try {
            Libgit2Helper.createTagLight(repo, tagName, commit, force = false)
          } finally {
            commit.close()
          }
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          Toast.makeText(requireContext(), "已创建 tag $tagName", Toast.LENGTH_SHORT).show()
        }
        result.onFailure {
          Toast.makeText(
                  requireContext(),
                  it.localizedMessage ?: "创建 tag 失败",
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

/** 提交历史 UI 状态。 */
private sealed interface HistoryUiState {
  data object Loading : HistoryUiState

  data object Empty : HistoryUiState

  data class Error(val message: String) : HistoryUiState

  data class Loaded(val commits: List<CommitDto>) : HistoryUiState
}

/**
 * 提交历史主体内容。负责按 [refreshKey] 加载数据并渲染 Loading / Empty /
 * Error / Loaded 四种状态。
 *
 * @param workdir 仓库工作目录绝对路径；为 null 表示当前没有打开项目
 * @param refreshKey 刷新触发器，值变化时重新加载
 * @param onRefresh 触发刷新（下拉刷新 / 重试按钮调用）
 * @param onResetToCommit 硬重置到指定 commit
 * @param onCherryPick Cherry-pick 指定 commit
 * @param onCheckoutCommit Checkout 到指定 commit (detached HEAD)
 */
@Composable
private fun CommitHistoryContent(
    workdir: String?,
    refreshKey: State<Int>,
    onRefresh: () -> Unit,
    onResetToCommit: (String) -> Unit,
    onCherryPick: (String) -> Unit,
    onCheckoutCommit: (String) -> Unit,
    onCreateTag: (commitHash: String, tagName: String) -> Unit,
) {
  if (workdir == null) {
    GitEmptyState("未打开项目")
    return
  }

  var uiState by remember { mutableStateOf<HistoryUiState>(HistoryUiState.Loading) }
  var isRefreshing by remember { mutableStateOf(false) }
  var confirmReset by remember { mutableStateOf<CommitDto?>(null) }
  var detailsCommit by remember { mutableStateOf<CommitDto?>(null) }
  var tagForCommit by remember { mutableStateOf<CommitDto?>(null) }

  LaunchedEffect(workdir, refreshKey.value) {
    if (uiState is HistoryUiState.Loaded) {
      isRefreshing = true
    } else {
      uiState = HistoryUiState.Loading
    }
    uiState = loadCommitHistory(workdir)
    isRefreshing = false
  }

  when (val state = uiState) {
    HistoryUiState.Loading -> GitLoadingState()
    HistoryUiState.Empty -> GitEmptyState("暂无提交历史")
    is HistoryUiState.Error -> GitErrorState(state.message, onRetry = onRefresh)
    is HistoryUiState.Loaded ->
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
          LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
          ) {
            items(state.commits, key = { it.oidStr }) { commit ->
              CommitItem(
                  commit = commit,
                  onResetToCommit = { confirmReset = commit },
                  onCherryPick = { onCherryPick(commit.oidStr) },
                  onCheckoutCommit = { onCheckoutCommit(commit.oidStr) },
                  onShowDetails = { detailsCommit = commit },
                  onCreateTag = { tagForCommit = commit },
              )
            }
          }
        }
  }

  // Reset 确认对话框 (危险操作)
  confirmReset?.let { commit ->
    AlertDialog(
        onDismissRequest = { confirmReset = null },
        title = { Text("确认重置") },
        text = {
          Text("将硬重置到 ${commit.shortOidStr}\n${commit.shortMsg}\n\n⚠️ 未提交的变更将丢失!")
        },
        confirmButton = {
          TextButton(onClick = {
            confirmReset = null
            onResetToCommit(commit.oidStr)
          }) { Text("重置") }
        },
        dismissButton = {
          TextButton(onClick = { confirmReset = null }) { Text("取消") }
        },
    )
  }

  // 提交详情对话框
  detailsCommit?.let { commit ->
    AlertDialog(
        onDismissRequest = { detailsCommit = null },
        title = { Text("提交详情") },
        text = {
          Column {
            DetailRow("Hash", commit.oidStr)
            DetailRow("作者", commit.author)
            DetailRow("邮箱", commit.email)
            DetailRow("日期", commit.dateTime)
            Spacer(Modifier.height(8.dp))
            Text("消息:", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(commit.msg, style = MaterialTheme.typography.bodySmall)
          }
        },
        confirmButton = {
          TextButton(onClick = { detailsCommit = null }) { Text("关闭") }
        },
    )
  }

  // 基于 commit 创建 tag 对话框
  tagForCommit?.let { commit ->
    var tagName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { tagForCommit = null },
        title = { Text("基于此提交创建 Tag") },
        text = {
          Column {
            Text("提交: ${commit.shortOidStr} ${commit.shortMsg}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tagName,
                onValueChange = { tagName = it },
                label = { Text("Tag 名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
          }
        },
        confirmButton = {
          TextButton(onClick = {
            val name = tagName.trim()
            tagForCommit = null
            if (name.isNotEmpty()) onCreateTag(commit.oidStr, name)
          }) { Text("创建") }
        },
        dismissButton = {
          TextButton(onClick = { tagForCommit = null }) { Text("取消") }
        },
    )
  }
}

/** 详情对话框中的键值对行. */
@Composable
private fun DetailRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    Text(
        text = "$label: ",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/**
 * 单条提交卡片：左侧短 hash（monospace, primary）+ 提交消息第一行（粗体）
 * + 作者名 + 日期（小字, onSurfaceVariant）。长按弹出操作菜单。
 */
@Composable
private fun CommitItem(
    commit: CommitDto,
    onResetToCommit: () -> Unit,
    onCherryPick: () -> Unit,
    onCheckoutCommit: () -> Unit,
    onShowDetails: () -> Unit,
    onCreateTag: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
      modifier =
          Modifier.fillMaxWidth().padding(horizontal = 12.dp).combinedClickable(
              onClick = { onShowDetails() },
              onLongClick = { menuExpanded = true },
          ),
      shape = RoundedCornerShape(GitSpacing.cardCorner),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(GitSpacing.cardPadding),
        verticalAlignment = Alignment.Top,
    ) {
      Text(
          text = commit.shortOidStr,
          style = MaterialTheme.typography.labelMedium,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(end = 12.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = commit.shortMsg.ifBlank { "(no message)" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = commit.author,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = commit.dateTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text("查看详情") },
            onClick = {
              menuExpanded = false
              onShowDetails()
            },
        )
        DropdownMenuItem(
            text = { Text("基于此提交创建 Tag") },
            onClick = {
              menuExpanded = false
              onCreateTag()
            },
        )
        DropdownMenuItem(
            text = { Text("重置到此 commit") },
            onClick = {
              menuExpanded = false
              onResetToCommit()
            },
        )
        DropdownMenuItem(
            text = { Text("Cherry-pick") },
            onClick = {
              menuExpanded = false
              onCherryPick()
            },
        )
        DropdownMenuItem(
            text = { Text("Checkout (detached)") },
            onClick = {
              menuExpanded = false
              onCheckoutCommit()
            },
        )
      }
    }
  }
}

/**
 * 在 IO 线程加载 HEAD 起的提交历史（最多 200 条，按时间倒序）。
 *
 * 一比一复刻 puppygit 的 git core 调用：[Repository.open] →
 * `repo.head().target()` → [Revwalk.create] + [Revwalk.sorting] +
 * [Revwalk.push] → [Commit.lookup]。
 */
private suspend fun loadCommitHistory(workdir: String): HistoryUiState =
    withContext(Dispatchers.IO) {
      runCatching {
            Repository.open(workdir).use { repo ->
              val headOid = repo.head()?.target()
              if (headOid == null || headOid.isNullOrEmptyOrZero) {
                return@use HistoryUiState.Empty
              }
              val revwalk = Revwalk.create(repo)
              try {
                revwalk.sorting(EnumSet.of(SortT.TIME))
                revwalk.push(headOid)
                val settings = SettingsUtil.getSettingsSnapshot()
                val commits = mutableListOf<CommitDto>()
                var oid = revwalk.next()
                var count = 0
                while (oid != null && count < 200) {
                  val commit = Commit.lookup(repo, oid)
                  if (commit != null) {
                    try {
                      val oidStr = oid.toString()
                      commits.add(
                          CommitDto(
                              oidStr = oidStr,
                              shortOidStr = oidStr.take(7),
                              shortMsg = commit.message().lines().firstOrNull() ?: "",
                              msg = commit.message(),
                              author = commit.author().name,
                              email = commit.author().email,
                              dateTime =
                                  Libgit2Helper.getDateTimeStrOfCommit(commit, settings),
                          ))
                    } finally {
                      commit.close()
                    }
                  }
                  oid = revwalk.next()
                  count++
                }
              } finally {
                revwalk.close()
              }
              if (commits.isEmpty()) HistoryUiState.Empty
              else HistoryUiState.Loaded(commits)
            }
          }
          .getOrElse { HistoryUiState.Error(it.message ?: "加载提交历史失败") }
    }
