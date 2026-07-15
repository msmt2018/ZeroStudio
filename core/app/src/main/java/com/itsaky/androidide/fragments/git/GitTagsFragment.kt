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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalOffer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.catpuppyapp.puppygit.data.entity.CredentialEntity
import com.catpuppyapp.puppygit.git.RemoteAndCredentials
import com.catpuppyapp.puppygit.git.TagDto
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Commit
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tag 管理页面 —— 独立设计的 Compose UI。
 *
 * 功能:
 * - 列出所有 tag (轻量 / 附注)
 * - 新建轻量 tag (基于 HEAD)
 * - 删除 tag
 *
 * git core 调用一比一复刻 puppygit:
 * - [Libgit2Helper.getAllTags] 加载列表
 * - [Libgit2Helper.createTagLight] 创建轻量 tag
 * - [Libgit2Helper.delTags] 删除 tag
 *
 * @author android_zero
 */
class GitTagsFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitComposeBinding? = null
  private val binding
    get() = _binding!!

  private val refreshTrigger = mutableStateOf(0)
  private val newTagTrigger = mutableStateOf(0)

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
      emitGitOperation("tags", "refresh")
      refreshTrigger.value++
    }

    addToolbarAction(R.drawable.ic_add_24, "新建 Tag") {
      emitGitOperation("tags", "create_tag_dialog")
      newTagTrigger.value++
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()
    val compose = setIdeContent {
      TagListContent(
          workdir = workdir,
          refreshTrigger = refreshTrigger,
          newTagTrigger = newTagTrigger,
          onRefresh = { refreshTrigger.value++ },
          onCreate = ::createTag,
          onDelete = ::deleteTag,
          onPushTag = ::pushTag,
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  /**
   * 创建 tag。支持轻量 tag 和附注 tag。
   * @param tagName tag 名称
   * @param message 附注消息, 为空则创建轻量 tag
   */
  private fun createTag(tagName: String, message: String) {
    if (tagName.isBlank()) return toast("Tag 名不能为空")
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          val headOid = repo.head()?.target()
          if (headOid == null || headOid.isNullOrEmptyOrZero) {
            throw RuntimeException("HEAD 不存在, 无法创建 tag")
          }
          val commit = Commit.lookup(repo, headOid)
              ?: throw RuntimeException("无法查找 HEAD commit")
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
          toast("已创建 tag $tagName")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "创建 tag 失败") }
      }
    }
  }

  /** 推送单个 tag 到 origin remote. */
  private fun pushTag(tagName: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    val context = context ?: return
    GitCredentialManager.ensureConfigured(context) { cfg ->
      viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
          Repository.open(workdir).use { repo ->
            val cred = GitCredentialManager.toHttpCredential(cfg)
            val remoteAndCred = listOf(
                RemoteAndCredentials("origin", fetchCredential = cred, pushCredential = cred),
            )
            val refspecs = listOf("refs/tags/$tagName")
            val failed = Libgit2Helper.pushTags(repo, remoteAndCred, refspecs)
            if (failed.isNotEmpty()) {
              throw RuntimeException(failed.first().exception?.message ?: "推送 tag 失败")
            }
          }
        }
        withContext(Dispatchers.Main) {
          result.onSuccess { toast("已推送 tag $tagName"); refreshTrigger.value++ }
          result.onFailure { toast(it.localizedMessage ?: "推送 tag 失败") }
        }
      }
    }
  }

  private fun deleteTag(tagName: String) {
    val workdir = resolveWorkspaceDirPath() ?: return toast("No opened project")
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
      val result = runCatching {
        Repository.open(workdir).use { repo ->
          Libgit2Helper.delTags(repoId = "", repo = repo, tagShortNames = listOf(tagName))
        }
      }
      withContext(Dispatchers.Main) {
        result.onSuccess {
          toast("已删除 tag $tagName")
          refreshTrigger.value++
        }
        result.onFailure { toast(it.localizedMessage ?: "删除 tag 失败") }
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

private sealed interface TagListUiState {
  data object Loading : TagListUiState
  data object NoProject : TagListUiState
  data class Loaded(val tags: List<TagDto>) : TagListUiState
  data class Error(val message: String) : TagListUiState
}

@Composable
private fun TagListContent(
    workdir: String?,
    refreshTrigger: State<Int>,
    newTagTrigger: State<Int>,
    onRefresh: () -> Unit,
    onCreate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onPushTag: (String) -> Unit,
) {
  val trigger = refreshTrigger.value
  val uiState by produceState<TagListUiState>(TagListUiState.Loading, trigger, workdir) {
    if (workdir == null) {
      value = TagListUiState.NoProject
      return@produceState
    }
    value = TagListUiState.Loading
    value =
        runCatching {
              withContext(Dispatchers.IO) {
                Repository.open(workdir).use { repo ->
                  val settings = SettingsUtil.getSettingsSnapshot()
                  val tags = Libgit2Helper.getAllTags(repoId = "", repo = repo, settings = settings)
                  TagListUiState.Loaded(tags)
                }
              }
            }
            .getOrElse { TagListUiState.Error(it.localizedMessage ?: "加载 tag 列表失败") }
  }

  val nbTrigger = newTagTrigger.value
  var showCreateDialog by remember { mutableStateOf(false) }
  LaunchedEffect(nbTrigger) {
    if (nbTrigger > 0) showCreateDialog = true
  }

  when (val s = uiState) {
    TagListUiState.Loading -> GitLoadingState()
    TagListUiState.NoProject -> GitEmptyState(message = "未打开工程")
    is TagListUiState.Error -> GitErrorState(message = s.message, onRetry = onRefresh)
    is TagListUiState.Loaded -> {
      if (s.tags.isEmpty()) {
        GitEmptyState(message = "暂无 Tag", icon = Icons.Outlined.LocalOffer)
      } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
        ) {
          items(s.tags, key = { it.name }) { tag ->
            TagItem(tag = tag, onDelete = onDelete, onPush = onPushTag)
          }
        }
      }
    }
  }

  if (showCreateDialog) {
    CreateTagDialog(
        onConfirm = { name, msg ->
          showCreateDialog = false
          onCreate(name.trim(), msg.trim())
        },
        onDismiss = { showCreateDialog = false },
    )
  }
}

