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
import com.itsaky.androidide.R

/** CD/CI 流水线状态页面。 */
class GitPipelinesFragment : BaseGitPageFragment() {

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
    addToolbarAction(R.drawable.ic_refresh_24, "Refresh Pipelines") {
      links = GitHostWebLinks.resolveForCurrentProject()
      emitGitOperation("pipelines", "refresh_remote_links")
      Toast.makeText(context, "Pipeline links refreshed", Toast.LENGTH_SHORT).show()
    }

    addToolbarAction(R.drawable.ic_info_24, "Open Pipelines") {
      emitGitOperation("pipelines", "open_pipelines")
      val url = links?.pipelinesUrl
      if (url == null) {
        Toast.makeText(context, "No remote repository detected", Toast.LENGTH_SHORT).show()
      } else {
        openExternalLink(url)
      }
    }

    addToolbarAction(R.drawable.ic_check_24, "Open Actions") {
      emitGitOperation("pipelines", "open_actions")
      val url = links?.actionsUrl
      if (url == null) {
        Toast.makeText(context, "No workflow URL detected", Toast.LENGTH_SHORT).show()
      } else {
        openExternalLink(url)
      }
    }
  }
}
