package com.itsaky.androidide.fragments.git

import android.net.Uri
import com.itsaky.androidide.projects.IProjectManager
import java.io.File

internal data class GitHostLinks(
    val baseHttpUrl: String,
    val pullRequestsUrl: String,
    val pipelinesUrl: String,
    val actionsUrl: String,
    val mergeRequestsUrl: String,
)

internal object GitHostWebLinks {
  fun resolveForCurrentProject(): GitHostLinks? {
    val projectPath = IProjectManager.getInstance().projectDirPath ?: return null
    val configFile = File(projectPath, ".git/config")
    if (!configFile.exists()) return null

    val config = configFile.readText()
    val remoteUrl =
        Regex("""url\s*=\s*(.+)""").find(config)?.groupValues?.getOrNull(1)?.trim() ?: return null

    val base = normalizeRemoteToHttp(remoteUrl) ?: return null
    val uri = Uri.parse(base)
    val host = (uri.host ?: "").lowercase()

    val pullRequestsUrl: String
    val mergeRequestsUrl: String
    val pipelinesUrl: String
    val actionsUrl: String

    when {
      host.contains("gitlab") -> {
        pullRequestsUrl = "$base/-/merge_requests"
        mergeRequestsUrl = "$base/-/merge_requests"
        pipelinesUrl = "$base/-/pipelines"
        actionsUrl = "$base/-/pipelines"
      }
      host.contains("gitee") -> {
        pullRequestsUrl = "$base/pulls"
        mergeRequestsUrl = "$base/pulls"
        pipelinesUrl = "$base/pipelines"
        actionsUrl = "$base/actions"
      }
      else -> {
        // GitHub and other GitHub-compatible hosts
        pullRequestsUrl = "$base/pulls"
        mergeRequestsUrl = "$base/pulls"
        pipelinesUrl = "$base/actions"
        actionsUrl = "$base/actions"
      }
    }

    return GitHostLinks(
        baseHttpUrl = base,
        pullRequestsUrl = pullRequestsUrl,
        pipelinesUrl = pipelinesUrl,
        actionsUrl = actionsUrl,
        mergeRequestsUrl = mergeRequestsUrl,
    )
  }

  private fun normalizeRemoteToHttp(remote: String): String? {
    val cleaned = remote.removeSuffix(".git")
    return when {
      cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
      cleaned.startsWith("git@") -> {
        val body = cleaned.removePrefix("git@")
        val split = body.split(":", limit = 2)
        if (split.size != 2) return null
        "https://${split[0]}/${split[1]}"
      }
      cleaned.startsWith("ssh://") -> {
        val uri = Uri.parse(cleaned)
        val host = uri.host ?: return null
        val path = uri.path?.trimStart('/') ?: return null
        "https://$host/$path"
      }
      else -> null
    }
  }
}
