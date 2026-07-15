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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.catpuppyapp.puppygit.data.entity.RepoEntity
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Remote
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
 * - 列出所有 remote (名称 + fetch URL + push URL)
 * - 新建 remote
 * - 删除 remote
 * - 修改 fetch URL / push URL (push URL 留空则删除)
 * - 重命名 remote
 * - Fetch 单个 / 全部 remote
 *
 * git core 调用一比一复刻 puppygit:
 * - [Libgit2Helper.getRemoteList] 加载列表
 * - [Libgit2Helper.createRemote] 创建
 * - [Libgit2Helper.delRemote] 删除
 * - [Libgit2Helper.setRemoteUrlForRepo] 修改 fetch URL
 * - [Remote.setPushurl] / [Libgit2Helper.deletePushUrl] 管理 push URL
 * - [Remote.rename] 重命名
 * - [Libgit2Helper.fetchRemoteForRepo] fetch
 *
 * @author android_zero
 */
class GitRemotesFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitComposeBinding? = null
  private val binding
    get() = _binding!!

  private val refreshTrigger = mutableStateOf(0)
  private val newRemoteTrigger = mutableStateOf(0)
  private val fetchAllTrigger = mutableStateOf(0)

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

    addToolbarAction(R.drawable.ic_download, "Fetch 全部") {
      emitGitOperation("remotes", "fetch_all")
      fetchAllTrigger.value++
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
          fetchAllTrigger = fetchAllTrigger,
          onRefresh = { refreshTrigger.value++ },
          onCreate = ::createRemote,
          onDelete = ::deleteRemote,
          onEditUrl = ::editRemoteUrl,
          onEditPushUrl = ::editRemotePushUrl,
          onRename = ::renameRemote,
          onFetch = ::fetchRemote,
          onFetchAll = ::fetchAllRemotes,
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

  /** Fetch 指定 remote. */
  private fun fetchRemote(remoteName: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    val context = context ?: return
    GitCredentialManager.ensureConfigured(context) { cfg ->
      viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
          Repository.open(workdir).use { repo ->
            val cred = GitCredentialManager.toHttpCredential(cfg)
            val dummyRepo = RepoEntity()
            Libgit2Helper.fetchRemoteForRepo(repo, remoteName, cred, dummyRepo)
          }
        }
        withContext(Dispatchers.Main) {
          result.onSuccess { toast("已 fetch $remoteName"); refreshTrigger.value++ }
          result.onFailure { toast(it.localizedMessage ?: "Fetch 失败") }
        }
      }
    }
  }

  /**
   * Fetch 所有 remote。对齐 puppygit RemoteListScreen 的 fetchAll:
   * 遍历 [Libgit2Helper.getRemoteList], 逐个调用 [Libgit2Helper.fetchRemoteForRepo]。
   */
  private fun fetchAllRemotes() {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    val context = context ?: return
    GitCredentialManager.ensureConfigured(context) { cfg ->
      viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        var ok = 0
        var fail = 0
        val result = runCatching {
          Repository.open(workdir).use { repo ->
            val cred = GitCredentialManager.toHttpCredential(cfg)
            val dummyRepo = RepoEntity()
            val names = Libgit2Helper.getRemoteList(repo)
            for (name in names) {
              runCatching { Libgit2Helper.fetchRemoteForRepo(repo, name, cred, dummyRepo) }
                  .onSuccess { ok++ }
                  .onFailure { fail++ }
            }
          }
        }
        withContext(Dispatchers.Main) {
          result.onSuccess { toast("Fetch 完成: 成功 $ok, 失败 $fail"); refreshTrigger.value++ }
          result.onFailure { toast(it.localizedMessage ?: "Fetch 全部失败") }
        }
      }
    }
  }

  /**
   * 重命名 remote。对齐 puppygit RemoteListScreen:
   * [Remote.rename] 返回重命名失败的 ref 列表。
   */
  private fun renameRemote(oldName: String, newName: String) {
    if (newName.isBlank()) return toast("新名称不能为空")
    if (newName == oldName) return
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val problems = Remote.rename(repo, oldName, newName)
          if (problems.isNotEmpty()) {
            throw RuntimeException("部分 ref 重命名失败, 需手动修改 config:\n${problems.joinToString("\n")}")
          }
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已重命名 $oldName → $newName")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "重命名失败") }
      }
    }
  }

  /**
   * 设置/删除 remote 的 push URL。
   * 对齐 puppygit RemoteListScreen: newUrl 为空时调用 [Libgit2Helper.deletePushUrl] 删除,
   * 否则调用 [Remote.setPushurl] 设置。
   */
  private fun editRemotePushUrl(remoteName: String, newUrl: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          if (newUrl.isBlank()) {
            Libgit2Helper.deletePushUrl(Libgit2Helper.getRepoConfigForWrite(repo), remoteName)
          } else {
            Remote.setPushurl(repo, remoteName, newUrl)
          }
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast(if (newUrl.isBlank()) "已删除 $remoteName 的 push URL" else "已更新 $remoteName 的 push URL")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "更新 push URL 失败") }
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

