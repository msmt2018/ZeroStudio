package android.zero.studio.core.domain.model

data class GithubRelease(
    val tagName: String,
    val apkUrl: String? = null,
    val body: String? = null
)
