package android.zero.studio.core.domain.repository

import android.zero.studio.core.domain.model.DownloadState

interface DownloadRepository {
    suspend fun downloadApk(
        url: String,
        fileName: String,
        onProgress: (DownloadState) -> Unit
    )

    fun cancelDownload()
}