private data class RemoteItemData(val name: String, val fetchUrl: String, val pushUrl: String)

private sealed interface RemoteListUiState {
  data object Loading : RemoteListUiState
  data object NoProject : RemoteListUiState
  data class Loaded(val remotes: List<RemoteItemData>) : RemoteListUiState
  data class Error(val message: String) : RemoteListUiState
}

/** 读取 remote 的 push URL (可能为空). */
private fun readRemotePushUrl(repo: Repository, remoteName: String): String =
    runCatching {
      Libgit2Helper.getRepoConfigForRead(repo)
          .getString("remote.$remoteName.pushurl")
          .orElse("")
    }.getOrDefault("")

@Composable
private fun RemoteListContent(
    workdir: String?,
    refreshTrigger: State<Int>,
    newRemoteTrigger: State<Int>,
    fetchAllTrigger: State<Int>,
    onRefresh: () -> Unit,
    onCreate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onEditUrl: (String, String) -> Unit,
    onEditPushUrl: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onFetch: (String) -> Unit,
    onFetchAll: () -> Unit,
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
                        val fetchUrl = Libgit2Helper.getRemoteFetchUrlByName(repo, name)
                        val pushUrl = readRemotePushUrl(repo, name)
                        RemoteItemData(name, fetchUrl, pushUrl)
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

  // Fetch 全部 trigger
  val faTrigger = fetchAllTrigger.value
  LaunchedEffect(faTrigger) {
    if (faTrigger > 0) onFetchAll()
  }

  var editing by remember { mutableStateOf<RemoteItemData?>(null) }
  var editingPushUrl by remember { mutableStateOf<RemoteItemData?>(null) }
  var renaming by remember { mutableStateOf<RemoteItemData?>(null) }
  var filterText by remember { mutableStateOf("") }
  var isRefreshing by remember { mutableStateOf(false) }
  var detailsFor by remember { mutableStateOf<RemoteItemData?>(null) }

  // 加载完成 (无论成功/失败/空) 后关闭下拉刷新指示器
  LaunchedEffect(uiState) {
    if (uiState !is RemoteListUiState.Loading) isRefreshing = false
  }

  when (val s = uiState) {
    RemoteListUiState.Loading -> GitLoadingState()
    RemoteListUiState.NoProject -> GitEmptyState(message = "未打开工程")
    is RemoteListUiState.Error -> GitErrorState(message = s.message, onRetry = onRefresh)
    is RemoteListUiState.Loaded -> {
      val filtered = if (filterText.isBlank()) s.remotes else s.remotes.filter {
        it.name.contains(filterText, ignoreCase = true) ||
            it.fetchUrl.contains(filterText, ignoreCase = true) ||
            it.pushUrl.contains(filterText, ignoreCase = true)
      }
      PullToRefreshBox(
          isRefreshing = isRefreshing,
          onRefresh = {
            isRefreshing = true
            onRefresh()
          },
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          GitFilterBar(
              value = filterText,
              onValueChange = { filterText = it },
              placeholder = "过滤 Remote (名称/URL)",
          )
          if (filtered.isEmpty()) {
            GitEmptyState(
                message =
                    if (filterText.isNotBlank()) "无匹配的 Remote"
                    else "暂无 Remote\n点击 + 添加远程仓库",
                icon = Icons.Outlined.Cloud,
            )
          } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
            ) {
              items(filtered, key = { it.name }) { remote ->
                RemoteItem(
                    remote = remote,
                    onFetch = { onFetch(remote.name) },
                    onEditUrl = { editing = remote },
                    onEditPushUrl = { editingPushUrl = remote },
                    onRename = { renaming = remote },
                    onShowDetails = { detailsFor = remote },
                    onDelete = { onDelete(remote.name) },
                )
              }
            }
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
  editingPushUrl?.let { remote ->
    RemoteEditPushUrlDialog(
        remoteName = remote.name,
        currentUrl = remote.pushUrl,
        onConfirm = { newUrl ->
          editingPushUrl = null
          onEditPushUrl(remote.name, newUrl.trim())
        },
        onDismiss = { editingPushUrl = null },
    )
  }
  renaming?.let { remote ->
    RemoteRenameDialog(
        remoteName = remote.name,
        onConfirm = { newName ->
          renaming = null
          onRename(remote.name, newName.trim())
        },
        onDismiss = { renaming = null },
    )
  }
  // Remote 详情对话框
  detailsFor?.let { remote ->
    AlertDialog(
        onDismissRequest = { detailsFor = null },
        title = { Text("Remote 详情") },
        text = {
          Column {
            DetailRow("名称", remote.name)
            DetailRow("Fetch URL", remote.fetchUrl.ifBlank { "(无)" })
            DetailRow("Push URL", remote.pushUrl.ifBlank { "(default: uses fetch URL)" })
          }
        },
        confirmButton = {
          TextButton(onClick = { detailsFor = null }) { Text("关闭") }
        },
    )
  }
}

@Composable
private fun RemoteItem(
    remote: RemoteItemData,
    onFetch: () -> Unit,
    onEditUrl: () -> Unit,
    onEditPushUrl: () -> Unit,
    onRename: () -> Unit,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  Card(
      modifier = Modifier.fillMaxWidth().combinedClickable(
          onClick = { menuExpanded = true },
          onLongClick = { menuExpanded = true },
      ),
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
            text = "fetch: " + remote.fetchUrl.ifBlank { "(无)" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (remote.pushUrl.isNotBlank()) {
          Text(
              text = "push: " + remote.pushUrl,
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
        }
      }
      IconButton(onClick = { menuExpanded = true }) {
        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "更多操作",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(text = { Text("详情") }, onClick = {
          menuExpanded = false
          onShowDetails()
        })
        DropdownMenuItem(text = { Text("Fetch") }, onClick = {
          menuExpanded = false
          onFetch()
        })
        DropdownMenuItem(text = { Text("修改 Fetch URL") }, onClick = {
          menuExpanded = false
          onEditUrl()
        })
        DropdownMenuItem(text = { Text("修改 Push URL") }, onClick = {
          menuExpanded = false
          onEditPushUrl()
        })
        DropdownMenuItem(text = { Text("重命名") }, onClick = {
          menuExpanded = false
          onRename()
        })
        DropdownMenuItem(text = { Text("删除") }, onClick = {
          menuExpanded = false
          onDelete()
        })
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

/** 修改 push URL 对话框: 留空则删除 push URL. */
@Composable
private fun RemoteEditPushUrlDialog(
    remoteName: String,
    currentUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  var url by remember { mutableStateOf(currentUrl) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("修改 $remoteName Push URL") },
      text = {
        Column {
          OutlinedTextField(
              value = url,
              onValueChange = { url = it },
              label = { Text("Push URL") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          Text(
              text = "留空将删除当前 push URL (回退到使用 fetch URL)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(url) }) { Text("保存") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

/** 重命名 remote 对话框. */
@Composable
private fun RemoteRenameDialog(
    remoteName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  var newName by remember { mutableStateOf(remoteName) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("重命名 $remoteName") },
      text = {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("新名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(newName) }) { Text("重命名") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

/** 详情对话框中的键值对行. 长 URL 单行省略号显示. */
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
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
  }
}
