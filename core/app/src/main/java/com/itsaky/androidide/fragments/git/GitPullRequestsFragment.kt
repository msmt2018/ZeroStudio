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
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.itsaky.androidide.R

/** Pull Requests 列表页面。 */
class GitPullRequestsFragment : BaseGitPageFragment() {

  private var links: GitHostLinks? = null

  // 这里复用一个简单的 RecyclerView 布局，实际开发建议创建 fragment_git_pull_requests.xml
  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    // 暂时复用通用的列表布局骨架
    return inflater.inflate(R.layout.fragment_git_branches, container, false)
  }

  override fun setupToolbar() {
    addToolbarAction(R.drawable.ic_add_24, "Open Pull Requests") {
      emitGitOperation("pull_requests", "open_pr_page")
      openPullRequestsPage()
    }
    addToolbarAction(R.drawable.ic_filter_list_24, "Open Merge Requests") {
      emitGitOperation("pull_requests", "open_mr_page")
      openMergeRequestsPage()
    }
    addToolbarAction(R.drawable.ic_refresh_24, "Refresh") {
      links = GitHostWebLinks.resolveForCurrentProject()
      emitGitOperation("pull_requests", "refresh_remote_links")
    }
    addToolbarAction(R.drawable.ic_check_24, "New Task") {
      emitGitOperation("pull_requests", "create_issue")
      openNewTaskPage()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // 查找 RecyclerView (ID 需与布局一致，这里假设复用了 fragment_git_branches 的 rv_branches)
    links = GitHostWebLinks.resolveForCurrentProject()
    view.findViewById<RecyclerView>(R.id.rv_branches)?.apply {
      layoutManager = LinearLayoutManager(context)
      adapter = QuickLinksAdapter(listOf("Open Pull Requests in Browser", "Create Task (Issue)", "Open Merge Requests"))
    }
  }

  private fun openPullRequestsPage() {
    val target = links?.pullRequestsUrl ?: links?.mergeRequestsUrl
    if (target == null) {
      Toast.makeText(context, "No remote repository detected", Toast.LENGTH_SHORT).show()
      return
    }
    openExternalLink(target)
  }

  private fun openNewTaskPage() {
    val target = links?.newTaskUrl(title = "Code review task", body = "Created from AndroidIDE")
    if (target == null) {
      Toast.makeText(context, "No remote repository detected", Toast.LENGTH_SHORT).show()
      return
    }
    openExternalLink(target)
  }

  private fun openMergeRequestsPage() {
    val target = links?.mergeRequestsUrl ?: links?.pullRequestsUrl
    if (target == null) {
      Toast.makeText(context, "No remote repository detected", Toast.LENGTH_SHORT).show()
      return
    }
    openExternalLink(target)
  }

  private inner class QuickLinksAdapter(private val items: List<String>) :
      RecyclerView.Adapter<QuickLinksAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
      val view =
          LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
      return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
      holder.text.text = items[position]
      holder.itemView.setOnClickListener {
        when (position) {
          0 -> openPullRequestsPage()
          1 -> openNewTaskPage()
          else -> openMergeRequestsPage()
        }
      }
    }

    override fun getItemCount() = items.size

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
      val text: TextView = view.findViewById(android.R.id.text1)
    }
  }
}
