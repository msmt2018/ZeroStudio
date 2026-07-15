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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.catpuppyapp.puppygit.git.StashDto
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.github.git24j.core.Signature
import com.github.git24j.core.Stash
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitStashComposeBinding
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Git Stash 管理页面。
 *
 * 改用独立设计的 Compose UI 渲染 (不再套娃 puppygit 的 `StashListScreen`,
 * 避免双 toolbar)。数据加载一比一复刻 puppygit 的 git core 调用
 * (`Libgit2Helper.stashList`), 但 UI 完全独立, 仅用 AndroidIDE 自己的
 * [MaterialTheme] 包裹 (见 [setIdeContent])。
 *
 * 功能:
 * - 列出所有 stash ([Libgit2Helper.stashList])
 * - 新建 stash ([Libgit2Helper.stashSave]) — 可选 include untracked
 * - Apply / Pop / Drop stash
 * - 按消息/index/stashId 过滤
 * - Stash 详情对话框
 * - Pull-to-refresh
 *
 * 工具栏:
 * - Refresh: 触发 `refreshTrigger` 重新加载列表
 * - Stash All: 调用 `Libgit2Helper.stashSave` 新建 stash, 完成后刷新
 *
 * @author android_zero
 */
class GitStashFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitStashComposeBinding? = null
  private val binding
    get() = _binding!!

  /**
   * 刷新触发器。toolbar 按钮和 stash 操作完成后递增它, Compose 侧
   * `LaunchedEffect(workdir, refreshTrigger)` 会随之重新拉取列表。
   */
  private val refreshTrigger = mutableStateOf(0)

  /**
   * Stash 对话框触发器。toolbar "Stash All" 按钮递增它, Compose 侧
   * `LaunchedEffect(stashDialogTrigger)` 观察变化弹出消息输入对话框。
   */
  private val stashDialogTrigger = mutableStateOf(0)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitStashComposeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      triggerRefresh()
    }

    addToolbarAction(R.drawable.ic_push_pin_24, "Stash All") {
      stashDialogTrigger.value++
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()

    val compose = setIdeContent {
      StashListContent(
          workdir = workdir,
          refreshTrigger = refreshTrigger.value,
          stashDialogTrigger = stashDialogTrigger.value,
          onApply = ::applyStash,
          onPop = ::popStash,
          onDrop = ::dropStash,
          onStashWithMessage = ::stashAll,
          onRefresh = ::triggerRefresh,
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  private fun triggerRefresh() {
    refreshTrigger.value++
  }

  // ---- stash 操作 (复刻 puppygit 调用方式) ----

  private fun applyStash(index: Int) = performStashAction("Apply") { repo ->
    Libgit2Helper.stashApply(repo, index)
  }

  private fun popStash(index: Int) = performStashAction("Pop") { repo ->
    Libgit2Helper.stashPop(repo, index)
  }

  private fun dropStash(index: Int) = performStashAction("Drop") { repo ->
    Libgit2Helper.stashDrop(repo, index)
  }

  /**
   * 新建 stash。若 [message] 为空则使用 [Libgit2Helper.stashGenMsg] 自动生成消息。
   * 对齐 puppygit StashListScreen: 若 [includeUntracked] 为 true, 则传入
   * [Stash.Flags.INCLUDE_UNTRACKED] 标志 (puppygit UI 自身未暴露此选项)。
   */
  private fun stashAll(message: String, includeUntracked: Boolean) =
      performStashAction("Stash All") { repo ->
        val (username, email) = Libgit2Helper.getGitUserNameAndEmailFromRepo(repo)
        if (username.isBlank() || email.isBlank()) {
          throw RuntimeException("请先设置 git 用户名和邮箱")
        }
        val sig = Signature.create(username, email)
        val msg = message.ifBlank { Libgit2Helper.stashGenMsg() }
        val flags = if (includeUntracked) {
          EnumSet.of(Stash.Flags.INCLUDE_UNTRACKED)
        } else {
          EnumSet.of(Stash.Flags.DEFAULT)
        }
        Libgit2Helper.stashSave(repo, stasher = sig, msg = msg, flags = flags)
      }

  /**
   * 在 `Dispatchers.IO` 上执行一次 stash 操作; 成功后递增 [refreshTrigger] 触发列表刷新,
   * 失败则在主线程弹 Toast 提示。
   */
  private fun performStashAction(label: String, block: (Repository) -> Unit) {
    val workdir = resolveWorkspaceDirPath()
    if (workdir == null) {
      Toast.makeText(requireContext(), "No opened project", Toast.LENGTH_SHORT).show()
      return
    }
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      var ok = false
      try {
        Repository.open(workdir).use(block)
        ok = true
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          Toast.makeText(
                  requireContext(),
                  "$label 失败: ${e.localizedMessage ?: "unknown error"}",
                  Toast.LENGTH_LONG,
          )
              .show()
        }
      }
      if (ok) {
        refreshTrigger.value++
      }
    }
  }

  override fun onDestroyView() {
    binding.gitContentContainer.removeAllViews()
    super.onDestroyView()
    _binding = null
  }
}

