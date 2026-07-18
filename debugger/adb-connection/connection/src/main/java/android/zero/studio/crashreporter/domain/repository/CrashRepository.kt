package android.zero.studio.crashreporter.domain.repository

import android.zero.studio.crashreporter.domain.model.CrashReport
import kotlinx.coroutines.flow.Flow

interface CrashRepository {
    suspend fun addCrash(crash: CrashReport)
    suspend fun getLatestCrash(): CrashReport?
    fun getAllCrashes(): Flow<List<CrashReport>>
    suspend fun clearAllCrashes()
}
