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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.catpuppyapp.puppygit.etc.Ret
import com.catpuppyapp.puppygit.git.BranchNameAndTypeDto
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Branch
import com.github.git24j.core.Repository
import com.github.git24j.core.Reset
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitBranchesComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 分支管理页面（2b 版本）。
 *
 * 2a2 直接渲染 puppygit 的 [com.catpuppyapp.puppygit.screen.BranchListScreen]，
 * 导致 "俄罗斯套娃"——双 toolbar / 双返回按钮。2b 起改为独立设计的 Compose UI：
 * - 用 [BaseGitPageFragment.setIdeContent] 挂载（不再走 puppygit 的 InitContent）
 * - git core 调用一比一复刻 puppygit（[Libgit2Helper] + git24j）
 * - 卡片式列表，分 "本地分支" / "远程分支" 两个 section
 * - 点击分支项弹出操作菜单：切换 / 重命名 / 删除
 *
 * 顶部 mini-toolbar 只保留两个按钮：Refresh / New Branch。toolbar 通过
 * [refreshTrigger] / [newBranchTrigger] 两个 [androidx.compose.runtime.MutableState]
 * 触发 Compose 刷新与新建分支对话框。
 *
 * @author android_zero
 */
class GitBranchesFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitBranchesComposeBinding? = null
  private val binding
    get() = _binding!!

  // toolbar 与 Compose 之间的通信信道：自增即触发。
  private val refreshTrigger = mutableStateOf(0)
  private val newBranchTrigger = mutableStateOf(0)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitBranchesComposeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()
    val compose = setIdeContent {
      BranchListContent(
          workdir = workdir,
          refreshTrigger = refreshTrigger,
          newBranchTrigger = newBranchTrigger,
          onRefresh = { refreshTrigger.value++ },
          onCheckout = ::checkoutBranch,
          onDelete = ::deleteBranch,
          onRename = { old, newN, force -> renameBranch(old, newN, force) },
          onCreate = { name, force, checkout -> createBranch(name, force, checkout) },
          onReset = ::resetToBranch,
          onSetUpstream = ::setUpstreamForBranch,
          onClearUpstream = ::clearUpstreamForBranch,
          onDeleteRemoteBranch = ::deleteRemoteBranch,
          onMerge = ::mergeBranch,
          onRebase = ::rebaseBranch,
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      emitGitOperation("branches", "refresh")
      refreshTrigger.value++
    }

    addToolbarAction(R.drawable.ic_add_24, getString(R.string.new_branch)) {
      emitGitOperation("branches", "create_branch_dialog")
      newBranchTrigger.value++
    }
  }

  // ---- 分支操作 (viewLifecycleOwner.lifecycleScope + Dispatchers.IO) ----

  private fun checkoutBranch(shortName: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret =
              Libgit2Helper.checkoutLocalBranchThenUpdateHead(
                  repo = repo,
                  branchName = shortName,
                  force = false,
                  updateHead = true,
              )
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已切换到分支 $shortName")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "切换分支失败") }
      }
    }
  }

  /**
   * 创建本地分支。对齐 puppygit BranchListScreen:
   * - [force] = true 时传入 [Libgit2Helper.createLocalBranchBasedHead] 的 overwriteIfExisted=true
   * - [checkout] = true 时创建后自动切换到新分支
   */
  private fun createBranch(branchName: String, force: Boolean, checkout: Boolean) {
    if (branchName.isBlank()) return toast("分支名不能为空")
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret =
              Libgit2Helper.createLocalBranchBasedHead(
                  repo = repo,
                  branchName = branchName,
                  overwriteIfExisted = force,
              )
          if (ret.hasError()) throw RuntimeException(ret.msg)
          if (checkout) {
            val coRet = Libgit2Helper.checkoutLocalBranchThenUpdateHead(
                repo = repo,
                branchName = branchName,
                force = false,
                updateHead = true,
            )
            if (coRet.hasError()) throw RuntimeException(coRet.msg)
          }
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已创建分支 $branchName" + if (checkout) " 并切换" else "")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "创建分支失败") }
      }
    }
  }

  private fun deleteBranch(shortName: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret = Libgit2Helper.deleteBranch(repo = repo, branchNameShortOrFull = shortName)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已删除分支 $shortName")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "删除分支失败") }
      }
    }
  }

  /**
   * 重命名本地分支。对齐 puppygit BranchListScreen:
   * - [force] = true 时传入 [Libgit2Helper.renameBranch] 的 force=true (覆盖同名分支)
   */
  private fun renameBranch(oldShortName: String, newName: String, force: Boolean) {
    if (newName.isBlank()) return toast("分支名不能为空")
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret =
              Libgit2Helper.renameBranch(
                  repo = repo,
                  branchShortName = oldShortName,
                  newName = newName,
                  force = force,
              )
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已重命名为 $newName")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "重命名失败") }
      }
    }
  }

  /**
   * 将当前分支 reset 到 [shortName] 指向的 commit。对齐 puppygit BranchListScreen 的 doReset:
   * - [resetType] 支持 SOFT / MIXED / HARD (对应 [Reset.ResetT])
   * - 调用 [Libgit2Helper.resetToRevspec], revspec 传分支短名
   */
  private fun resetToBranch(shortName: String, resetType: Reset.ResetT) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret = Libgit2Helper.resetToRevspec(repo, shortName, resetType)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已 Reset ($resetType) 到 $shortName")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "Reset 失败") }
      }
    }
  }

  /** 设置本地分支的上游 (remote + 远程分支全 refspec). */
  private fun setUpstreamForBranch(localBranchShortName: String, remoteName: String, remoteBranchShortName: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val upstream = com.catpuppyapp.puppygit.git.Upstream()
          upstream.remote = remoteName
          upstream.branchRefsHeadsFullRefSpec = "refs/heads/$remoteBranchShortName"
          Libgit2Helper.setUpstreamForBranch(repo, upstream, localBranchShortName)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已设置 $localBranchShortName 的上游为 $remoteName/$remoteBranchShortName")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "设置上游失败") }
      }
    }
  }

  /** 清除本地分支的上游. */
  private fun clearUpstreamForBranch(localBranchShortName: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          Libgit2Helper.clearUpstreamForBranch(repo, localBranchShortName)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已清除 $localBranchShortName 的上游")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "清除上游失败") }
      }
    }
  }

  /** 删除远程分支 (需要凭据, 推送删除). */
  private fun deleteRemoteBranch(remoteBranchFullRefSpec: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    val context = context ?: return
    GitCredentialManager.ensureConfigured(context) { cfg ->
      viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
          Repository.open(workdir).use { repo ->
            val ret = Libgit2Helper.deleteRemoteBranch(repo, remoteBranchFullRefSpec, GitCredentialManager.toHttpCredential(cfg))
            if (ret.hasError()) throw RuntimeException(ret.msg)
          }
        }
        withContext(Dispatchers.Main) {
          result.onSuccess {
            toast("已删除远程分支")
            refreshTrigger.value++
          }
          result.onFailure { toast(it.localizedMessage ?: "删除远程分支失败") }
        }
      }
    }
  }

  /**
   * 将 [targetBranchShortName] 合并到当前分支。
   *
   * 复刻 puppygit BranchListScreen 的 doMerge 调用:
   * - [Libgit2Helper.mergeOneHead] (trueMergeFalseRebase=true)
   * - 返回冲突时提示用户去冲突页面解决
   */
  private fun mergeBranch(targetBranchShortName: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val (username, email) = Libgit2Helper.getGitUserNameAndEmailFromRepo(repo)
          val settings = SettingsUtil.getSettingsSnapshot()
          val ret = Libgit2Helper.mergeOneHead(
              repo = repo,
              targetRefName = targetBranchShortName,
              username = username,
              email = email,
              settings = settings,
          )
          // 不抛异常, 而是根据 code 判断状态
          ret
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess { ret ->
          when {
            ret.success() -> {
              toast("合并成功")
              refreshTrigger.value++
            }
            ret.code == Ret.ErrCode.mergeFailedByAfterMergeHasConfilts -> {
              toast("合并存在冲突，请到「冲突」页面解决")
              refreshTrigger.value++
            }
            ret.code == Ret.ErrCode.alreadyUpToDate -> {
              toast("已经是最新的")
            }
            else -> toast(ret.msg.ifBlank { "合并失败" })
          }
        }
        result.onFailure { toast(it.localizedMessage ?: "合并失败") }
      }
    }
  }

  /**
   * 将当前分支 rebase 到 [targetBranchShortName]。
   * 复刻 puppygit BranchListScreen 的 doMerge (requireRebase=true) 调用。
   */
  private fun rebaseBranch(targetBranchShortName: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    val ctx = context ?: return
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val (username, email) = Libgit2Helper.getGitUserNameAndEmailFromRepo(repo)
          val settings = SettingsUtil.getSettingsSnapshot()
          val ret = Libgit2Helper.mergeOrRebase(
              repo = repo,
              targetRefName = targetBranchShortName,
              username = username,
              email = email,
              trueMergeFalseRebase = false,
              settings = settings,
          )
          ret
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess { ret ->
          when {
            ret.success() -> {
              toast("Rebase 成功")
              refreshTrigger.value++
            }
            ret.code == Ret.ErrCode.mergeFailedByAfterMergeHasConfilts -> {
              toast("Rebase 存在冲突，请到「变更」页面解决后继续")
              refreshTrigger.value++
            }
            ret.code == Ret.ErrCode.alreadyUpToDate -> {
              toast("已经是最新的")
            }
            else -> toast(ret.msg.ifBlank { "Rebase 失败" })
          }
        }
        result.onFailure { toast(it.localizedMessage ?: "Rebase 失败") }
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

// ---- 独立设计的 Compose UI ----

/** 分支列表 UI 状态。 */
private sealed interface BranchListUiState {
  data object Loading : BranchListUiState

  data object NoProject : BranchListUiState

  data class Loaded(
      val local: List<BranchNameAndTypeDto>,
      val remote: List<BranchNameAndTypeDto>,
  ) : BranchListUiState

  data class Error(val message: String) : BranchListUiState
}

/**
 * 分支列表主体。数据加载通过 [produceState] 在 [Dispatchers.IO] 上完成，
 * 一比一复刻 puppygit 的 git core 调用。
 */
@Composable
private fun BranchListContent(
    workdir: String?,
    refreshTrigger: State<Int>,
    newBranchTrigger: State<Int>,
    onRefresh: () -> Unit,
    onCheckout: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String, Boolean) -> Unit,
    onCreate: (String, Boolean, Boolean) -> Unit,
    onReset: (String, Reset.ResetT) -> Unit,
    onSetUpstream: (String, String, String) -> Unit,
    onClearUpstream: (String) -> Unit,
    onDeleteRemoteBranch: (String) -> Unit,
    onMerge: (String) -> Unit,
    onRebase: (String) -> Unit,
) {
  val trigger = refreshTrigger.value
  val uiState by produceState<BranchListUiState>(BranchListUiState.Loading, trigger, workdir) {
    if (workdir == null) {
      value = BranchListUiState.NoProject
      return@produceState
    }
    value = BranchListUiState.Loading
    value =
        runCatching {
              withContext(Dispatchers.IO) {
                Repository.open(workdir).use { repo ->
                  val local =
                      Libgit2Helper.getBranchList(
                          repo,
                          Branch.BranchType.LOCAL,
                          excludeRemoteHead = true,
                      )
                  val remote =
                      Libgit2Helper.getBranchList(
                          repo,
                          Branch.BranchType.REMOTE,
                          excludeRemoteHead = true,
                      )
                  BranchListUiState.Loaded(local, remote)
                }
              }
            }
            .getOrElse { BranchListUiState.Error(it.localizedMessage ?: "加载分支失败") }
  }

  val nbTrigger = newBranchTrigger.value
  var showCreateDialog by remember { mutableStateOf(false) }
  LaunchedEffect(nbTrigger) {
    if (nbTrigger > 0) showCreateDialog = true
  }
  var renaming by remember { mutableStateOf<BranchNameAndTypeDto?>(null) }
  var settingUpstream by remember { mutableStateOf<BranchNameAndTypeDto?>(null) }
  var confirmingDelete by remember { mutableStateOf<BranchNameAndTypeDto?>(null) }
  var confirmingMerge by remember { mutableStateOf<BranchNameAndTypeDto?>(null) }
  var confirmingRebase by remember { mutableStateOf<BranchNameAndTypeDto?>(null) }
  var resetting by remember { mutableStateOf<BranchNameAndTypeDto?>(null) }
  var showingDetails by remember { mutableStateOf<BranchNameAndTypeDto?>(null) }
  var filterText by remember { mutableStateOf("") }

  when (val s = uiState) {
    BranchListUiState.Loading -> GitLoadingState()
    BranchListUiState.NoProject -> GitEmptyState(message = "未打开工程")
    is BranchListUiState.Error -> GitErrorState(message = s.message, onRetry = onRefresh)
    is BranchListUiState.Loaded -> {
      if (s.local.isEmpty() && s.remote.isEmpty()) {
        GitEmptyState(message = "暂无分支")
      } else {
        Column(modifier = Modifier.fillMaxSize()) {
          GitFilterBar(
              value = filterText,
              onValueChange = { filterText = it },
              placeholder = "过滤分支名称",
          )
          val filteredLocal = s.local.filter { it.fullName.contains(filterText, ignoreCase = true) }
          val filteredRemote = s.remote.filter { it.fullName.contains(filterText, ignoreCase = true) }
          if (filteredLocal.isEmpty() && filteredRemote.isEmpty()) {
            GitEmptyState(message = "无匹配的分支")
          } else {
            BranchList(
                local = filteredLocal,
                remote = filteredRemote,
                onCheckout = onCheckout,
                onDelete = onDelete,
                onRenameRequest = { renaming = it },
                onSetUpstreamRequest = { settingUpstream = it },
                onClearUpstream = onClearUpstream,
                onDeleteRemoteBranch = { confirmingDelete = it },
                onMergeRequest = { confirmingMerge = it },
                onRebaseRequest = { confirmingRebase = it },
                onResetRequest = { resetting = it },
                onShowDetails = { showingDetails = it },
            )
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateBranchDialog(
        onConfirm = { name, force, checkout ->
          showCreateDialog = false
          onCreate(name, force, checkout)
        },
        onDismiss = { showCreateDialog = false },
    )
  }
  renaming?.let { branch ->
    RenameBranchDialog(
        branchShortName = branch.shortName,
        onConfirm = { newName, force ->
          renaming = null
          onRename(branch.shortName, newName, force)
        },
        onDismiss = { renaming = null },
    )
  }
  settingUpstream?.let { branch ->
    UpstreamDialog(
        localBranchName = branch.shortName,
        onConfirm = { remoteName, remoteBranch ->
          settingUpstream = null
          onSetUpstream(branch.shortName, remoteName, remoteBranch)
        },
        onDismiss = { settingUpstream = null },
    )
  }
  confirmingDelete?.let { branch ->
    AlertDialog(
        onDismissRequest = { confirmingDelete = null },
        title = { Text("删除远程分支") },
        text = { Text("确认删除远程分支 ${branch.shortName}?\n此操作不可撤销!") },
        confirmButton = {
          TextButton(onClick = {
            confirmingDelete = null
            onDeleteRemoteBranch(branch.fullName)
          }) { Text("删除") }
        },
        dismissButton = {
          TextButton(onClick = { confirmingDelete = null }) { Text("取消") }
        },
    )
  }
  confirmingMerge?.let { branch ->
    AlertDialog(
        onDismissRequest = { confirmingMerge = null },
        title = { Text("合并分支") },
        text = { Text("将分支 ${branch.shortName} 合并到当前分支?") },
        confirmButton = {
          TextButton(onClick = {
            confirmingMerge = null
            onMerge(branch.shortName)
          }) { Text("合并") }
        },
        dismissButton = {
          TextButton(onClick = { confirmingMerge = null }) { Text("取消") }
        },
    )
  }
  confirmingRebase?.let { branch ->
    AlertDialog(
        onDismissRequest = { confirmingRebase = null },
        title = { Text("Rebase 分支") },
        text = { Text("将当前分支 rebase 到 ${branch.shortName}?") },
        confirmButton = {
          TextButton(onClick = {
            confirmingRebase = null
            onRebase(branch.shortName)
          }) { Text("Rebase") }
        },
        dismissButton = {
          TextButton(onClick = { confirmingRebase = null }) { Text("取消") }
        },
    )
  }
  resetting?.let { branch ->
    ResetBranchDialog(
        branchShortName = branch.shortName,
        onConfirm = { resetType ->
          resetting = null
          onReset(branch.shortName, resetType)
        },
        onDismiss = { resetting = null },
    )
  }
  showingDetails?.let { branch ->
    BranchDetailsDialog(branch = branch, onDismiss = { showingDetails = null })
  }
}

/** 卡片式分支列表，分 "本地分支" / "远程分支" 两个 section。 */
@Composable
private fun BranchList(
    local: List<BranchNameAndTypeDto>,
    remote: List<BranchNameAndTypeDto>,
    onCheckout: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRenameRequest: (BranchNameAndTypeDto) -> Unit,
    onSetUpstreamRequest: (BranchNameAndTypeDto) -> Unit,
    onClearUpstream: (String) -> Unit,
    onDeleteRemoteBranch: (BranchNameAndTypeDto) -> Unit,
    onMergeRequest: (BranchNameAndTypeDto) -> Unit,
    onRebaseRequest: (BranchNameAndTypeDto) -> Unit,
    onResetRequest: (BranchNameAndTypeDto) -> Unit,
    onShowDetails: (BranchNameAndTypeDto) -> Unit,
) {
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
  ) {
    if (local.isNotEmpty()) {
      item(key = "header_local") { SectionHeader("本地分支") }
      items(local, key = { it.fullName }) { branch ->
        BranchItem(
            branch = branch,
            onCheckout = onCheckout,
            onDelete = onDelete,
            onRenameRequest = onRenameRequest,
            onSetUpstreamRequest = onSetUpstreamRequest,
            onClearUpstream = onClearUpstream,
            onMergeRequest = onMergeRequest,
            onRebaseRequest = onRebaseRequest,
            onResetRequest = onResetRequest,
            onShowDetails = onShowDetails,
        )
      }
    }
    if (remote.isNotEmpty()) {
      item(key = "header_remote") { SectionHeader("远程分支") }
      items(remote, key = { it.fullName }) { branch ->
        BranchItem(
            branch = branch,
            onCheckout = onCheckout,
            onDelete = onDelete,
            onRenameRequest = onRenameRequest,
            onSetUpstreamRequest = onSetUpstreamRequest,
            onClearUpstream = onClearUpstream,
            onDeleteRemoteBranch = onDeleteRemoteBranch,
            onMergeRequest = onMergeRequest,
            onRebaseRequest = onRebaseRequest,
            onResetRequest = onResetRequest,
            onShowDetails = onShowDetails,
        )
      }
    }
  }
}

/** section 小字标题。 */
@Composable
private fun SectionHeader(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 4.dp, top = 4.dp),
  )
}

/**
 * 单个分支卡片。
 *
 * 卡片圆角 12dp，间距由父 LazyColumn 的 spacedBy(8dp) 控制。当前分支用
 * primaryContainer 背景 + 右侧 primary 色小圆点高亮。点击弹出操作菜单。
 */
@Composable
private fun BranchItem(
    branch: BranchNameAndTypeDto,
    onCheckout: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRenameRequest: (BranchNameAndTypeDto) -> Unit,
    onSetUpstreamRequest: (BranchNameAndTypeDto) -> Unit,
    onClearUpstream: (String) -> Unit,
    onMergeRequest: (BranchNameAndTypeDto) -> Unit,
    onRebaseRequest: (BranchNameAndTypeDto) -> Unit,
    onResetRequest: (BranchNameAndTypeDto) -> Unit,
    onShowDetails: (BranchNameAndTypeDto) -> Unit,
    onDeleteRemoteBranch: (BranchNameAndTypeDto) -> Unit = null,
) {
  val isLocal = branch.type == Branch.BranchType.LOCAL
  val isCurrent = branch.isCurrent
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
      onClick = { menuExpanded = true },
      shape = RoundedCornerShape(GitSpacing.cardCorner),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant,
          ),
      modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = branch.shortName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color =
                if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
        )
        if (branch.shortOidStr.isNotBlank()) {
          Spacer(Modifier.size(4.dp))
          Text(
              text = branch.shortOidStr,
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (isCurrent) {
        Box(
            modifier =
                Modifier.padding(start = 8.dp).size(10.dp).clip(CircleShape).background(
                    MaterialTheme.colorScheme.primary),
        )
      }
      DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text("详情") },
            onClick = {
              menuExpanded = false
              onShowDetails(branch)
            },
        )
        if (isLocal && !isCurrent) {
          DropdownMenuItem(
              text = { Text("切换") },
              onClick = {
                menuExpanded = false
                onCheckout(branch.shortName)
              },
          )
        }
        if (isLocal) {
          DropdownMenuItem(
              text = { Text("重命名") },
              onClick = {
                menuExpanded = false
                onRenameRequest(branch)
              },
          )
          DropdownMenuItem(
              text = { Text("设置上游") },
              onClick = {
                menuExpanded = false
                onSetUpstreamRequest(branch)
              },
          )
          DropdownMenuItem(
              text = { Text("清除上游") },
              onClick = {
                menuExpanded = false
                onClearUpstream(branch.shortName)
              },
          )
        }
        if (!isCurrent) {
          DropdownMenuItem(
              text = { Text("合并到当前分支") },
              onClick = {
                menuExpanded = false
                onMergeRequest(branch)
              },
          )
          DropdownMenuItem(
              text = { Text("Rebase 到此分支") },
              onClick = {
                menuExpanded = false
                onRebaseRequest(branch)
              },
          )
          DropdownMenuItem(
              text = { Text("重置到此分支") },
              onClick = {
                menuExpanded = false
                onResetRequest(branch)
              },
          )
          DropdownMenuItem(
              text = { Text("删除") },
              onClick = {
                menuExpanded = false
                onDelete(branch.shortName)
              },
          )
        }
        if (!isLocal && onDeleteRemoteBranch != null) {
          DropdownMenuItem(
              text = { Text("删除远程分支") },
              onClick = {
                menuExpanded = false
                onDeleteRemoteBranch(branch)
              },
          )
        }
      }
    }
  }
}

