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
import androidx.compose.material3.OutlinedTextField
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
import com.catpuppyapp.puppygit.data.entity.CredentialEntity
import com.catpuppyapp.puppygit.data.repository.CredentialRepository
import com.catpuppyapp.puppygit.git.SubmoduleDto
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitSubmoduleComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Git Submodule 管理页面 —— 独立设计的 Compose UI。
 *
 * 功能:
 * - 列出所有子模块 ([Libgit2Helper.getSubmoduleDtoList])
 * - 添加子模块 ([Libgit2Helper.addSubmodule])
 * - 删除子模块 ([Libgit2Helper.removeSubmodule])
 * - 修改子模块 URL ([Libgit2Helper.updateSubmoduleUrl])
 * - 克隆单个/全部子模块 ([Libgit2Helper.cloneSubmodules])
 * - 更新单个/全部子模块 ([Libgit2Helper.updateSubmodule])
 *
 * 凭据策略: 复用 [GitCredentialManager.ensureConfigured] 得到 cfg, 用
 * [GitCredentialManager.toHttpCredential] 构造一个具体的 [CredentialEntity] 作为
 * specifiedCredential 传入. 因其 id 为随机 UUID (非 "match_by_domain"),
 * `cloneSubmodules`/`updateSubmodule` 内部不会查询 credentialDb, 故可传入
 * [NoOpCredentialRepository] 占位.
 *
 * @author android_zero
 */
class GitSubmoduleFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitSubmoduleComposeBinding? = null
  private val binding
    get() = _binding!!

  private val refreshTrigger = mutableStateOf(0)
  private val addDialogTrigger = mutableStateOf(0)
  private val cloneAllTrigger = mutableStateOf(0)
  private val updateAllTrigger = mutableStateOf(0)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitSubmoduleComposeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_refresh_24, getString(R.string.refresh)) {
      refreshTrigger.value++
    }

    addToolbarAction(R.drawable.ic_add_24, "添加子模块") {
      addDialogTrigger.value++
    }

    addToolbarAction(R.drawable.ic_cloud_download_24, "克隆全部子模块") {
      cloneAllTrigger.value++
    }

    addToolbarAction(R.drawable.ic_sync, "更新全部子模块") {
      updateAllTrigger.value++
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()

    val compose = setIdeContent {
      SubmoduleListContent(
          workdir = workdir,
          refreshTrigger = refreshTrigger.value,
          addDialogTrigger = addDialogTrigger.value,
          cloneAllTrigger = cloneAllTrigger.value,
          updateAllTrigger = updateAllTrigger.value,
          onAdd = ::addSubmodule,
          onRemove = ::removeSubmodule,
          onUpdateUrl = ::updateSubmoduleUrl,
          onClone = ::cloneSubmodule,
          onUpdate = ::updateSubmodule,
          onCloneAll = ::cloneAllSubmodules,
          onUpdateAll = ::updateAllSubmodules,
          onRefresh = { refreshTrigger.value++ },
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  // ---- submodule 操作 ----

  /** 添加子模块. */
  private fun addSubmodule(remoteUrl: String, relativePath: String) {
    if (remoteUrl.isBlank() || relativePath.isBlank()) {
      toast("URL 和路径不能为空")
      return
    }
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          Libgit2Helper.addSubmodule(repo, remoteUrl, relativePath)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已添加子模块 $relativePath")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "添加子模块失败") }
      }
    }
  }

  /** 删除子模块 (同时删除文件和配置). */
  private fun removeSubmodule(dto: SubmoduleDto) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          Libgit2Helper.removeSubmodule(
              deleteFiles = true,
              deleteConfigs = true,
              repo = repo,
              repoWorkDirPath = workdir,
              submoduleName = dto.name,
              submoduleFullPath = dto.fullPath,
          )
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已删除子模块 ${dto.name}")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "删除子模块失败") }
      }
    }
  }

  /** 修改子模块的远程 URL. */
  private fun updateSubmoduleUrl(submoduleName: String, newUrl: String) {
    if (newUrl.isBlank()) return toast("URL 不能为空")
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val sm = Libgit2Helper.resolveSubmodule(repo, submoduleName)
            ?: throw RuntimeException("找不到子模块 $submoduleName")
          Libgit2Helper.updateSubmoduleUrl(repo, sm, newUrl)
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已更新 $submoduleName 的 URL")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "更新 URL 失败") }
      }
    }
  }

  /**
   * 克隆单个子模块. 若已克隆则跳过 (由 [Libgit2Helper.cloneSubmodules] 内部判断).
   * 非递归, depth=0 (完整克隆).
   */
  private fun cloneSubmodule(dto: SubmoduleDto) {
    cloneSubmodules(names = listOf(dto.name), recursive = false, successMsg = "已克隆子模块 ${dto.name}")
  }

  /**
   * 更新单个子模块. 非递归.
   */
  private fun updateSubmodule(dto: SubmoduleDto) {
    updateSubmodules(names = listOf(dto.name), recursive = false, successMsg = "已更新子模块 ${dto.name}")
  }

  /** 克隆全部子模块, 递归. */
  private fun cloneAllSubmodules() {
    cloneSubmodules(names = null, recursive = true, successMsg = "已克隆全部子模块")
  }

  /** 更新全部子模块, 递归. */
  private fun updateAllSubmodules() {
    updateSubmodules(names = null, recursive = true, successMsg = "已更新全部子模块")
  }

  /**
   * 克隆子模块通用入口.
   * @param names 指定子模块名列表, null 表示全部
   */
  private fun cloneSubmodules(names: List<String>?, recursive: Boolean, successMsg: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    val ctx = context ?: return
    GitCredentialManager.ensureConfigured(ctx) { cfg ->
      viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
          Repository.open(workdir).use { repo ->
            val cred = GitCredentialManager.toHttpCredential(cfg)
            val nameList = names ?: Libgit2Helper.getSubmoduleNameList(repo)
            if (nameList.isEmpty()) throw RuntimeException("没有子模块")
            Libgit2Helper.cloneSubmodules(
                repo = repo,
                recursive = recursive,
                depth = 0,
                specifiedCredential = cred,
                submoduleNameList = nameList,
                credentialDb = NoOpCredentialRepository,
            )
          }
        }
        withContext(Dispatchers.Main) {
          result.onSuccess { toast(successMsg); refreshTrigger.value++ }
          result.onFailure { toast(it.localizedMessage ?: "克隆子模块失败") }
        }
      }
    }
  }

  /**
   * 更新子模块通用入口.
   * @param names 指定子模块名列表, null 表示全部
   */
  private fun updateSubmodules(names: List<String>?, recursive: Boolean, successMsg: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    val ctx = context ?: return
    GitCredentialManager.ensureConfigured(ctx) { cfg ->
      viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
          Repository.open(workdir).use { repo ->
            val cred = GitCredentialManager.toHttpCredential(cfg)
            val nameList = names ?: Libgit2Helper.getSubmoduleNameList(repo)
            if (nameList.isEmpty()) throw RuntimeException("没有子模块")
            Libgit2Helper.updateSubmodule(
                parentRepo = repo,
                specifiedCredential = cred,
                submoduleNameList = nameList,
                recursive = recursive,
                credentialDb = NoOpCredentialRepository,
                superParentRepo = repo,
            )
          }
        }
        withContext(Dispatchers.Main) {
          result.onSuccess { toast(successMsg); refreshTrigger.value++ }
          result.onFailure { toast(it.localizedMessage ?: "更新子模块失败") }
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

// ---- 独立设计的 Compose UI ----

private sealed interface SubmoduleUiState {
  data object Loading : SubmoduleUiState
  data object NoProject : SubmoduleUiState
  data class Loaded(val list: List<SubmoduleDto>) : SubmoduleUiState
  data class Error(val message: String) : SubmoduleUiState
}

@Composable
private fun SubmoduleListContent(
    workdir: String?,
    refreshTrigger: Int,
    addDialogTrigger: Int,
    cloneAllTrigger: Int,
    updateAllTrigger: Int,
    onAdd: (remoteUrl: String, relativePath: String) -> Unit,
    onRemove: (SubmoduleDto) -> Unit,
    onUpdateUrl: (submoduleName: String, newUrl: String) -> Unit,
    onClone: (SubmoduleDto) -> Unit,
    onUpdate: (SubmoduleDto) -> Unit,
    onCloneAll: () -> Unit,
    onUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
) {
  var uiState by remember { mutableStateOf<SubmoduleUiState>(SubmoduleUiState.Loading) }
  var showAddDialog by remember { mutableStateOf(false) }
  var removing by remember { mutableStateOf<SubmoduleDto?>(null) }
  var editingUrl by remember { mutableStateOf<SubmoduleDto?>(null) }
  var cloning by remember { mutableStateOf<SubmoduleDto?>(null) }
  var updating by remember { mutableStateOf<SubmoduleDto?>(null) }
  var confirmCloneAll by remember { mutableStateOf(false) }
  var confirmUpdateAll by remember { mutableStateOf(false) }

  LaunchedEffect(workdir, refreshTrigger) {
    if (workdir == null) {
      uiState = SubmoduleUiState.NoProject
      return@LaunchedEffect
    }
    uiState = SubmoduleUiState.Loading
    val result = withContext(Dispatchers.IO) {
      runCatching {
        Repository.open(workdir).use { repo ->
          Libgit2Helper.getSubmoduleDtoList(repo, "(无效 URL)")
        }
      }
    }
    uiState = result.fold(
        onSuccess = { SubmoduleUiState.Loaded(it) },
        onFailure = { SubmoduleUiState.Error(it.localizedMessage ?: "加载子模块失败") },
    )
  }

  LaunchedEffect(addDialogTrigger) {
    if (addDialogTrigger > 0) showAddDialog = true
  }
  LaunchedEffect(cloneAllTrigger) {
    if (cloneAllTrigger > 0) confirmCloneAll = true
  }
  LaunchedEffect(updateAllTrigger) {
    if (updateAllTrigger > 0) confirmUpdateAll = true
  }

  when (val s = uiState) {
    SubmoduleUiState.Loading -> GitLoadingState()
    SubmoduleUiState.NoProject -> GitEmptyState(message = "未打开工程")
    is SubmoduleUiState.Error -> GitErrorState(message = s.message, onRetry = onRefresh)
    is SubmoduleUiState.Loaded -> {
      if (s.list.isEmpty()) {
        GitEmptyState(message = "暂无子模块")
      } else {
        SubmoduleList(
            list = s.list,
            onRemoveRequest = { removing = it },
            onEditUrlRequest = { editingUrl = it },
            onCloneRequest = { cloning = it },
            onUpdateRequest = { updating = it },
        )
      }
    }
  }

  if (showAddDialog) {
    SubmoduleAddDialog(
        onConfirm = { url, path ->
          showAddDialog = false
          onAdd(url, path)
        },
        onDismiss = { showAddDialog = false },
    )
  }
  removing?.let { dto ->
    AlertDialog(
        onDismissRequest = { removing = null },
        title = { Text("删除子模块") },
        text = { Text("确认删除子模块 ${dto.name}?\n将同时删除文件和配置，此操作不可撤销!") },
        confirmButton = {
          TextButton(onClick = {
            removing = null
            onRemove(dto)
          }) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } },
    )
  }
  editingUrl?.let { dto ->
    SubmoduleEditUrlDialog(
        submodule = dto,
        onConfirm = { newUrl ->
          editingUrl = null
          onUpdateUrl(dto.name, newUrl)
        },
        onDismiss = { editingUrl = null },
    )
  }
  cloning?.let { dto ->
    AlertDialog(
        onDismissRequest = { cloning = null },
        title = { Text("克隆子模块") },
        text = { Text("确认克隆子模块 ${dto.name}?\n将从 ${dto.remoteUrl} 拉取到 ${dto.relativePathUnderParent}") },
        confirmButton = {
          TextButton(onClick = {
            cloning = null
            onClone(dto)
          }) { Text("克隆") }
        },
        dismissButton = { TextButton(onClick = { cloning = null }) { Text("取消") } },
    )
  }
  updating?.let { dto ->
    AlertDialog(
        onDismissRequest = { updating = null },
        title = { Text("更新子模块") },
        text = { Text("确认更新子模块 ${dto.name} 到远程最新提交?") },
        confirmButton = {
          TextButton(onClick = {
            updating = null
            onUpdate(dto)
          }) { Text("更新") }
        },
        dismissButton = { TextButton(onClick = { updating = null }) { Text("取消") } },
    )
  }
  if (confirmCloneAll) {
    AlertDialog(
        onDismissRequest = { confirmCloneAll = false },
        title = { Text("克隆全部子模块") },
        text = { Text("将递归克隆所有未克隆的子模块，可能需要网络连接。继续?") },
        confirmButton = {
          TextButton(onClick = {
            confirmCloneAll = false
            onCloneAll()
          }) { Text("克隆全部") }
        },
        dismissButton = { TextButton(onClick = { confirmCloneAll = false }) { Text("取消") } },
    )
  }
  if (confirmUpdateAll) {
    AlertDialog(
        onDismissRequest = { confirmUpdateAll = false },
        title = { Text("更新全部子模块") },
        text = { Text("将递归更新所有子模块到远程最新提交，可能需要网络连接。继续?") },
        confirmButton = {
          TextButton(onClick = {
            confirmUpdateAll = false
            onUpdateAll()
          }) { Text("更新全部") }
        },
        dismissButton = { TextButton(onClick = { confirmUpdateAll = false }) { Text("取消") } },
    )
  }
}

@Composable
private fun SubmoduleList(
    list: List<SubmoduleDto>,
    onRemoveRequest: (SubmoduleDto) -> Unit,
    onEditUrlRequest: (SubmoduleDto) -> Unit,
    onCloneRequest: (SubmoduleDto) -> Unit,
    onUpdateRequest: (SubmoduleDto) -> Unit,
) {
  LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
  ) {
    items(list, key = { it.name }) { dto ->
      SubmoduleItem(
          dto = dto,
          onRemoveRequest = onRemoveRequest,
          onEditUrlRequest = onEditUrlRequest,
          onCloneRequest = onCloneRequest,
          onUpdateRequest = onUpdateRequest,
      )
    }
  }
}

