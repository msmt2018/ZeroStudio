package android.zero.studio.core.domain.repository

import android.zero.studio.core.domain.model.GithubRepoStats
import android.zero.studio.settings.domain.model.UpdateResult
import kotlinx.coroutines.flow.Flow

interface GithubDataRepository {
    suspend fun fetchLatestRelease(includePrerelease: Boolean, releaseType: Int): UpdateResult
    fun observeRepoStats(): Flow<GithubRepoStats>
    suspend fun refreshRepoStats()
}