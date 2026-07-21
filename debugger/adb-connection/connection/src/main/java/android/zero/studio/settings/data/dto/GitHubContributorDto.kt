package android.zero.studio.settings.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class GitHubContributorDto(
    val name: String,
    val username: String,
    val profileUrl: String,
    val avatar: String,
    val contributions: Int
)