@Composable
private fun SubmoduleItem(
    dto: SubmoduleDto,
    onRemoveRequest: (SubmoduleDto) -> Unit,
    onEditUrlRequest: (SubmoduleDto) -> Unit,
    onCloneRequest: (SubmoduleDto) -> Unit,
    onUpdateRequest: (SubmoduleDto) -> Unit,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
      modifier = Modifier.fillMaxWidth().clickable { menuExpanded = true },
      shape = RoundedCornerShape(GitSpacing.cardCorner),
      colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
      ),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(GitSpacing.cardPadding)) {
      Text(
          text = dto.name,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
          text = dto.remoteUrl,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
              text = dto.relativePathUnderParent,
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
        )
        if (dto.targetHash.isNotBlank()) {
          Text(
              text = dto.getShortTargetHashCached(),
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      if (dto.cloned) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "已克隆",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
      } else {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "未克隆",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
      }
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
      DropdownMenuItem(
          text = { Text(if (dto.cloned) "重新克隆" else "克隆") },
          onClick = {
            menuExpanded = false
            onCloneRequest(dto)
          },
      )
      DropdownMenuItem(
          text = { Text("更新") },
          onClick = {
            menuExpanded = false
            onUpdateRequest(dto)
          },
      )
      DropdownMenuItem(
          text = { Text("修改 URL") },
          onClick = {
            menuExpanded = false
            onEditUrlRequest(dto)
          },
      )
      DropdownMenuItem(
          text = { Text("删除") },
          onClick = {
            menuExpanded = false
            onRemoveRequest(dto)
          },
      )
    }
  }
}

