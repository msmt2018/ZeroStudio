/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.fragments.git

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitComposeBinding

/**
 * 代码审查页面 —— 独立设计的 Compose UI (web 链接到 GitHub/GitLab/Gitee 的 review/diff 页面)。
 *
 * UI 结构:
 * - 无远程仓库: 空状态提示
 * - 有远程仓库: 远程仓库信息卡片 + Review Page / Diffs / New Review Task 操作卡片列表
 *
 * 交互:
 * - 点击操作卡片 -> 在浏览器打开对应 web 链接
 * - 顶部 mini-toolbar: 快捷打开 Review Page / Diffs / New Review Task / 刷新
 *
 * @author android_zero
 */
class GitCodeReviewFragment : BaseGitPageFragment() {

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
    addToolbarAction(R.drawable.ic_check_24, "Open Review Page") {
      emitGitOperation("code_review", "open_review_page")
      openWebLinkOrToast(
        linksState.value?.pullRequestsUrl ?: linksState.value?.mergeRequestsUrl,
        "No remote repository detected",
      )
    }

    addToolbarAction(R.drawable.ic_info_24, "Open Diffs") {
      emitGitOperation("code_review", "open_diffs_page")
      openWebLinkOrToast(
        linksState.value?.pullRequestsUrl ?: linksState.value?.mergeRequestsUrl,
        "No remote repository detected",
      )
    }

    addToolbarAction(R.drawable.ic_add_24, "New Review Task") {
      emitGitOperation("code_review", "new_review_task")
      openWebLinkOrToast(
        linksState.value?.newTaskUrl("Code Review Task", "Created from AndroidIDE code review page"),
        "No remote repository detected",
      )
    }

    addToolbarAction(R.drawable.ic_refresh_24, "Refresh") {
      refreshLinks()
      emitGitOperation("code_review", "refresh_remote_links")
      val msg = if (linksState.value != null) "Review links refreshed" else "No remote repository detected"
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    refreshLinks()
    val compose = setIdeContent {
      CodeReviewContent(
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
            icon = Icons.Outlined.RateReview,
            title = "审查页面",
            description = "在浏览器打开 PR / MR 审查页面",
            onClick = {
              emitGitOperation("code_review", "open_review_page")
              openWebLinkOrToast(
                links.pullRequestsUrl ?: links.mergeRequestsUrl,
                "No remote repository detected",
              )
            },
        ),
        GitWebLinkAction(
            icon = Icons.Outlined.Difference,
            title = "查看 Diff",
            description = "在浏览器查看代码差异对比",
            onClick = {
              emitGitOperation("code_review", "open_diffs_page")
              openWebLinkOrToast(
                links.pullRequestsUrl ?: links.mergeRequestsUrl,
                "No remote repository detected",
              )
            },
        ),
        GitWebLinkAction(
            icon = Icons.Outlined.AddTask,
            title = "新建审查任务",
            description = "在浏览器创建新的代码审查 Issue",
            onClick = {
              emitGitOperation("code_review", "new_review_task")
              openWebLinkOrToast(
                links.newTaskUrl("Code Review Task", "Created from AndroidIDE code review page"),
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

/** 代码审查页面 Compose 内容: 观察 linksState 变化, 显示操作卡片列表. */
@Composable
private fun CodeReviewContent(
    linksState: State<GitHostLinks?>,
    buildActions: (GitHostLinks?) -> List<GitWebLinkAction>,
) {
  val links = linksState.value
  val actions = remember(links) { buildActions(links) }
  GitWebLinkPage(
      pageTitle = "Code Review",
      pageDescription = "代码审查与差异对比",
      links = links,
      actions = actions,
  )
}
