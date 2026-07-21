package android.zero.studio.core.data.local.mapper

import android.zero.studio.core.data.local.model.GithubRepoStatsEntity
import android.zero.studio.core.domain.model.GithubRepoStats


fun GithubRepoStatsEntity.toDomain(): GithubRepoStats {
    return GithubRepoStats(
        stars = stars,
        forks = forks,
        openIssues = issues,
        totalDownloadCount = downloads,
        license = license,
        latestVersion = latestVersion
    )
}

fun GithubRepoStats.toEntity(repo: String): GithubRepoStatsEntity {
    return GithubRepoStatsEntity(
        repo = repo,
        stars = stars,
        forks = forks,
        issues = openIssues,
        downloads = totalDownloadCount,
        license = license,
        latestVersion = latestVersion,
        lastUpdated = System.currentTimeMillis()
    )
}
