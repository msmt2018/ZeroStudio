package android.zero.studio.qstiles.domain.repository

import android.zero.studio.qstiles.domain.model.TileLog
import kotlinx.coroutines.flow.Flow

interface TileLogRepository {
    suspend fun insert(log: TileLog)
    fun getLogs(tileId: Int): Flow<List<TileLog>>
    fun getAllLogs(): Flow<List<TileLog>>
    fun getTotalExecutions(): Flow<Int>
    fun getSuccessCount(): Flow<Int>
}