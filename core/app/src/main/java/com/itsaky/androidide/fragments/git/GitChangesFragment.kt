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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import com.catpuppyapp.puppygit.data.entity.RepoEntity
import com.catpuppyapp.puppygit.git.StatusTypeEntrySaver
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitChangesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 变更与提交页面 —— 独立设计的 Compose UI (commit 工作流)。
 *
 * UI 结构 (参考 core/git/UI设计概念图/Core Workflow.svg):
 * - 顶部 mini-toolbar: Commit / Push / Pull / Force Push / Stage All / Unstage All / Discard All / Refresh
 * - 内容区: 可折叠的 Staged / Unstaged 两个 section, 每个文件项显示
 *   文件名 + 路径 + 变更类型色块 (M/A/D/R/?)
 * - 底部: commit 消息输入框 + Amend 勾选 + Commit 按钮 (显示已暂存数量)
 *
 * 交互:
 * - 点击文件项 -> 打开 diff (通知 GitDiffFragment)
 * - 长按文件项 -> 暂存/取消暂存单个文件
 * - 底部 Commit 按钮 -> 提交已暂存内容
 * - 文件监听器: 每 1500ms 轮询, signature 变化时自动刷新
 *
 * git core 调用一比一复刻 puppygit:
 * - [Libgit2Helper.getWorkdirStatusList] + [Libgit2Helper.getWorktreeChangeList] -> 未暂存
 * - [Libgit2Helper.checkIndexIsEmptyAndGetIndexList] -> 已暂存
 * - [Libgit2Helper.stageAll] / [Libgit2Helper.unStageItems] / [Libgit2Helper.resetHardToHead]
 * - [Libgit2Helper.createCommit] / [Libgit2Helper.push] / [Libgit2Helper.fetchRemoteForRepo]
 *
 * @author android_zero
 */
class GitChangesFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitChangesBinding? = null
  private val binding
    get() = _binding!!

  /** 刷新触发器, toolbar 按钮和文件监听器递增它, Compose 内 LaunchedEffect 观察变化重新加载. */
  private val refreshTrigger = mutableStateOf(0)

  /** commit 消息, Fragment 字段持有, toolbar Commit 按钮和 Compose 底部输入框共享. */
  private val commitMessage = mutableStateOf("")

  /** Amend 勾选状态, 同上共享. */
  private val amend = mutableStateOf(false)

  private var watchJob: Job? = null
  private var lastSnapshotSignature: String? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitChangesBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    // 分组1: 提交
    addToolbarAction(R.drawable.ic_check_24, getString(R.string.commit)) {
      emitGitOperation("changes", "commit")
      commitChanges()
    }

    addToolbarSeparator()

    // 分组2: Remote 操作
    addToolbarAction(R.drawable.ic_arrow_upward_24, getString(R.string.push)) {
      emitGitOperation("changes", "push")
      pushCurrentBranch(force = false)
    }
    addToolbarAction(R.drawable.ic_cloud_download_24, getString(R.string.pull)) {
      emitGitOperation("changes", "pull_fetch_origin")
      pullFromOrigin()
    }
    addToolbarAction(R.drawable.ic_warning_24, "Force Push") {
      emitGitOperation("changes", "force_push")
      pushCurrentBranch(force = true)
    }
    addToolbarAction(R.drawable.ic_download_24, "Fetch") {
      emitGitOperation("changes", "fetch_origin")
      fetchFromOrigin()
    }

    addToolbarSeparator()

    // 分组3: Staging 操作
    addToolbarAction(R.drawable.ic_select_all_24, getString(R.string.stage_all)) {
      emitGitOperation("changes", "stage_all")
      stageAll()
    }
    addToolbarAction(R.drawable.ic_remove_circle_outline_24, getString(R.string.unstage)) {
      emitGitOperation("changes", "unstage_all")
      unstageAll()
    }
    addToolbarAction(R.drawable.ic_delete_sweep_24, getString(R.string.revert)) {
      emitGitOperation("changes", "discard_all")
      discardAll()
    }
    addToolbarAction(R.drawable.ic_delete_sweep_24, "Clean Untracked") {
      emitGitOperation("changes", "clean_untracked")
      cleanUntracked()
    }

    addToolbarSeparator()

    // 分组4: Merge/Rebase 进行中状态管理
    addToolbarAction(R.drawable.ic_check_24, "Merge Continue") {
      emitGitOperation("changes", "merge_continue")
      mergeContinue()
    }
    addToolbarAction(R.drawable.ic_warning_24, "Merge Abort") {
      emitGitOperation("changes", "merge_abort")
      mergeAbort()
    }
    addToolbarAction(R.drawable.ic_check_24, "Rebase Continue") {
      emitGitOperation("changes", "rebase_continue")
      rebaseContinue()
    }
    addToolbarAction(R.drawable.ic_remove_circle_outline_24, "Rebase Skip") {
      emitGitOperation("changes", "rebase_skip")
      rebaseSkip()
    }
    addToolbarAction(R.drawable.ic_warning_24, "Rebase Abort") {
      emitGitOperation("changes", "rebase_abort")
      rebaseAbort()
    }

    addToolbarSeparator()

    // 分组5: 刷新
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      emitGitOperation("changes", "refresh")
      triggerRefresh()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()

    val compose = setIdeContent {
      ChangesContent(
          workdir = workdir,
          refreshTrigger = refreshTrigger,
          commitMessage = commitMessage,
          amend = amend,
          onRefresh = ::triggerRefresh,
          onStageFile = ::stageFile,
          onUnstageFile = ::unstageFile,
          onOpenDiff = ::openDiff,
          onCommit = ::commitChanges,
          onCommitAndPush = ::commitThenPush,
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  override fun onStart() {
    super.onStart()
    startChangesWatcher()
  }

  override fun onStop() {
    watchJob?.cancel()
    watchJob = null
    super.onStop()
  }

  private fun triggerRefresh() {
    refreshTrigger.value++
  }

  // ---- git 操作 (复刻 puppygit 调用方式) ----

  private fun stageAll() {
    withRepo { repo ->
      val ret = Libgit2Helper.stageAll(repo, repoId = "")
      if (ret.hasError()) throw RuntimeException(ret.msg)
    }
  }

  private fun unstageAll() {
    withRepo { repo ->
      val (_, staged) =
          Libgit2Helper.checkIndexIsEmptyAndGetIndexList(
              repo = repo,
              repoId = "",
              onlyCheckEmpty = false,
          )
      val paths = staged.orEmpty().map { it.relativePathUnderRepo }
      if (paths.isEmpty()) throw RuntimeException("No staged file")
      Libgit2Helper.unStageItems(repo, paths)
    }
  }

  private fun discardAll() {
    withRepo { repo ->
      val ret = Libgit2Helper.resetHardToHead(repo)
      if (ret.hasError()) throw RuntimeException(ret.msg)
    }
  }

  /** 删除所有未跟踪文件 (git clean). */
  private fun cleanUntracked() {
    withRepo { repo ->
      val statusList = Libgit2Helper.getWorkdirStatusList(repo)
      val untrackedPaths =
          statusList
              .filter { it.changeType == com.catpuppyapp.puppygit.utils.Cons.gitStatusNew }
              .map { it.canonicalPath }
              .filter { it.isNotBlank() }
      if (untrackedPaths.isNotEmpty()) {
        Libgit2Helper.rmUntrackedFiles(untrackedPaths)
      }
    }
  }

  private fun stageFile(item: StatusTypeEntrySaver) {
    withRepo { repo -> Libgit2Helper.stageStatusEntryAndWriteToDisk(repo, listOf(item)) }
  }

  private fun unstageFile(path: String) {
    withRepo { repo -> Libgit2Helper.unStageItems(repo, listOf(path)) }
  }

  private fun openDiff(path: String) {
    GitSharedState.openDiffForPath(path)
    emitGitOperation("changes", "open_diff")
    (requireActivity() as? androidx.fragment.app.FragmentActivity)?.let {
      androidx.lifecycle.ViewModelProvider(it)[GitUiEventViewModel::class.java]
          .emit(GitUiEvent.OpenDiff(path))
    }
  }

  private fun commitChanges(onSuccess: (() -> Unit)? = null) {
    val msg = commitMessage.value.trim()
    if (msg.isBlank()) {
      Toast.makeText(context, getString(R.string.please_input_commit_msg), Toast.LENGTH_SHORT)
          .show()
      return
    }

    val ctx = context ?: return
    GitCredentialManager.ensureConfigured(ctx) { cfg ->
      withRepo(
          action = { repo ->
            val settings = SettingsUtil.getSettingsSnapshot()
            val ret =
                Libgit2Helper.createCommit(
                    repo = repo,
                    msg = msg,
                    username = cfg.username,
                    email = cfg.email,
                    amend = amend.value,
                    cleanRepoStateIfSuccess = true,
                    settings = settings,
                )
            if (ret.hasError()) throw RuntimeException(ret.msg)
          },
          onSuccess = {
            // 提交成功后清空消息
            commitMessage.value = ""
            amend.value = false
            onSuccess?.invoke()
          },
      )
    }
  }

  private fun commitThenPush() {
    commitChanges(onSuccess = { pushCurrentBranch(force = false) })
  }

  private fun pushCurrentBranch(force: Boolean) {
    val context = context ?: return
    GitCredentialManager.ensureConfigured(context) { cfg ->
      withRepo { repo ->
        if (Libgit2Helper.resolveRemote(repo, "origin") == null) {
          throw IllegalStateException("Remote origin not found")
        }
        val branch =
            repo.head()?.shorthand()?.removePrefix("refs/heads/")?.ifBlank { "main" } ?: "main"
        val hasLocalBranch =
            Libgit2Helper.getBranchList(repo).any {
              it.type == com.github.git24j.core.Branch.BranchType.LOCAL && it.shortName == branch
            }
        if (!hasLocalBranch) throw IllegalStateException("Current branch '$branch' is invalid")

        val refspec = "refs/heads/$branch:refs/heads/$branch"
        val credential = GitCredentialManager.toHttpCredential(cfg)
        Libgit2Helper.push(repo, "origin", listOf(refspec), credential, force)
      }
    }
  }

  private fun pullFromOrigin() {
    val context = context ?: return
    GitCredentialManager.ensureConfigured(context) { cfg ->
      withRepo { repo ->
        if (Libgit2Helper.resolveRemote(repo, "origin") == null) {
          throw IllegalStateException("Remote origin not found")
        }
        val workdir = repo.workdir() ?: throw IllegalStateException("Repository workdir is null")
        val repoEntity =
            RepoEntity(
                repoName = java.io.File(workdir).name,
                fullSavePath = workdir,
                branch = repo.head()?.shorthand().orEmpty(),
            )
        Libgit2Helper.fetchRemoteForRepo(
            repo = repo,
            remoteName = "origin",
            credential = GitCredentialManager.toHttpCredential(cfg),
            repoFromDb = repoEntity,
        )
      }
    }
  }

  /** 仅 fetch (不 merge), 拉取远程更新但不改变工作区. */
  private fun fetchFromOrigin() {
    pullFromOrigin()
  }

  // ---- Merge/Rebase 进行中状态管理 ----

  /** Merge Continue: 解决冲突后提交以继续合并. */
  private fun mergeContinue() {
    val workdir = resolveWorkspaceDirPath() ?: return
    val ctx = context ?: return
    GitCredentialManager.ensureConfigured(ctx) { cfg ->
      viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
          Repository.open(workdir).use { repo ->
            val readyCheck = Libgit2Helper.readyForContinueMerge(repo, ctx)
            if (readyCheck.hasError()) throw RuntimeException(readyCheck.msg)
            val settings = SettingsUtil.getSettingsSnapshot()
            val msg = "Merge"  // 默认 merge commit 消息
            val ret = Libgit2Helper.createCommit(
                repo = repo, msg = msg,
                username = cfg.username, email = cfg.email,
                amend = false, cleanRepoStateIfSuccess = true,
                settings = settings,
            )
            if (ret.hasError()) throw RuntimeException(ret.msg)
          }
        }
        withContext(Dispatchers.Main) {
          result.onSuccess { toast("Merge continue 完成"); triggerRefresh() }
              .onFailure { toast(it.localizedMessage ?: "Merge continue 失败") }
        }
      }
    }
  }

  /** Merge Abort: 中止合并, 回到合并前状态. */
  private fun mergeAbort() {
    val workdir = resolveWorkspaceDirPath() ?: return
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret = Libgit2Helper.resetHardToHead(repo)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess { toast("已中止合并"); triggerRefresh() }
            .onFailure { toast(it.localizedMessage ?: "中止合并失败") }
      }
    }
  }

  /** Rebase Continue: 解决冲突后继续变基. */
  private fun rebaseContinue() {
    val workdir = resolveWorkspaceDirPath() ?: return
    val ctx = context ?: return
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val (username, email) = Libgit2Helper.getGitUserNameAndEmailFromRepo(repo)
          val settings = SettingsUtil.getSettingsSnapshot()
          val ret = Libgit2Helper.rebaseContinue(repo, ctx, username, email, settings = settings)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess { toast("Rebase continue 完成"); triggerRefresh() }
            .onFailure { toast(it.localizedMessage ?: "Rebase continue 失败") }
      }
    }
  }

  /** Rebase Skip: 跳过当前冲突的 commit. */
  private fun rebaseSkip() {
    val workdir = resolveWorkspaceDirPath() ?: return
    val ctx = context ?: return
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val (username, email) = Libgit2Helper.getGitUserNameAndEmailFromRepo(repo)
          val settings = SettingsUtil.getSettingsSnapshot()
          val ret = Libgit2Helper.rebaseSkip(repo, ctx, username, email, settings = settings)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess { toast("已跳过当前 commit"); triggerRefresh() }
            .onFailure { toast(it.localizedMessage ?: "Rebase skip 失败") }
      }
    }
  }

  /** Rebase Abort: 中止变基, 回到变基前状态. */
  private fun rebaseAbort() {
    val workdir = resolveWorkspaceDirPath() ?: return
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret = Libgit2Helper.rebaseAbort(repo)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess { toast("已中止 rebase"); triggerRefresh() }
            .onFailure { toast(it.localizedMessage ?: "中止 rebase 失败") }
      }
    }
  }

  private fun toast(msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
  }

  /** 在 IO 线程执行 git 操作, 成功后刷新列表. */
  private fun withRepo(onSuccess: (() -> Unit)? = null, action: (Repository) -> Unit) {
    val projectDir = resolveWorkspaceDirPath()
    if (projectDir == null) {
      Toast.makeText(context, "No opened project", Toast.LENGTH_SHORT).show()
      return
    }

    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val ret = runCatching { Repository.open(projectDir).use(action) }
      withContext(Dispatchers.Main) {
        ret.onSuccess {
          triggerRefresh()
          onSuccess?.invoke()
          Toast.makeText(context, "Git operation completed", Toast.LENGTH_SHORT).show()
        }
        ret.onFailure {
          Toast.makeText(context, it.localizedMessage ?: "Git operation failed", Toast.LENGTH_LONG)
              .show()
        }
      }
    }
  }

  /** 文件变更监听器: 每 1500ms 轮询, signature 变化时触发 Compose 刷新. */
  private fun startChangesWatcher() {
    if (watchJob?.isActive == true) return
    watchJob =
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
          while (isActive) {
            val projectDir = resolveWorkspaceDirPath()
            if (projectDir != null) {
              runCatching { readChangeSignature(projectDir) }
                  .onSuccess { sig ->
                    if (sig != lastSnapshotSignature && isAdded) {
                      lastSnapshotSignature = sig
                      withContext(Dispatchers.Main) {
                        if (!isAdded || view == null) return@withContext
                        triggerRefresh()
                      }
                    }
                  }
            }
            delay(1500)
          }
        }
  }

  /** 只读 signature (不加载完整列表), 用于监听器快速判断是否有变化. */
  private fun readChangeSignature(projectDir: String): String {
    return Repository.open(projectDir).use { repo ->
      val statusList = Libgit2Helper.getWorkdirStatusList(repo)
      val unstaged = Libgit2Helper.getWorktreeChangeList(repo, statusList, repoId = "")
      val (_, staged) =
          Libgit2Helper.checkIndexIsEmptyAndGetIndexList(
              repo = repo,
              repoId = "",
              onlyCheckEmpty = false,
          )
      val head = runCatching { repo.head()?.target()?.toString() ?: "" }.getOrDefault("")
      buildSignature(head, staged.orEmpty(), unstaged)
    }
  }

  private fun buildSignature(
      head: String,
      staged: List<StatusTypeEntrySaver>,
      unstaged: List<StatusTypeEntrySaver>,
  ): String {
    val stagedSig =
        staged.joinToString("|") { "${it.relativePathUnderRepo}:${it.changeType.orEmpty()}" }
    val unstagedSig =
        unstaged.joinToString("|") { "${it.relativePathUnderRepo}:${it.changeType.orEmpty()}" }
    return "$head#$stagedSig#$unstagedSig"
  }

  override fun onDestroyView() {
    watchJob?.cancel()
    watchJob = null
    binding.gitContentContainer.removeAllViews()
    super.onDestroyView()
    _binding = null
  }
}

