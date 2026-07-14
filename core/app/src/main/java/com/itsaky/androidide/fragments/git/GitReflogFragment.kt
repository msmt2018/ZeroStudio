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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catpuppyapp.puppygit.git.ReflogEntryDto
import com.catpuppyapp.puppygit.settings.SettingsUtil
import com.catpuppyapp.puppygit.utils.Libgit2Helper
import com.github.git24j.core.Repository
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reflog (操作日志) 页面 —— 独立设计的 Compose UI。
 *
 * 显示 HEAD 的 reflog 记录, 让用户查看最近的 git 操作历史
 * (commit / checkout / pull / reset 等)。
 *
 * git core 调用一比一复刻 puppygit:
 * - [Libgit2Helper.getReflogList] 加载 reflog
 *
 * @author android_zero
 */
class GitReflogFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitComposeBinding? = null
  private val binding
    get() = _binding!!

  private val refreshTrigger = mutableStateOf(0)

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
      emitGitOperation("reflog", "refresh")
      refreshTrigger.value++
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val workdir = resolveWorkspaceDirPath()
    val compose = setIdeContent {
      ReflogContent(
          workdir = workdir,
          refreshTrigger = refreshTrigger,
          onRefresh = { refreshTrigger.value++ },
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  override fun onDestroyView() {
    binding.gitContentContainer.removeAllViews()
    super.onDestroyView()
    _binding = null
  }
}

// ---- Compose UI ----

private sealed interface ReflogUiState {
  data object Loading : ReflogUiState
  data object NoProject : ReflogUiState
  data class Loaded(val entries: List<ReflogEntryDto>) : ReflogUiState
  data class Error(val message: String) : ReflogUiState
}

@Composable
private fun ReflogContent(
    workdir: String?,
    refreshTrigger: State<Int>,
    onRefresh: () -> Unit,
) {
  val trigger = refreshTrigger.value
  val uiState by produceState<ReflogUiState>(ReflogUiState.Loading, trigger, workdir) {
    if (workdir == null) {
      value = ReflogUiState.NoProject
      return@produceState
    }
    value = ReflogUiState.Loading
    value =
        runCatching {
              withContext(Dispatchers.IO) {
                Repository.open(workdir).use { repo ->
                  val settings = SettingsUtil.getSettingsSnapshot()
                  val entries = mutableListOf<ReflogEntryDto>()
                  Libgit2Helper.getReflogList(repo, "HEAD", entries, settings)
                  ReflogUiState.Loaded(entries)
                }
              }
            }
            .getOrElse { ReflogUiState.Error(it.localizedMessage ?: "加载 reflog 失败") }
  }

  when (val s = uiState) {
    ReflogUiState.Loading -> GitLoadingState()
    ReflogUiState.NoProject -> GitEmptyState(message = "未打开工程")
    is ReflogUiState.Error -> GitErrorState(message = s.message, onRetry = onRefresh)
    is ReflogUiState.Loaded -> {
      if (s.entries.isEmpty()) {
        GitEmptyState(message = "暂无操作日志", icon = Icons.Outlined.History)
      } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(GitSpacing.itemSpacing),
        ) {
          items(s.entries.size) { index ->
            ReflogItem(entry = s.entries[index])
          }
        }
      }
    }
  }
}

@Composable
private fun ReflogItem(entry: ReflogEntryDto) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(GitSpacing.cardCorner),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(GitSpacing.cardPadding),
        verticalAlignment = Alignment.Top,
    ) {
      // 新 commit 短 hash
      entry.idNew?.toString()?.take(7)?.let { hash ->
        Text(
            text = hash,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = entry.msg.ifBlank { "(no message)" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row {
          if (entry.username.isNotBlank()) {
            Text(
                text = entry.username,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
          }
          if (entry.date.isNotBlank()) {
            Text(
                text = entry.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
