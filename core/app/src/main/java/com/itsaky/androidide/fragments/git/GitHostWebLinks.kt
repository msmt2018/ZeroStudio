package com.itsaky.androidide.fragments.git

import android.net.Uri
import com.github.git24j.core.Repository
import com.itsaky.androidide.projects.IProjectManager
import java.io.File

internal enum class GitHostKind {
  GITHUB,
  GITLAB,
  GITEE,
  UNKNOWN,
}

internal data class GitHostLinks(
    val hostKind: GitHostKind,
    val remoteName: String,
    val baseHttpUrl: String,
    val pullRequestsUrl: String,
    val pipelinesUrl: String,
    val actionsUrl: String,
    val mergeRequestsUrl: String,
    val issuesUrl: String,
) {
  fun newTaskUrl(title: String, body: String = ""): String {
    val encodedTitle = Uri.encode(title)
    val encodedBody = Uri.encode(body)
    return when (hostKind) {
      GitHostKind.GITLAB -> "$issuesUrl/new?issue[title]=$encodedTitle&issue[description]=$encodedBody"
      else -> "$issuesUrl/new?title=$encodedTitle&body=$encodedBody"
    }
  }

  /** 返回一个可在浏览器触发 workflow/pipeline 的入口页面。 */
  fun workflowRunUrl(yamlFile: String, ref: String): String {
    val safeRef = Uri.encode(ref.ifBlank { "main" })
    val safeYaml = Uri.encode(yamlFile)
    return when (hostKind) {
      GitHostKind.GITHUB -> "$baseHttpUrl/actions/workflows/$safeYaml?query=branch%3A$safeRef"
      GitHostKind.GITLAB -> "$baseHttpUrl/-/pipelines/new?ref=$safeRef"
      GitHostKind.GITEE -> "$baseHttpUrl/actions"
      GitHostKind.UNKNOWN -> actionsUrl
    }
  }
}

internal object GitHostWebLinks {

  fun resolveForCurrentProject(): GitHostLinks? {
    val projectPath = IProjectManager.getInstance().projectDirPath ?: return null
    val configFile = File(projectPath, ".git/config")
    if (!configFile.exists()) return null

    val configText = configFile.readText()
    val remote = resolvePreferredRemote(configText) ?: return null
    val base = normalizeRemoteToHttp(remote.url) ?: return null

    val host = (Uri.parse(base).host ?: "").lowercase()
    val kind = resolveHostKind(host)

    val pullRequestsUrl: String
    val mergeRequestsUrl: String
    val pipelinesUrl: String
    val actionsUrl: String
    val issuesUrl: String

    when (kind) {
      GitHostKind.GITLAB -> {
        pullRequestsUrl = "$base/-/merge_requests"
        mergeRequestsUrl = "$base/-/merge_requests"
        pipelinesUrl = "$base/-/pipelines"
        actionsUrl = "$base/-/pipelines"
        issuesUrl = "$base/-/issues"
      }
      GitHostKind.GITEE -> {
        pullRequestsUrl = "$base/pulls"
        mergeRequestsUrl = "$base/pulls"
        pipelinesUrl = "$base/pipelines"
        actionsUrl = "$base/actions"
        issuesUrl = "$base/issues"
      }
      else -> {
        pullRequestsUrl = "$base/pulls"
        mergeRequestsUrl = "$base/pulls"
        pipelinesUrl = "$base/actions"
        actionsUrl = "$base/actions"
        issuesUrl = "$base/issues"
      }
    }

    return GitHostLinks(
        hostKind = kind,
        remoteName = remote.name,
        baseHttpUrl = base,
        pullRequestsUrl = pullRequestsUrl,
        pipelinesUrl = pipelinesUrl,
        actionsUrl = actionsUrl,
        mergeRequestsUrl = mergeRequestsUrl,
        issuesUrl = issuesUrl,
    )
  }

  fun getCurrentBranchName(): String {
    val projectPath = IProjectManager.getInstance().projectDirPath ?: return "main"
    return runCatching {
          Repository.open(projectPath).use { repo ->
            if (repo.headDetached()) "HEAD" else repo.head()?.shorthand().orEmpty()
          }
        }
        .getOrDefault("main")
        .ifBlank { "main" }
  }

  private fun resolvePreferredRemote(configText: String): GitRemoteConfig? {
    val remoteBlocks =
        Regex("""\[remote\s+\"([^\"]+)\"\]([\s\S]*?)(?=\n\[|$)""")
            .findAll(configText)
            .mapNotNull { m ->
              val name = m.groupValues[1]
              val body = m.groupValues[2]
              val url = Regex("""\n\s*url\s*=\s*(.+)""").find("\n$body")?.groupValues?.getOrNull(1)?.trim()
              if (url.isNullOrBlank()) null else GitRemoteConfig(name, url)
            }
            .toList()

    if (remoteBlocks.isEmpty()) return null

    val branchRemote =
        Regex("""\[branch\s+\"[^\"]+\"\]([\s\S]*?)(?=\n\[|$)""")
            .findAll(configText)
            .mapNotNull { block ->
              Regex("""\n\s*remote\s*=\s*(.+)""")
                  .find("\n${block.groupValues[1]}")
                  ?.groupValues
                  ?.getOrNull(1)
                  ?.trim()
            }
            .firstOrNull()

    val preferredName = when {
      !branchRemote.isNullOrBlank() -> branchRemote
      remoteBlocks.any { it.name == "origin" } -> "origin"
      else -> remoteBlocks.first().name
    }

    return remoteBlocks.firstOrNull { it.name == preferredName } ?: remoteBlocks.firstOrNull()
  }

  private data class GitRemoteConfig(val name: String, val url: String)

  private fun resolveHostKind(host: String): GitHostKind {
    return when {
      host.contains("gitlab") -> GitHostKind.GITLAB
      host.contains("gitee") -> GitHostKind.GITEE
      host.contains("github") -> GitHostKind.GITHUB
      else -> GitHostKind.UNKNOWN
    }
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
