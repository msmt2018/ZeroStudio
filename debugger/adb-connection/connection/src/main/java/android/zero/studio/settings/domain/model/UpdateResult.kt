package android.zero.studio.settings.domain.model

import android.zero.studio.core.domain.model.GithubRelease

sealed class UpdateResult {
    data class Success(val release: GithubRelease, val isUpdateAvailable: Boolean) : UpdateResult()
    object NetworkError : UpdateResult()
    object Timeout : UpdateResult()
    object UnknownError : UpdateResult()
}