// ===================== 独立设计的 Compose UI =====================

/** 变更页面 UI 状态. */
private sealed interface ChangesUiState {
  data object Loading : ChangesUiState

  data object NoProject : ChangesUiState

  data class Error(val message: String) : ChangesUiState

  data class Loaded(
      val staged: List<StatusTypeEntrySaver>,
      val unstaged: List<StatusTypeEntrySaver>,
  ) : ChangesUiState
}

/**
 * 变更页面主体: 上方文件列表 (Staged + Unstaged) + 下方 commit 输入框.
 *
 * @param workdir 仓库工作目录; null 表示未打开项目
 * @param refreshKey 刷新触发器
 * @param commitMessage commit 消息 (Fragment 字段, 共享)
 * @param amend Amend 勾选状态 (Fragment 字段, 共享)
 */
@Composable
private fun ChangesContent(
    workdir: String?,
    refreshTrigger: State<Int>,
    commitMessage: MutableState<String>,
    amend: MutableState<Boolean>,
    onRefresh: () -> Unit,
    onStageFile: (StatusTypeEntrySaver) -> Unit,
    onUnstageFile: (String) -> Unit,
    onOpenDiff: (String) -> Unit,
    onCommit: () -> Unit,
    onCommitAndPush: () -> Unit,
) {
  if (workdir == null) {
    GitEmptyState("未打开项目")
    return
  }

  var uiState by remember { mutableStateOf<ChangesUiState>(ChangesUiState.Loading) }

  LaunchedEffect(workdir, refreshTrigger.value) {
    if (uiState is ChangesUiState.Loaded) {
      // 已有列表时不显示全屏 Loading (静默刷新)
    } else {
      uiState = ChangesUiState.Loading
    }
    uiState = loadChanges(workdir)
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // 文件列表区域 (占满剩余空间)
    when (val state = uiState) {
      ChangesUiState.Loading -> GitLoadingState()
      ChangesUiState.NoProject -> GitEmptyState("未打开项目")
      is ChangesUiState.Error -> GitErrorState(state.message, onRetry = onRefresh)
      is ChangesUiState.Loaded -> {
        if (state.staged.isEmpty() && state.unstaged.isEmpty()) {
          GitEmptyState("工作区干净，没有变更")
        } else {
          ChangesList(
              staged = state.staged,
              unstaged = state.unstaged,
              onStageFile = onStageFile,
              onUnstageFile = onUnstageFile,
              onOpenDiff = onOpenDiff,
              modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // 底部 commit 输入区
    CommitInputCard(
        commitMessage = commitMessage,
        amend = amend,
        stagedCount =
            (uiState as? ChangesUiState.Loaded)?.staged?.size ?: 0,
        onCommit = onCommit,
        onCommitAndPush = onCommitAndPush,
    )
  }
}

/** 加载变更快照 (IO 线程). */
private suspend fun loadChanges(workdir: String): ChangesUiState =
    withContext(Dispatchers.IO) {
      runCatching {
            Repository.open(workdir).use { repo ->
              val statusList = Libgit2Helper.getWorkdirStatusList(repo)
              val unstaged = Libgit2Helper.getWorktreeChangeList(repo, statusList, repoId = "")
              val (_, staged) =
                  Libgit2Helper.checkIndexIsEmptyAndGetIndexList(
                      repo = repo,
                      repoId = "",
                      onlyCheckEmpty = false,
                  )
              ChangesUiState.Loaded(staged = staged.orEmpty(), unstaged = unstaged)
            }
          }
          .getOrElse { ChangesUiState.Error(it.localizedMessage ?: "加载变更失败") }
    }

/**
 * 变更文件列表: 可折叠的 Staged / Unstaged 两个 section.
 */
@Composable
private fun ChangesList(
    staged: List<StatusTypeEntrySaver>,
    unstaged: List<StatusTypeEntrySaver>,
    onStageFile: (StatusTypeEntrySaver) -> Unit,
    onUnstageFile: (String) -> Unit,
    onOpenDiff: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  var stagedExpanded by remember { mutableStateOf(true) }
  var unstagedExpanded by remember { mutableStateOf(true) }

  LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    // Staged section
    item(key = "header_staged") {
      SectionHeader(
          title = "已暂存 (${staged.size})",
          expanded = stagedExpanded,
          onClick = { stagedExpanded = !stagedExpanded },
      )
    }
    if (stagedExpanded) {
      items(staged, key = { "s_" + it.relativePathUnderRepo }) { item ->
        ChangeFileItem(
            entry = item,
            onClick = { onOpenDiff(item.relativePathUnderRepo) },
            onLongClick = { onUnstageFile(item.relativePathUnderRepo) },
            actionLabel = "取消暂存",
        )
      }
    }

    // Unstaged section
    item(key = "header_unstaged") {
      Spacer(Modifier.height(4.dp))
      SectionHeader(
          title = "未暂存 (${unstaged.size})",
          expanded = unstagedExpanded,
          onClick = { unstagedExpanded = !unstagedExpanded },
      )
    }
    if (unstagedExpanded) {
      items(unstaged, key = { "u_" + it.relativePathUnderRepo }) { item ->
        ChangeFileItem(
            entry = item,
            onClick = { onOpenDiff(item.relativePathUnderRepo) },
            onLongClick = { onStageFile(item) },
            actionLabel = "暂存",
        )
      }
    }
  }
}

/** 可折叠的 section 标题. */
@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .combinedClickable { onClick() }
              .padding(horizontal = 4.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = if (expanded) "▼" else "▶",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 6.dp),
    )
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

/**
 * 单个变更文件项: 变更类型色块 + 文件名 + 路径 + 长按操作提示.
 *
 * @param actionLabel 长按操作标签 ("暂存" / "取消暂存")
 */
@Composable
private fun ChangeFileItem(
    entry: StatusTypeEntrySaver,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actionLabel: String,
) {
  val path = entry.relativePathUnderRepo
  val (badge, color) = changeTypeMeta(entry.changeType)
  val slashIndex = path.lastIndexOf('/')
  val fileName = if (slashIndex >= 0) path.substring(slashIndex + 1) else path
  val dirPart = if (slashIndex > 0) path.substring(0, slashIndex + 1) else ""

  Row(
      modifier =
          Modifier.fillMaxWidth()
              .combinedClickable(
                  onClick = onClick,
                  onLongClick = onLongClick,
              )
              .padding(horizontal = 4.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // 变更类型色块
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
    // 文件名 + 路径
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
    // 长按操作提示
    Text(
        text = actionLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 8.dp),
    )
  }
}

/**
 * 底部 commit 输入卡片: 消息输入框 + Amend 勾选 + Commit 按钮 + Commit & Push 按钮.
 */
@Composable
private fun CommitInputCard(
    commitMessage: MutableState<String>,
    amend: MutableState<Boolean>,
    stagedCount: Int,
    onCommit: () -> Unit,
    onCommitAndPush: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          ),
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      OutlinedTextField(
          value = commitMessage.value,
          onValueChange = { commitMessage.value = it },
          modifier = Modifier.fillMaxWidth(),
          placeholder = {
            Text(
                "输入 commit 消息...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
          },
          minLines = 2,
          maxLines = 4,
          textStyle = MaterialTheme.typography.bodySmall,
      )
      Spacer(Modifier.height(8.dp))
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
              checked = amend.value,
              onCheckedChange = { amend.value = it },
              modifier = Modifier.size(36.dp),
          )
          Text(
              text = "Amend",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row {
          TextButton(onClick = onCommitAndPush) {
            Text("Commit & Push", style = MaterialTheme.typography.labelMedium)
          }
          Spacer(Modifier.width(4.dp))
          androidx.compose.material3.Button(
              onClick = onCommit,
              enabled = stagedCount > 0,
          ) {
            Text("Commit ($stagedCount)", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
  }
}

/**
 * 变更类型 -> (单字符标识, 颜色).
 * 兼容全写字符串 ("Modified") 和单字符码 ("M").
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
