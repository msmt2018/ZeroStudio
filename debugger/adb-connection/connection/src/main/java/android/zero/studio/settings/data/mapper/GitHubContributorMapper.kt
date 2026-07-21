package android.zero.studio.settings.data.mapper

import android.zero.studio.settings.data.dto.GitHubContributorDto
import android.zero.studio.settings.domain.model.GitHubContributor

fun GitHubContributorDto.toGitHubContributor(): GitHubContributor {
    return GitHubContributor(
        name = name,
        username = username,
        profileUrl = profileUrl,
        avatarAssetPath = avatar,
        contributions = contributions
    )
}
