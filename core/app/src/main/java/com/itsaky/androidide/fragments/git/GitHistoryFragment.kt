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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlinx.coroutines.withContext

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

  /**
   * 刷新触发器。由 mini-toolbar 的 Refresh 按钮递增，Compose 内的
   * [LaunchedEffect] 观察 [State.value] 变化后重新加载提交历史。
   *
   * 在 Fragment 字段处创建（非 composition 内），在 composition 中读取
   * `.value` 仍会被快照追踪，属于标准的 state hoisting 用法。
   */
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
    // GitRuntimeBootstrap.ensureLoaded() 已在 BaseGitPageFragment.onViewCreated 中调用
    val workdir = resolveWorkspaceDirPath()

    val compose = setIdeContent {
      CommitHistoryContent(
          workdir = workdir,
          refreshKey = refreshTrigger,
          onRefresh = { refreshTrigger.value++ },
      )
    }
    binding.gitContentContainer.addView(compose)
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
 */
@Composable
private fun CommitHistoryContent(
    workdir: String?,
    refreshKey: State<Int>,
    onRefresh: () -> Unit,
) {
  if (workdir == null) {
    GitEmptyState("未打开项目")
    return
  }

  var uiState by remember { mutableStateOf<HistoryUiState>(HistoryUiState.Loading) }
  var isRefreshing by remember { mutableStateOf(false) }

  LaunchedEffect(workdir, refreshKey.value) {
    // 首次加载 / 从非 Loaded 状态重试：全屏 Loading；
    // 已有列表时刷新：保留列表，仅显示下拉刷新指示器。
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
            items(state.commits, key = { it.oidStr }) { commit -> CommitItem(commit) }
          }
        }
  }
}

/**
 * 单条提交卡片：左侧短 hash（monospace, primary）+ 提交消息第一行（粗体）
 * + 作者名 + 日期（小字, onSurfaceVariant）。
 */
@Composable
private fun CommitItem(commit: CommitDto) {
  Card(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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