/** 创建 Tag 对话框: 支持轻量 tag 和附注 tag. */
@Composable
private fun CreateTagDialog(
    onConfirm: (name: String, message: String) -> Unit,
    onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var message by remember { mutableStateOf("") }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("新建 Tag") },
      text = {
        Column {
          OutlinedTextField(
              value = name,
              onValueChange = { name = it },
              label = { Text("Tag 名称") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(8.dp))
          OutlinedTextField(
              value = message,
              onValueChange = { message = it },
              label = { Text("附注消息 (留空创建轻量 tag)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { onConfirm(name, message) }) { Text("创建") }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

@Composable
private fun TagItem(tag: TagDto, onDelete: (String) -> Unit, onPush: (String) -> Unit) {
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
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          androidx.compose.material3.Icon(
              imageVector = Icons.Outlined.LocalOffer,
              contentDescription = null,
              tint = if (tag.isAnnotated) MaterialTheme.colorScheme.primary
                     else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(end = 8.dp).then(Modifier.size(20.dp)),
          )
          Text(
              text = tag.shortName.ifBlank { tag.name },
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
          )
        }
        if (tag.targetFullOidStr.isNotBlank()) {
          Spacer(Modifier.height(4.dp))
          Text(
              text = tag.targetFullOidStr.take(7),
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (tag.isAnnotated && tag.taggerName.isNotBlank()) {
          Text(
              text = "附注 · ${tag.taggerName}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      androidx.compose.material3.IconButton(onClick = { onDelete(tag.shortName) }) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = "删除 Tag",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
      }
      DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text("推送到 origin") },
            onClick = { menuExpanded = false; onPush(tag.shortName) },
        )
        DropdownMenuItem(
            text = { Text("删除") },
            onClick = { menuExpanded = false; onDelete(tag.shortName) },
        )
      }
    }
  }
}

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