/** 设置上游对话框: 输入 remote 名称和远程分支名. */
@Composable
private fun UpstreamDialog(
    localBranchName: String,
    onConfirm: (remoteName: String, remoteBranchName: String) -> Unit,
    onDismiss: () -> Unit,
) {
  var remoteName by remember { mutableStateOf("origin") }
  var remoteBranch by remember { mutableStateOf(localBranchName) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("设置 $localBranchName 的上游") },
      text = {
        Column {
          OutlinedTextField(
              value = remoteName,
              onValueChange = { remoteName = it },
              label = { Text("Remote 名称 (如 origin)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
              value = remoteBranch,
              onValueChange = { remoteBranch = it },
              label = { Text("远程分支名 (如 main)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        TextButton(onClick = {
          onConfirm(remoteName.trim(), remoteBranch.trim())
        }) { Text("设置") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

/**
 * 新建分支对话框。对齐 puppygit BranchListScreen:
 * - [force] = true 时覆盖同名分支 (overwriteIfExisted)
 * - [checkout] = true 时创建后自动切换到新分支
 */
@Composable
private fun CreateBranchDialog(
    onConfirm: (name: String, force: Boolean, checkout: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var force by remember { mutableStateOf(false) }
  var checkout by remember { mutableStateOf(false) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("新建分支") },
      text = {
        Column {
          OutlinedTextField(
              value = name,
              onValueChange = { name = it },
              label = { Text("分支名") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
          ) {
            Checkbox(checked = force, onCheckedChange = { force = it })
            Text("强制创建 (覆盖同名)")
          }
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
          ) {
            Checkbox(checked = checkout, onCheckedChange = { checkout = it })
            Text("创建后切换")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(name.trim(), force, checkout) }) { Text("创建") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

/**
 * 重命名分支对话框。对齐 puppygit BranchListScreen:
 * - [force] = true 时覆盖同名分支
 */
@Composable
private fun RenameBranchDialog(
    branchShortName: String,
    onConfirm: (newName: String, force: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
  var newName by remember { mutableStateOf(branchShortName) }
  var force by remember { mutableStateOf(false) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("重命名分支") },
      text = {
        Column {
          OutlinedTextField(
              value = newName,
              onValueChange = { newName = it },
              label = { Text("新分支名") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth(),
          ) {
            Checkbox(checked = force, onCheckedChange = { force = it })
            Text("强制覆盖同名分支")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(newName.trim(), force) }) { Text("重命名") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

/**
 * Reset 到分支对话框。对齐 puppygit BranchListScreen 的 doReset:
 * - SOFT: 仅移动 HEAD, 保留 index 和工作区
 * - MIXED: 移动 HEAD, 重置 index, 保留工作区 (默认)
 * - HARD: 移动 HEAD, 重置 index 和工作区 (危险)
 */
@Composable
private fun ResetBranchDialog(
    branchShortName: String,
    onConfirm: (Reset.ResetT) -> Unit,
    onDismiss: () -> Unit,
) {
  var resetType by remember { mutableStateOf(Reset.ResetT.MIXED) }
  val options = listOf(
      Reset.ResetT.SOFT to "SOFT (保留 index 和工作区)",
      Reset.ResetT.MIXED to "MIXED (重置 index, 保留工作区)",
      Reset.ResetT.HARD to "HARD (重置 index 和工作区, 危险!)",
  )
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Reset 到 $branchShortName") },
      text = {
        Column {
          Text(
              text = "选择 Reset 类型:",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(8.dp))
          options.forEach { (type, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
              RadioButton(
                  selected = resetType == type,
                  onClick = { resetType = type },
              )
              Text(label, style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(resetType) }) { Text("Reset") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

/**
 * 分支详情对话框。展示 [BranchNameAndTypeDto] 中的关键字段。
 */
@Composable
private fun BranchDetailsDialog(
    branch: BranchNameAndTypeDto,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("分支详情") },
      text = {
        Column {
          DetailRow("短名", branch.shortName)
          DetailRow("全名", branch.fullName)
          DetailRow("类型", branch.type.name)
          DetailRow("当前分支", if (branch.isCurrent) "是" else "否")
          if (branch.shortOidStr.isNotBlank()) {
            DetailRow("短 OID", branch.shortOidStr)
          }
          if (branch.oidStr.isNotBlank()) {
            DetailRow("完整 OID", branch.oidStr)
          }
          if (branch.isSymbolic) {
            DetailRow("符号引用目标", branch.symbolicTargetShortName)
          }
          branch.upstream?.let { up ->
            if (up.remote.isNotBlank()) {
              DetailRow("上游 Remote", up.remote)
            }
            if (up.remoteBranchShortRefSpec.isNotBlank()) {
              DetailRow("上游分支", up.remoteBranchShortRefSpec)
            }
          }
          if (branch.ahead > 0 || branch.behind > 0) {
            DetailRow("领先/落后", "↑${branch.ahead} ↓${branch.behind}")
          }
          if (branch.remotePrefixFromShortName.isNotBlank()) {
            DetailRow("Remote 前缀", branch.remotePrefixFromShortName)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = onDismiss) { Text("关闭") }
      },
  )
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
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
  }
}
