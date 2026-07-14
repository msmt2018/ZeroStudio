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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Remote 管理页面 —— 独立设计的 Compose UI。
 *
 * 功能:
 * - 列出所有 remote (名称 + fetch URL)
 * - 新建 remote
 * - 删除 remote
 * - 修改 remote URL
 *
 * git core 调用一比一复刻 puppygit:
 * - [Libgit2Helper.getRemoteList] 加载列表
 * - [Libgit2Helper.createRemote] 创建
 * - [Libgit2Helper.delRemote] 删除
 * - [Libgit2Helper.setRemoteUrlForRepo] 修改 URL
 * - [Libgit2Helper.getRemoteFetchUrlByName] 读取 URL
 *
 * @author android_zero
 */
class GitRemotesFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitComposeBinding? = null
  private val binding
    get() = _binding!!

  private val refreshTrigger = mutableStateOf(0)
  private val newRemoteTrigger = mutableStateOf(0)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitComposeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      emitGitOperation("remotes", "refresh")
      refreshTrigger.value++
    }

    addToolbarAction(R.drawable.ic_add_24, "添加 Remote") {
      emitGitOperation("remotes", "create_remote_dialog")
      newRemoteTrigger.value++
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()
    val compose = setIdeContent {
      RemoteListContent(
          workdir = workdir,
          refreshTrigger = refreshTrigger,
          newRemoteTrigger = newRemoteTrigger,
          onRefresh = { refreshTrigger.value++ },
          onCreate = ::createRemote,
          onDelete = ::deleteRemote,
          onEditUrl = ::editRemoteUrl,
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  private fun createRemote(name: String, url: String) {
    if (name.isBlank() || url.isBlank()) return toast("名称和 URL 不能为空")
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret = Libgit2Helper.createRemote(repo, name, url)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已添加 remote $name")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "添加 remote 失败") }
      }
    }
  }

  private fun deleteRemote(name: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val ret = Libgit2Helper.delRemote(repo, name)
          if (ret.hasError()) throw RuntimeException(ret.msg)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已删除 remote $name")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "删除 remote 失败") }
      }
    }
  }

  private fun editRemoteUrl(name: String, newUrl: String) {
    if (newUrl.isBlank()) return toast("URL 不能为空")
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          Libgit2Helper.setRemoteUrlForRepo(repo, name, newUrl)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已更新 $name 的 URL")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "更新 URL 失败") }
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

// ---- Compose UI ----

private data class RemoteItemData(val name: String, val fetchUrl: String)

private sealed interface RemoteListUiState {
  data object Loading : RemoteListUiState
  data object NoProject : RemoteListUiState
  data class Loaded(val remotes: List<RemoteItemData>) : RemoteListUiState
  data class Error(val message: String) : RemoteListUiState
}

@Composable
private fun RemoteListContent(
    workdir: String?,
    refreshTrigger: State<Int>,
    newRemoteTrigger: State<Int>,
    onRefresh: () -> Unit,
    onCreate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onEditUrl: (String, String) -> Unit,
) {
  val trigger = refreshTrigger.value
  val uiState by produceState<RemoteListUiState>(RemoteListUiState.Loading, trigger, workdir) {
    if (workdir == null) {
      value = RemoteListUiState.NoProject
      return@produceState
    }
    value = RemoteListUiState.Loading
    value =
        runCatching {
              withContext(Dispatchers.IO) {
                Repository.open(workdir).use { repo ->
                  val names = Libgit2Helper.getRemoteList(repo)
                  val remotes =
                      names.map { name ->
                        val url = Libgit2Helper.getRemoteFetchUrlByName(repo, name)
                        RemoteItemData(name, url)
                      }
                  RemoteListUiState.Loaded(remotes)
                }
              }
            }
            .getOrElse { RemoteListUiState.Error(it.localizedMessage ?: "加载 remote 列表失败") }
  }

  val nrTrigger = newRemoteTrigger.value
  var showCreateDialog by remember { mutableStateOf(false) }
  LaunchedEffect(nrTrigger) {
    if (nrTrigger > 0) showCreateDialog = true
  }
  var editing by remember { mutableStateOf<RemoteItemData?>(null) }

  when (val s = uiState) {
    RemoteListUiState.Loading -> GitLoadingState()
    RemoteListUiState.NoProject -> GitEmptyState(message = "未打开工程")
    is RemoteListUiState.Error -> GitErrorState(message = s.message, onRetry = onRefresh)
    is RemoteListUiState.Loaded -> {
      if (s.remotes.isEmpty()) {
        GitEmptyState(message = "暂无 Remote\n点击 + 添加远程仓库", icon = Icons.Outlined.Cloud)
      } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
        ) {
          items(s.remotes, key = { it.name }) { remote ->
            RemoteItem(
                remote = remote,
                onDelete = { onDelete(remote.name) },
                onEdit = { editing = remote },
            )
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    RemoteCreateDialog(
        onConfirm = { name, url ->
          showCreateDialog = false
          onCreate(name.trim(), url.trim())
        },
        onDismiss = { showCreateDialog = false },
    )
  }
  editing?.let { remote ->
    RemoteEditUrlDialog(
        remoteName = remote.name,
        currentUrl = remote.fetchUrl,
        onConfirm = { newUrl ->
          editing = null
          onEditUrl(remote.name, newUrl.trim())
        },
        onDismiss = { editing = null },
    )
  }
}

@Composable
private fun RemoteItem(
    remote: RemoteItemData,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(GitSpacing.cardCorner),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(GitSpacing.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Outlined.Cloud,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(end = 12.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = remote.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = remote.fetchUrl.ifBlank { "(无 URL)" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onEdit) {
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "修改 URL",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
      }
      IconButton(onClick = onDelete) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = "删除",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
      }
    }
  }
}

@Composable
private fun RemoteCreateDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var url by remember { mutableStateOf("") }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("添加 Remote") },
      text = {
        Column {
          OutlinedTextField(
              value = name,
              onValueChange = { name = it },
              label = { Text("名称 (如 origin)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
              value = url,
              onValueChange = { url = it },
              label = { Text("URL (https://... 或 git@...)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(name, url) }) { Text("添加") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

@Composable
private fun RemoteEditUrlDialog(
    remoteName: String,
    currentUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  var url by remember { mutableStateOf(currentUrl) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("修改 $remoteName URL") },
      text = {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(url) }) { Text("保存") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}
