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
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.FragmentGitComposeBinding

/**
 * CI/CD 流水线页面 —— 独立设计的 Compose UI (web 链接到 GitHub Actions / GitLab Pipelines / Gitee)。
 *
 * UI 结构:
 * - 无远程仓库: 空状态提示
 * - 有远程仓库: 远程仓库信息卡片 + Pipelines / Actions / Run Workflow 操作卡片列表
 *
 * 交互:
 * - 点击操作卡片 -> 在浏览器打开对应 web 链接
 * - 顶部 mini-toolbar: 快捷打开 Pipelines / Actions / Run Workflow / 刷新
 *
 * @author android_zero
 */
class GitPipelinesFragment : BaseGitPageFragment() {

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
    addToolbarAction(R.drawable.ic_info_24, "Open Pipelines") {
      emitGitOperation("pipelines", "open_pipelines")
      openWebLinkOrToast(linksState.value?.pipelinesUrl, "No remote repository detected")
    }

    addToolbarAction(R.drawable.ic_check_24, "Open Actions") {
      emitGitOperation("pipelines", "open_actions")
      openWebLinkOrToast(linksState.value?.actionsUrl, "No workflow URL detected")
    }

    addToolbarAction(R.drawable.ic_add_24, "Run Workflow on Branch") {
      emitGitOperation("pipelines", "run_workflow")
      val target = linksState.value
      if (target == null) {
        Toast.makeText(context, "No remote repository detected", Toast.LENGTH_SHORT).show()
        return@addToolbarAction
      }
      val ref = GitHostWebLinks.getCurrentBranchName()
      openWebLinkOrToast(target.workflowRunUrl(yamlFile = "ci.yml", ref = ref))
    }

    addToolbarAction(R.drawable.ic_refresh_24, "Refresh") {
      refreshLinks()
      emitGitOperation("pipelines", "refresh_remote_links")
      val msg = if (linksState.value != null) "Pipeline links refreshed" else "No remote repository detected"
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    refreshLinks()
    val compose = setIdeContent {
      PipelinesContent(
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
            icon = Icons.Outlined.Timeline,
            title = "Pipelines",
            description = "查看 GitLab / Gitee 的流水线运行历史",
            onClick = {
              emitGitOperation("pipelines", "open_pipelines")
              openWebLinkOrToast(links.pipelinesUrl, "No remote repository detected")
            },
        ),
        GitWebLinkAction(
            icon = Icons.Outlined.PlayCircle,
            title = "Actions",
            description = "查看 GitHub Actions 工作流状态",
            onClick = {
              emitGitOperation("pipelines", "open_actions")
              openWebLinkOrToast(links.actionsUrl, "No workflow URL detected")
            },
        ),
        GitWebLinkAction(
            icon = Icons.Outlined.RocketLaunch,
            title = "运行 Workflow",
            description = "在当前分支触发 CI/CD 工作流",
            onClick = {
              emitGitOperation("pipelines", "run_workflow")
              val ref = GitHostWebLinks.getCurrentBranchName()
              openWebLinkOrToast(links.workflowRunUrl(yamlFile = "ci.yml", ref = ref))
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

/** Pipelines 页面 Compose 内容: 观察 linksState 变化, 显示操作卡片列表. */
@Composable
private fun PipelinesContent(
    linksState: State<GitHostLinks?>,
    buildActions: (GitHostLinks?) -> List<GitWebLinkAction>,
) {
  val links = linksState.value
  val actions = remember(links) { buildActions(links) }
  GitWebLinkPage(
      pageTitle = "CI / CD Pipelines",
      pageDescription = "查看与触发持续集成 / 部署流水线",
      links = links,
      actions = actions,
  )
}
