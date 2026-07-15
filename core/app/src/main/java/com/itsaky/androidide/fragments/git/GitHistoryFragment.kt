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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.catpuppyapp.puppygit.constants.Cons
import com.catpuppyapp.puppygit.git.CommitDto
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.FsUtils
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Commit
import com.github.git24j.core.Repository
import com.github.git24j.core.Reset
import com.github.git24j.core.Revwalk
import com.github.git24j.core.SortT
import com.github.git24j.core.Tree
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitHistoryComposeBinding
import java.io.File
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
          onCopyHash = ::copyCommitHash,
          onSavePatch = ::savePatchForCommit,
          onCherryPickWithOptions = ::cherryPickWithOptions,
          onSquashToCommit = ::squashToCommit,
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  /** 复制 commit hash 到剪贴板. */
  private fun copyCommitHash(commitHash: String) {
    val ctx = context ?: return
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("commit_hash", commitHash))
    Toast.makeText(ctx, "已复制 $commitHash", Toast.LENGTH_SHORT).show()
  }

  /**
   * 重置到指定 commit。对齐 puppygit CommitListScreen 的 ResetDialog:
   * 支持 soft / mixed / hard 三种模式。
   */
  private fun resetToCommit(commitHash: String, resetType: Reset.ResetT) {
    val workdir = resolveWorkspaceDirPath() ?: return
    emitGitOperation("history", "reset_${resetType.name.lowercase()}_to:$commitHash")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret = Libgit2Helper.resetToRevspec(repo, commitHash, resetType)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          Toast.makeText(requireContext(), "已重置 (${resetType.name}) 到 $commitHash", Toast.LENGTH_SHORT).show()
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

  /**
   * 基于指定 commit 创建 tag。支持轻量 tag 和附注 tag。
   * @param commitHash 目标 commit hash
   * @param tagName tag 名称
   * @param message 附注消息, 为空则创建轻量 tag
   */
  private fun createTagForCommit(commitHash: String, tagName: String, message: String) {
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
            if (message.isBlank()) {
              Libgit2Helper.createTagLight(repo, tagName, commit, force = false)
            } else {
              val (username, email) = Libgit2Helper.getGitUserNameAndEmailFromRepo(repo)
              val settings = SettingsUtil.getSettingsSnapshot()
              Libgit2Helper.createTagAnnotated(
                  repo, tagName, commit, message, username, email, force = false, settings = settings,
              )
            }
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

  /**
   * 保存 commit 与其父提交之间的差异为 patch 文件。对齐 puppygit
   * CommitListScreen 的 create_patch 操作: 调用
   * [Libgit2Helper.savePatchToFileAndGetContent] 写入 patch 文件.
   *
   * @param commitHash 目标 commit hash
   * @param parentHash 父 commit hash (用于 diff). 若为空且 commit 无父, 则报错.
   */
  private fun savePatchForCommit(commitHash: String, parentHash: String) {
    if (parentHash.isBlank()) {
      toast("此提交无父提交, 无法生成 patch")
      return
    }
    val workdir = resolveWorkspaceDirPath() ?: return
    emitGitOperation("history", "save_patch:$commitHash")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val repoName = File(workdir).name
          val outFile = FsUtils.Patch.newPatchFile(repoName, parentHash, commitHash)
          val tree1 = Libgit2Helper.resolveTree(repo, parentHash)
          val tree2 = Libgit2Helper.resolveTree(repo, commitHash)
          val ret = Libgit2Helper.savePatchToFileAndGetContent(
              outFile = outFile,
              repo = repo,
              tree1 = tree1,
              tree2 = tree2,
              fromTo = Cons.gitDiffFromTreeToTree,
              reverse = false,
              treeToWorkTree = false,
              returnDiffContent = false,
          )
          if (ret.hasError()) throw RuntimeException(ret.msg)
          outFile.absolutePath
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          Toast.makeText(requireContext(), "已保存 patch: $it", Toast.LENGTH_LONG).show()
        }
        result.onFailure {
          Toast.makeText(
                  requireContext(),
                  it.localizedMessage ?: "保存 patch 失败",
                  Toast.LENGTH_LONG,
              )
              .show()
        }
      }
    }
  }

  /**
   * Cherry-pick 指定 commit (带父提交选择 / autoCommit 选项).
   * 对齐 puppygit CommitListScreen: 当目标 commit 是 merge commit (多个父提交)
   * 时需指定 mainline; autoCommit=false 时只 pick 到 index 不创建提交.
   *
   * @param commitHash 目标 commit hash
   * @param parentHash 父 commit hash, 用于 mainline 选择 (单父提交时可传空)
   * @param autoCommit true=自动提交, false=只 pick 到 index
   */
  private fun cherryPickWithOptions(commitHash: String, parentHash: String, autoCommit: Boolean) {
    val workdir = resolveWorkspaceDirPath() ?: return
    emitGitOperation("history", "cherrypick_opts:$commitHash")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val settings = SettingsUtil.getSettingsSnapshot()
          val ret = Libgit2Helper.cherrypick(
              repo = repo,
              targetCommitFullHash = commitHash,
              parentCommitFullHash = parentHash,
              autoCommit = autoCommit,
              settings = settings,
          )
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          val msg = if (autoCommit) "Cherry-pick 完成: $commitHash"
              else "Cherry-pick 到 index 完成 (未提交): $commitHash"
          Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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

  /**
   * Squash HEAD 到目标 commit (含 target, 不含 HEAD). 对齐 puppygit
   * CommitListScreen 的 squash 操作: soft reset HEAD 到 target, 然后创建一个
   * 新提交合并所有变更.
   *
   * 前置: 仅 HEAD 视图可用 (本 Fragment 始终从 HEAD 加载, 故恒满足);
   * 仓库状态必须 NONE; index 为空 (除非 force); 用户名/邮箱必须已设置.
   *
   * @param targetHash 目标 commit hash
   * @param commitMsg 新提交消息, 为空时使用自动生成的 "Squash: target..head"
   * @param force true 时即使 index 有变更也强制执行
   */
  private fun squashToCommit(targetHash: String, commitMsg: String, force: Boolean) {
    val workdir = resolveWorkspaceDirPath() ?: return
    emitGitOperation("history", "squash:$targetHash")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          // 前置检查: 用户名/邮箱/状态/冲突
          val checkRet = Libgit2Helper.squashCommitsCheckBeforeShowDialog(
              repo, targetHash, isShowingCommitListForHEAD = true,
          )
          if (checkRet.hasError()) throw RuntimeException(checkRet.msg)
          val data = checkRet.data ?: throw RuntimeException("squash 数据为空")
          // 执行前检查: index 状态
          val execCheck = Libgit2Helper.squashCommitsCheckBeforeExecute(repo, force)
          if (execCheck.hasError()) {
            throw RuntimeException("index 含未提交变更, 请勾选 force 或先提交/暂存")
          }
          val msg = if (commitMsg.isBlank()) {
            Libgit2Helper.squashCommitsGenCommitMsg(
                Libgit2Helper.getShortOidStrByFull(targetHash),
                Libgit2Helper.getShortOidStrByFull(data.headFullOid),
            )
          } else commitMsg
          val ret = Libgit2Helper.squashCommits(
              repo = repo,
              targetFullOidStr = targetHash,
              commitMsg = msg,
              username = data.username,
              email = data.email,
              currentBranchFullNameOrHEAD = data.headFullName.ifBlank { "HEAD" },
              settings = SettingsUtil.getSettingsSnapshot(),
          )
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          Toast.makeText(requireContext(), "已 Squash 到 $targetHash", Toast.LENGTH_SHORT).show()
          refreshTrigger.value++
        }
        result.onFailure {
          Toast.makeText(
                  requireContext(),
                  it.localizedMessage ?: "Squash 失败",
                  Toast.LENGTH_LONG,
              )
              .show()
        }
      }
    }
  }

  private fun toast(msg: String) {
    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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
 * @param onResetToCommit 重置到指定 commit (指定 soft/mixed/hard 模式)
 * @param onCherryPick Cherry-pick 指定 commit (无选项, 直接自动提交)
 * @param onCheckoutCommit Checkout 到指定 commit (detached HEAD)
 * @param onCreateTag 基于指定 commit 创建 tag (附注消息为空则轻量 tag)
 * @param onCopyHash 复制 commit hash 到剪贴板
 * @param onSavePatch 保存 commit 与父提交之间的 diff 为 patch 文件
 * @param onCherryPickWithOptions Cherry-pick 带父提交选择 / autoCommit 选项
 * @param onSquashToCommit Squash HEAD 到指定 commit
 */
@Composable
private fun CommitHistoryContent(
    workdir: String?,
    refreshKey: State<Int>,
    onRefresh: () -> Unit,
    onResetToCommit: (String, Reset.ResetT) -> Unit,
    onCherryPick: (String) -> Unit,
    onCheckoutCommit: (String) -> Unit,
    onCreateTag: (commitHash: String, tagName: String, message: String) -> Unit,
    onCopyHash: (String) -> Unit,
    onSavePatch: (commitHash: String, parentHash: String) -> Unit,
    onCherryPickWithOptions: (commitHash: String, parentHash: String, autoCommit: Boolean) -> Unit,
    onSquashToCommit: (targetHash: String, commitMsg: String, force: Boolean) -> Unit,
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
  var patchForCommit by remember { mutableStateOf<CommitDto?>(null) }
  var cherryPickForCommit by remember { mutableStateOf<CommitDto?>(null) }
  var squashTarget by remember { mutableStateOf<CommitDto?>(null) }
  var filterText by remember { mutableStateOf("") }

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
    is HistoryUiState.Loaded -> {
      // 对齐 puppygit CommitListScreen 的 FilterTextField: 支持按消息/作者/hash 过滤
      val filtered = if (filterText.isBlank()) state.commits
          else state.commits.filter {
            it.shortMsg.contains(filterText, ignoreCase = true) ||
                it.author.contains(filterText, ignoreCase = true) ||
                it.oidStr.contains(filterText, ignoreCase = true)
          }
      PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        Column(modifier = Modifier.fillMaxSize()) {
          GitFilterBar(
              value = filterText,
              onValueChange = { filterText = it },
              placeholder = "过滤提交 (消息/作者/hash)",
          )
          if (filtered.isEmpty()) {
            GitEmptyState(if (filterText.isNotBlank()) "无匹配的提交" else "暂无提交历史")
          } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
            ) {
              items(filtered, key = { it.oidStr }) { commit ->
                CommitItem(
                    commit = commit,
                    onResetToCommit = { confirmReset = commit },
                    onCherryPick = { cherryPickForCommit = commit },
                    onCheckoutCommit = { onCheckoutCommit(commit.oidStr) },
                    onShowDetails = { detailsCommit = commit },
                    onCreateTag = { tagForCommit = commit },
                    onCopyHash = { onCopyHash(commit.oidStr) },
                    onSavePatch = { patchForCommit = commit },
                    onSquashToCommit = { squashTarget = commit },
                )
              }
            }
          }
        }
      }
    }
  }

  // Reset 确认对话框: 选择 soft / mixed / hard 模式 (对齐 puppygit ResetDialog)
  confirmReset?.let { commit ->
    AlertDialog(
        onDismissRequest = { confirmReset = null },
        title = { Text("重置到 ${commit.shortOidStr}") },
        text = {
          Column {
            Text(commit.shortMsg, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("SOFT: 移动 HEAD, 保留暂存区与工作区",
                style = MaterialTheme.typography.bodySmall)
            Text("MIXED: 移动 HEAD, 重置暂存区, 保留工作区",
                style = MaterialTheme.typography.bodySmall)
            Text("HARD: 移动 HEAD, 重置暂存区与工作区 (⚠️ 丢失未提交变更)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
          }
        },
        confirmButton = {
          Row {
            TextButton(onClick = {
              confirmReset = null
              onResetToCommit(commit.oidStr, Reset.ResetT.SOFT)
            }) { Text("SOFT") }
            TextButton(onClick = {
              confirmReset = null
              onResetToCommit(commit.oidStr, Reset.ResetT.MIXED)
            }) { Text("MIXED") }
            TextButton(onClick = {
              confirmReset = null
              onResetToCommit(commit.oidStr, Reset.ResetT.HARD)
            }) { Text("HARD") }
          }
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

  // 基于 commit 创建 tag 对话框 (支持附注 tag)
  tagForCommit?.let { commit ->
    var tagName by remember { mutableStateOf("") }
    var tagMsg by remember { mutableStateOf("") }
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
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tagMsg,
                onValueChange = { tagMsg = it },
                label = { Text("附注消息 (留空创建轻量 tag)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
          }
        },
        confirmButton = {
          TextButton(onClick = {
            val name = tagName.trim()
            val msg = tagMsg.trim()
            tagForCommit = null
            if (name.isNotEmpty()) onCreateTag(commit.oidStr, name, msg)
          }) { Text("创建") }
        },
        dismissButton = {
          TextButton(onClick = { tagForCommit = null }) { Text("取消") }
        },
    )
  }

  // 保存 Patch 对话框: 选择父提交 (多父提交时), 然后 onSavePatch
  patchForCommit?.let { commit ->
    val parents = commit.parentOidStrList
    var selectedParent by remember { mutableStateOf(parents.firstOrNull() ?: "") }
    AlertDialog(
        onDismissRequest = { patchForCommit = null },
        title = { Text("保存为 Patch 文件") },
        text = {
          Column {
            Text("提交: ${commit.shortOidStr} ${commit.shortMsg}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (parents.isEmpty()) {
              Text("此提交无父提交, 无法生成 patch",
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall)
            } else if (parents.size == 1) {
              Text("将生成 ${parents[0].take(7)}..${commit.shortOidStr} 的 patch 文件",
                  style = MaterialTheme.typography.bodySmall)
            } else {
              Text("选择父提交 (作为 diff 左侧):",
                  style = MaterialTheme.typography.labelMedium)
              parents.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                  RadioButton(
                      selected = selectedParent == p,
                      onClick = { selectedParent = p },
                  )
                  Text(p.take(7), style = MaterialTheme.typography.bodySmall,
                      fontFamily = FontFamily.Monospace)
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(
              enabled = parents.isNotEmpty(),
              onClick = {
                patchForCommit = null
                onSavePatch(commit.oidStr, selectedParent)
              },
          ) { Text("保存") }
        },
        dismissButton = {
          TextButton(onClick = { patchForCommit = null }) { Text("取消") }
        },
    )
  }

  // Cherry-pick 选项对话框: 选择父提交 (merge commit) + autoCommit 选项
  cherryPickForCommit?.let { commit ->
    val parents = commit.parentOidStrList
    var selectedParent by remember { mutableStateOf(parents.firstOrNull() ?: "") }
    var autoCommit by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = { cherryPickForCommit = null },
        title = { Text("Cherry-pick 选项") },
        text = {
          Column {
            Text("提交: ${commit.shortOidStr} ${commit.shortMsg}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (parents.size > 1) {
              Text("此为 merge commit, 请选择 mainline 父提交:",
                  style = MaterialTheme.typography.labelMedium)
              parents.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                  RadioButton(
                      selected = selectedParent == p,
                      onClick = { selectedParent = p },
                  )
                  Text("父 $i: ${p.take(7)}", style = MaterialTheme.typography.bodySmall,
                      fontFamily = FontFamily.Monospace)
                }
              }
              Spacer(Modifier.height(4.dp))
            } else if (parents.isEmpty()) {
              Text("无父提交 (根提交), 将直接 cherry-pick",
                  style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(checked = autoCommit, onCheckedChange = { autoCommit = it })
              Text("自动提交 (取消勾选则只 pick 到 index)",
                  style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        confirmButton = {
          TextButton(onClick = {
            cherryPickForCommit = null
            onCherryPickWithOptions(commit.oidStr, selectedParent, autoCommit)
          }) { Text("Cherry-pick") }
        },
        dismissButton = {
          TextButton(onClick = { cherryPickForCommit = null }) { Text("取消") }
        },
    )
  }

  // Squash 对话框: 输入 commit msg + force 选项
  squashTarget?.let { commit ->
    var msg by remember { mutableStateOf("") }
    var force by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { squashTarget = null },
        title = { Text("Squash 到 ${commit.shortOidStr}") },
        text = {
          Column {
            Text("将 Squash HEAD 到此提交 (含此提交, 不含 HEAD).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("即: soft reset HEAD 到此 commit, 再用一条新提交合并所有变更.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = msg,
                onValueChange = { msg = it },
                label = { Text("提交消息 (留空自动生成 'Squash: xxx..yyy')") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(checked = force, onCheckedChange = { force = it })
              Text("Force (index 含未提交变更时也执行)",
                  style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        confirmButton = {
          TextButton(onClick = {
            val m = msg.trim()
            squashTarget = null
            onSquashToCommit(commit.oidStr, m, force)
          }) { Text("Squash", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
          TextButton(onClick = { squashTarget = null }) { Text("取消") }
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
 *
 * 对齐 puppygit CommitListScreen 的 BottomSheet 操作项: 查看详情 / 复制 Hash /
 * 创建 Tag / Reset / Cherry-pick(带选项) / Checkout / 保存 Patch / Squash.
 */
@Composable
private fun CommitItem(
    commit: CommitDto,
    onResetToCommit: () -> Unit,
    onCherryPick: () -> Unit,
    onCheckoutCommit: () -> Unit,
    onShowDetails: () -> Unit,
    onCreateTag: () -> Unit,
    onCopyHash: () -> Unit,
    onSavePatch: () -> Unit,
    onSquashToCommit: () -> Unit,
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
            text = { Text("复制 Hash") },
            onClick = {
              menuExpanded = false
              onCopyHash()
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
            text = { Text("Cherry-pick (带选项)") },
            onClick = {
              menuExpanded = false
              onCherryPick()
            },
        )
        DropdownMenuItem(
            text = { Text("Squash 到此提交") },
            onClick = {
              menuExpanded = false
              onSquashToCommit()
            },
        )
        DropdownMenuItem(
            text = { Text("保存为 Patch 文件") },
            onClick = {
              menuExpanded = false
              onSavePatch()
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
                      // 填充父提交列表, 供 Patch / Cherry-pick 选项对话框使用
                      val parentOidStrList = mutableListOf<String>()
                      val parentCount = commit.parentCount()
                      for (i in 0 until parentCount) {
                        val parentId = commit.parentId(i)
                        if (parentId != null) {
                          parentOidStrList.add(parentId.toString())
                        }
                      }
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
                              parentOidStrList = parentOidStrList,
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
