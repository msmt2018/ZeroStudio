/*
 *  This file is part of AndroidIDE.
 */
package com.itsaky.androidide.fragments.git

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.itsaky.androidide.R

/** 代码审查页面。 */
class GitCodeReviewFragment : BaseGitPageFragment() {

  private var links: GitHostLinks? = null

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    links = GitHostWebLinks.resolveForCurrentProject()
    return inflater.inflate(R.layout.fragment_git_branches, container, false)
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_check_24, "Open Review Page") {
      emitGitOperation("code_review", "open_review_page")
      openIfAny(links?.pullRequestsUrl ?: links?.mergeRequestsUrl)
    }

    addToolbarAction(R.drawable.ic_info_24, "Open Diffs") {
      emitGitOperation("code_review", "open_diffs_page")
      openIfAny(links?.pullRequestsUrl ?: links?.mergeRequestsUrl)
    }

    addToolbarAction(R.drawable.ic_add_24, "New Review Task") {
      emitGitOperation("code_review", "new_review_task")
      val task = links?.newTaskUrl("Code Review Task", "Created from AndroidIDE code review page")
      openIfAny(task)
    }
  }

  private fun openIfAny(url: String?) {
    if (url == null) {
      Toast.makeText(context, "No remote repository detected", Toast.LENGTH_SHORT).show()
      return
    }
    openExternalLink(url)
  }
}
