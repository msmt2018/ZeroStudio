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
 *
 * @author android_zero
 */
class GitSubmoduleFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitSubmoduleComposeBinding? = null
  private val binding
    get() = _binding!!

  private val refreshTrigger = mutableStateOf(0)
  private val addDialogTrigger = mutableStateOf(0)

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
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()

    val compose = setIdeContent {
      SubmoduleListContent(
          workdir = workdir,
          refreshTrigger = refreshTrigger.value,
          addDialogTrigger = addDialogTrigger.value,
          onAdd = ::addSubmodule,
          onRemove = ::removeSubmodule,
          onUpdateUrl = ::updateSubmoduleUrl,
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
    onAdd: (remoteUrl: String, relativePath: String) -> Unit,
    onRemove: (SubmoduleDto) -> Unit,
    onUpdateUrl: (submoduleName: String, newUrl: String) -> Unit,
    onRefresh: () -> Unit,
) {
  var uiState by remember { mutableStateOf<SubmoduleUiState>(SubmoduleUiState.Loading) }
  var showAddDialog by remember { mutableStateOf(false) }
  var removing by remember { mutableStateOf<SubmoduleDto?>(null) }
  var editingUrl by remember { mutableStateOf<SubmoduleDto?>(null) }

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
}

@Composable
private fun SubmoduleList(
    list: List<SubmoduleDto>,
    onRemoveRequest: (SubmoduleDto) -> Unit,
    onEditUrlRequest: (SubmoduleDto) -> Unit,
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
      )
    }
  }
}

@Composable
private fun SubmoduleItem(
    dto: SubmoduleDto,
    onRemoveRequest: (SubmoduleDto) -> Unit,
    onEditUrlRequest: (SubmoduleDto) -> Unit,
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
