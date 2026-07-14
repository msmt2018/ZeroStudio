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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.catpuppyapp.puppygit.git.BranchNameAndTypeDto
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Branch
import com.github.git24j.core.Repository
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
          onRename = ::renameBranch,
          onCreate = ::createBranch,
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

  private fun createBranch(branchName: String) {
    if (branchName.isBlank()) return toast("分支名不能为空")
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret =
              Libgit2Helper.createLocalBranchBasedHead(
                  repo = repo,
                  branchName = branchName,
                  overwriteIfExisted = false,
              )
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已创建分支 $branchName")
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

  private fun renameBranch(oldShortName: String, newName: String) {
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
                  force = false,
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
    onRename: (String, String) -> Unit,
    onCreate: (String) -> Unit,
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

  // New Branch toolbar 按钮 → 打开新建对话框
  val nbTrigger = newBranchTrigger.value
  var showCreateDialog by remember { mutableStateOf(false) }
  LaunchedEffect(nbTrigger) {
    if (nbTrigger > 0) showCreateDialog = true
  }
  var renaming by remember { mutableStateOf<BranchNameAndTypeDto?>(null) }

  when (val s = uiState) {
    BranchListUiState.Loading -> GitLoadingState()
    BranchListUiState.NoProject -> GitEmptyState(message = "未打开工程")
    is BranchListUiState.Error -> GitErrorState(message = s.message, onRetry = onRefresh)
    is BranchListUiState.Loaded -> {
      if (s.local.isEmpty() && s.remote.isEmpty()) {
        GitEmptyState(message = "暂无分支")
      } else {
        BranchList(
            local = s.local,
            remote = s.remote,
            onCheckout = onCheckout,
            onDelete = onDelete,
            onRenameRequest = { renaming = it },
        )
      }
    }
  }

  if (showCreateDialog) {
    TextInputDialog(
        title = "新建分支",
        confirmText = "创建",
        initial = "",
        onConfirm = { name ->
          showCreateDialog = false
          onCreate(name)
        },
        onDismiss = { showCreateDialog = false },
    )
  }
  renaming?.let { branch ->
    TextInputDialog(
        title = "重命名分支",
        confirmText = "重命名",
        initial = branch.shortName,
        onConfirm = { newName ->
          renaming = null
          onRename(branch.shortName, newName)
        },
        onDismiss = { renaming = null },
    )
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
) {
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
  ) {
    if (local.isNotEmpty()) {
      item(key = "header_local") { SectionHeader("本地分支") }
      items(local, key = { it.fullName }) { branch ->
        BranchItem(branch, onCheckout, onDelete, onRenameRequest)
      }
    }
    if (remote.isNotEmpty()) {
      item(key = "header_remote") { SectionHeader("远程分支") }
      items(remote, key = { it.fullName }) { branch ->
        BranchItem(branch, onCheckout, onDelete, onRenameRequest)
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
        }
        if (!isCurrent) {
          DropdownMenuItem(
              text = { Text("删除") },
              onClick = {
                menuExpanded = false
                onDelete(branch.shortName)
              },
          )
        }
      }
    }
  }
}

/** 简单的文本输入对话框，用于新建 / 重命名分支。 */
@Composable
private fun TextInputDialog(
    title: String,
    confirmText: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf(initial) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = {
        OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(text.trim()) }) { Text(confirmText) }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}
