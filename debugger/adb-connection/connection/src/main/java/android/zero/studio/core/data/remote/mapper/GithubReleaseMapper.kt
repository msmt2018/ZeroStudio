package android.zero.studio.core.data.remote.mapper

import android.zero.studio.BuildConfig
import android.zero.studio.core.data.remote.dto.GithubReleaseDto
import android.zero.studio.core.domain.model.GithubRelease
import android.zero.studio.core.domain.model.GithubReleaseType

fun GithubReleaseDto.toDomain(releaseType: Int): GithubRelease {
    val flavor = if (releaseType == GithubReleaseType.STABLE_FDROID) {
        BuildConfig.DIST_FLAVOR_FDROID
    } else {
        BuildConfig.DIST_FLAVOR_GITHUB
    }
    val apkAsset = assets.firstOrNull {
        it.name.contains(flavor, ignoreCase = true) && it.name.endsWith(".apk")
    }

    return GithubRelease(
        tagName = tagName,
        apkUrl = apkAsset?.browserDownloadUrl,
        body = body
    )
}