// ---- 独立设计的 Compose UI ----

/**
 * Stash 列表主体。负责加载状态管理 (Loading / Empty / Error / Loaded) 与
 * 列表渲染。git 操作通过回调交回 [GitStashFragment] 在 `lifecycleScope` 上执行。
 *
 * 功能:
 * - [stashDialogTrigger] 变化时弹出消息输入对话框, 用户确认后调用 [onStashWithMessage]
 * - [GitFilterBar] 按 index/msg/stashId 过滤
 * - [PullToRefreshBox] 下拉刷新
 * - 点击卡片弹出操作菜单 (Apply / Pop / Drop / 详情), Drop 需二次确认
 */
@Composable
private fun StashListContent(
    workdir: String?,
    refreshTrigger: Int,
    stashDialogTrigger: Int,
    onApply: (Int) -> Unit,
    onPop: (Int) -> Unit,
    onDrop: (Int) -> Unit,
    onStashWithMessage: (String, Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
  var list by remember { mutableStateOf<List<StashDto>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var showStashDialog by remember { mutableStateOf(false) }
  var filterText by remember { mutableStateOf("") }
  var isRefreshing by remember { mutableStateOf(false) }
  var detailsFor by remember { mutableStateOf<StashDto?>(null) }

  LaunchedEffect(workdir, refreshTrigger) {
    if (workdir == null) {
      list = emptyList()
      errorMsg = null
      loading = false
      return@LaunchedEffect
    }
    if (list.isNotEmpty()) isRefreshing = true else loading = true
    errorMsg = null
    val out = mutableListOf<StashDto>()
    val err = withContext(Dispatchers.IO) {
      try {
        Repository.open(workdir).use { repo -> Libgit2Helper.stashList(repo, out) }
        null
      } catch (e: Exception) {
        e.localizedMessage ?: "加载 Stash 列表失败"
      }
    }
    if (err == null) {
      list = out
    } else {
      errorMsg = err
      list = emptyList()
    }
    loading = false
    isRefreshing = false
  }

  // toolbar "Stash All" 按钮递增 stashDialogTrigger, 这里观察变化弹出对话框。
  // 排除初始值 0, 避免首次进入页面就弹出。
  LaunchedEffect(stashDialogTrigger) {
    if (stashDialogTrigger > 0) showStashDialog = true
  }

  when {
    loading -> GitLoadingState()
    workdir == null -> GitEmptyState(message = "No opened project")
    errorMsg != null -> GitErrorState(message = errorMsg!!, onRetry = onRefresh)
    else -> {
      val filtered = if (filterText.isBlank()) list else list.filter {
        it.msg.contains(filterText, ignoreCase = true) ||
            it.index.toString() == filterText ||
            it.getCachedShortStashId().contains(filterText, ignoreCase = true)
      }
      PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
        Column(modifier = Modifier.fillMaxSize()) {
          GitFilterBar(
              value = filterText,
              onValueChange = { filterText = it },
              placeholder = "过滤 Stash (消息/序号/hash)",
          )
          if (filtered.isEmpty()) {
            GitEmptyState(if (filterText.isNotBlank()) "无匹配的 Stash" else "暂无 Stash")
          } else {
            StashList(
                list = filtered,
                onApply = onApply,
                onPop = onPop,
                onDrop = onDrop,
                onShowDetails = { detailsFor = it },
            )
          }
        }
      }
    }
  }

  if (showStashDialog) {
    StashMessageDialog(
        onConfirm = { msg, includeUntracked ->
          showStashDialog = false
          onStashWithMessage(msg, includeUntracked)
        },
        onDismiss = { showStashDialog = false },
    )
  }

  // Stash 详情对话框
  detailsFor?.let { dto ->
    AlertDialog(
        onDismissRequest = { detailsFor = null },
        title = { Text("Stash 详情") },
        text = {
          Column {
            DetailRow("序号", "#${dto.index}")
            DetailRow("ID", dto.stashId?.toString() ?: "(null)")
            DetailRow("短 ID", dto.getCachedShortStashId())
            Spacer(Modifier.height(8.dp))
            Text("消息:", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dto.msg.ifBlank { "(无消息)" }, style = MaterialTheme.typography.bodySmall)
          }
        },
        confirmButton = {
          TextButton(onClick = { detailsFor = null }) { Text("关闭") }
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
 * Stash 消息输入对话框。留空则由 [Libgit2Helper.stashGenMsg] 自动生成消息。
 * 支持 include untracked 选项 (对齐 puppygit API, 但 puppygit UI 自身未暴露)。
 */
@Composable
private fun StashMessageDialog(
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
  var message by remember { mutableStateOf("") }
  var includeUntracked by remember { mutableStateOf(false) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("新建 Stash") },
      text = {
        Column {
          Text(
              text = "输入 stash 消息 (留空则自动生成)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
              value = message,
              onValueChange = { message = it },
              label = { Text("Stash 消息") },
              placeholder = { Text("WIP: feature-x") },
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeUntracked, onCheckedChange = { includeUntracked = it })
            Text("Include untracked (包含未跟踪文件)",
                style = MaterialTheme.typography.bodySmall)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(message.trim(), includeUntracked) }) { Text("Stash") }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text("取消") }
      },
  )
}

@Composable
private fun StashList(
    list: List<StashDto>,
    onApply: (Int) -> Unit,
    onPop: (Int) -> Unit,
    onDrop: (Int) -> Unit,
    onShowDetails: (StashDto) -> Unit,
) {
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = GitSpacing.itemSpacing),
      verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
  ) {
    items(list, key = { it.index }) { dto ->
      StashItem(
          dto = dto,
          onApply = onApply,
          onPop = onPop,
          onDrop = onDrop,
          onShowDetails = { onShowDetails(dto) },
      )
    }
  }
}

