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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitStashComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 2a2-A 迁移: Git Stash 管理页面。
 *
 * 改用独立设计的 Compose UI 渲染 (不再套娃 puppygit 的 `StashListScreen`,
 * 避免双 toolbar)。数据加载一比一复刻 puppygit 的 git core 调用
 * (`Libgit2Helper.stashList`), 但 UI 完全独立, 仅用 AndroidIDE 自己的
 * [MaterialTheme] 包裹 (见 [setIdeContent])。
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
      stashAll()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()

    val compose = setIdeContent {
      StashListContent(
          workdir = workdir,
          refreshTrigger = refreshTrigger.value,
          onApply = ::applyStash,
          onPop = ::popStash,
          onDrop = ::dropStash,
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

  private fun stashAll() = performStashAction("Stash All") { repo ->
    val (username, email) = Libgit2Helper.getGitUserNameAndEmailFromRepo(repo)
    val sig = Signature.create(username, email)
    Libgit2Helper.stashSave(repo, stasher = sig, msg = Libgit2Helper.stashGenMsg())
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
 */
@Composable
private fun StashListContent(
    workdir: String?,
    refreshTrigger: Int,
    onApply: (Int) -> Unit,
    onPop: (Int) -> Unit,
    onDrop: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
  var list by remember { mutableStateOf<List<StashDto>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(workdir, refreshTrigger) {
    if (workdir == null) {
      list = emptyList()
      errorMsg = null
      loading = false
      return@LaunchedEffect
    }
    loading = true
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
  }

  when {
    loading -> GitLoadingState()
    workdir == null -> GitEmptyState(message = "No opened project")
    errorMsg != null -> GitErrorState(message = errorMsg!!, onRetry = onRefresh)
    list.isEmpty() -> GitEmptyState(message = "暂无 Stash")
    else -> StashList(list = list, onApply = onApply, onPop = onPop, onDrop = onDrop)
  }
}

@Composable
private fun StashList(
    list: List<StashDto>,
    onApply: (Int) -> Unit,
    onPop: (Int) -> Unit,
    onDrop: (Int) -> Unit,
) {
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = GitSpacing.itemSpacing),
      verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
  ) {
    items(list, key = { it.index }) { dto ->
      StashItem(dto = dto, onApply = onApply, onPop = onPop, onDrop = onDrop)
    }
  }
}

/**
 * 单个 stash 卡片: 序号 (#0) + 单行消息 (粗体) + 短 hash (monospace 小字)。
 * 点击卡片弹出操作菜单 (Apply / Pop / Drop), Drop 需二次确认。
 */
@Composable
private fun StashItem(
    dto: StashDto,
    onApply: (Int) -> Unit,
    onPop: (Int) -> Unit,
    onDrop: (Int) -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }
  var showDropDialog by remember { mutableStateOf(false) }

  Box {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { showMenu = true },
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