/** 添加子模块对话框: 输入远程 URL + 相对路径. */
@Composable
private fun SubmoduleAddDialog(
    onConfirm: (remoteUrl: String, relativePath: String) -> Unit,
    onDismiss: () -> Unit,
) {
  var url by remember { mutableStateOf("") }
  var path by remember { mutableStateOf("") }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("添加子模块") },
      text = {
        Column {
          OutlinedTextField(
              value = url,
              onValueChange = { url = it },
              label = { Text("远程仓库 URL") },
              placeholder = { Text("https://github.com/user/repo.git") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
              value = path,
              onValueChange = { path = it },
              label = { Text("相对路径 (如 libs/mylib)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(url.trim(), path.trim()) }) { Text("添加") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

/** 修改子模块 URL 对话框. */
@Composable
private fun SubmoduleEditUrlDialog(
    submodule: SubmoduleDto,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  var url by remember { mutableStateOf(submodule.remoteUrl) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("修改 ${submodule.name} 的 URL") },
      text = {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("远程 URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(url.trim()) }) { Text("保存") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

/**
 * 空实现的 [CredentialRepository], 所有方法均抛 [UnsupportedOperationException].
 *
 * 用于 [Libgit2Helper.cloneSubmodules] / [Libgit2Helper.updateSubmodule] 的
 * `credentialDb` 占位参数: 当传入一个具体的 `specifiedCredential` (id 非 "match_by_domain")
 * 时, 这两个方法内部永远不会查询 `credentialDb`, 因此此占位实现不会被实际调用.
 */
private object NoOpCredentialRepository : CredentialRepository {
  private fun unsupported(): Nothing = throw UnsupportedOperationException("NoOpCredentialRepository 不支持此操作")

  override suspend fun getAllWithDecrypt(includeNone: Boolean, includeMatchByDomain: Boolean, masterPassword: String): List<CredentialEntity> = unsupported()
  override suspend fun getAll(includeNone: Boolean, includeMatchByDomain: Boolean): List<CredentialEntity> = unsupported()
  override suspend fun insertWithEncrypt(item: CredentialEntity, masterPassword: String) = unsupported()
  override suspend fun insert(item: CredentialEntity) = unsupported()
  override suspend fun delete(item: CredentialEntity) = unsupported()
  override suspend fun updateWithEncrypt(item: CredentialEntity, touchTime: Boolean, masterPassword: String) = unsupported()
  override suspend fun update(item: CredentialEntity, touchTime: Boolean) = unsupported()
  override suspend fun isCredentialNameExist(name: String): Boolean = unsupported()
  override suspend fun getByIdWithDecrypt(id: String, masterPassword: String): CredentialEntity? = unsupported()
  override suspend fun getByIdWithDecryptAndMatchByDomain(id: String, url: String, masterPassword: String): CredentialEntity? = unsupported()
  override suspend fun getByIdAndMatchByDomain(id: String, url: String): CredentialEntity? = unsupported()
  override suspend fun getById(id: String, includeNone: Boolean, includeMatchByDomain: Boolean): CredentialEntity? = unsupported()
  override suspend fun deleteAndUnlink(item: CredentialEntity) = unsupported()
  override fun encryptPassIfNeed(item: CredentialEntity?, masterPassword: String) = unsupported()
  override fun decryptPassIfNeed(item: CredentialEntity?, masterPassword: String) = unsupported()
  override suspend fun updateMasterPassword(oldMasterPassword: String, newMasterPassword: String): List<String> = unsupported()
  override suspend fun migrateEncryptVerIfNeed(masterPassword: String) = unsupported()
  override suspend fun getByEncryptVerNotEqualsTo(encryptVer: Int): List<CredentialEntity> = unsupported()
  override suspend fun subtractTimeOffset(offsetInSec: Long) = unsupported()
}