/**
 * 单个 stash 卡片: 序号 (#0) + 单行消息 (粗体) + 短 hash (monospace 小字)。
 * 点击卡片弹出操作菜单 (Apply / Pop / Drop / 详情), Drop 需二次确认。
 */
@Composable
private fun StashItem(
    dto: StashDto,
    onApply: (Int) -> Unit,
    onPop: (Int) -> Unit,
    onDrop: (Int) -> Unit,
    onShowDetails: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }
  var showDropDialog by remember { mutableStateOf(false) }

  Box {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { showMenu = true },
            onLongClick = { onShowDetails() },
        ),
        shape = RoundedCornerShape(GitSpacing.cardCorner),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(GitSpacing.cardPadding),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = "#${dto.index}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = dto.getCachedOneLineMsg().ifBlank { "(no message)" },
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
              text = dto.getCachedShortStashId(),
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
      DropdownMenuItem(
          text = { Text("详情") },
          onClick = {
            showMenu = false
            onShowDetails()
          },
      )
      DropdownMenuItem(
          text = { Text("Apply") },
          onClick = {
            showMenu = false
            onApply(dto.index)
          },
      )
      DropdownMenuItem(
          text = { Text("Pop") },
          onClick = {
            showMenu = false
            onPop(dto.index)
          },
      )
      DropdownMenuItem(
          text = { Text("Drop") },
          onClick = {
            showMenu = false
            showDropDialog = true
          },
      )
    }
  }

  if (showDropDialog) {
    AlertDialog(
        onDismissRequest = { showDropDialog = false },
        title = { Text("删除 Stash") },
        text = { Text("确认删除 stash #${dto.index}？此操作不可撤销。") },
        confirmButton = {
          TextButton(
              onClick = {
                showDropDialog = false
                onDrop(dto.index)
              },
          ) {
            Text("删除", color = MaterialTheme.colorScheme.error)
          }
        },
        dismissButton = {
          TextButton(onClick = { showDropDialog = false }) { Text("取消") }
        },
    )
  }
}
