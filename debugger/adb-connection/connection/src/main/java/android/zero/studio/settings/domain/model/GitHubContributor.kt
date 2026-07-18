package android.zero.studio.settings.domain.model

data class GitHubContributor(
    val name: String,
    val username: String,
    val profileUrl: String,
    val avatarAssetPath: String,
    val contributions: Int
)
