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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitComposeBinding

/**
 * Pull Requests 列表页面 —— 独立设计的 Compose UI (web 链接到 GitHub/GitLab/Gitee)。
 *
 * UI 结构:
 * - 无远程仓库: 空状态提示
 * - 有远程仓库: 远程仓库信息卡片 + PR / MR / Issue 操作卡片列表
 *
 * 交互:
 * - 点击操作卡片 -> 在浏览器打开对应 web 链接
 * - 顶部 mini-toolbar: 快捷打开 PR / MR / 新建 Issue / 刷新
 *
 * @author android_zero
 */
class GitPullRequestsFragment : BaseGitPageFragment() {

  private var _binding: FragmentGitComposeBinding? = null
  private val binding
    get() = _binding!!

  /** web 链接状态, Fragment 字段持有, toolbar 和 Compose 共享. */
  private val linksState = mutableStateOf<GitHostLinks?>(null)

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    _binding = FragmentGitComposeBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_add_24, "Open Pull Requests") {
      emitGitOperation("pull_requests", "open_pr_page")
      openWebLinkOrToast(
        linksState.value?.pullRequestsUrl ?: linksState.value?.mergeRequestsUrl,
        "No remote repository detected",
      )
    }

    addToolbarAction(R.drawable.ic_filter_list_24, "Open Merge Requests") {
      emitGitOperation("pull_requests", "open_mr_page")
      openWebLinkOrToast(
        linksState.value?.mergeRequestsUrl ?: linksState.value?.pullRequestsUrl,
        "No remote repository detected",
      )
    }

    addToolbarAction(R.drawable.ic_check_24, "New Task (Issue)") {
      emitGitOperation("pull_requests", "create_issue")
      val target =
          linksState.value?.newTaskUrl(title = "Code review task", body = "Created from AndroidIDE")
      openWebLinkOrToast(target, "No remote repository detected")
    }

    addToolbarAction(R.drawable.ic_refresh_24, "Refresh") {
      refreshLinks()
      emitGitOperation("pull_requests", "refresh_remote_links")
      val msg = if (linksState.value != null) "Remote links refreshed" else "No remote repository detected"
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    refreshLinks()
    val compose = setIdeContent {
      PullRequestsContent(
          linksState = linksState,
          buildActions = ::buildActions,
      )
    }
    binding.gitContentContainer.addView(compose)
  }

  private fun refreshLinks() {
    linksState.value = GitHostWebLinks.resolveForCurrentProject()
  }

  /** 根据 links 构造操作卡片列表; links 为 null 时返回空列表 (由 GitWebLinkPage 显示空状态). */
  private fun buildActions(links: GitHostLinks?): List<GitWebLinkAction> {
    if (links == null) return emptyList()
    return listOf(
        GitWebLinkAction(
            icon = Icons.Outlined.FolderOpen,
            title = "Pull Requests",
            description = "在浏览器打开 GitHub / Gitee 的 PR 列表",
            onClick = {
              emitGitOperation("pull_requests", "open_pr_page")
              openWebLinkOrToast(
                links.pullRequestsUrl ?: links.mergeRequestsUrl,
                "No remote repository detected",
              )
            },
        ),
        GitWebLinkAction(
            icon = Icons.Outlined.CallMerge,
            title = "Merge Requests",
            description = "在浏览器打开 GitLab 的 MR 列表",
            onClick = {
              emitGitOperation("pull_requests", "open_mr_page")
              openWebLinkOrToast(
                links.mergeRequestsUrl ?: links.pullRequestsUrl,
                "No remote repository detected",
              )
            },
        ),
        GitWebLinkAction(
            icon = Icons.Outlined.AddCircle,
            title = "新建 Issue",
            description = "在浏览器创建新的 Issue / 任务",
            onClick = {
              emitGitOperation("pull_requests", "create_issue")
              openWebLinkOrToast(
                links.newTaskUrl("Code review task", "Created from AndroidIDE"),
                "No remote repository detected",
              )
            },
        ),
    )
  }

  override fun onDestroyView() {
    binding.gitContentContainer.removeAllViews()
    super.onDestroyView()
    _binding = null
  }
}

/** PR 页面 Compose 内容: 观察 linksState 变化, 显示操作卡片列表. */
@Composable
private fun PullRequestsContent(
    linksState: State<GitHostLinks?>,
    buildActions: (GitHostLinks?) -> List<GitWebLinkAction>,
) {
  val links = linksState.value
  val actions = remember(links) { buildActions(links) }
  GitWebLinkPage(
      pageTitle = "Pull Requests",
      pageDescription = "管理代码合并请求与 Issue",
      links = links,
      actions = actions,
  )
}
