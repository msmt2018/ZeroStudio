package android.zero.studio.qstiles.data.repository

import android.zero.studio.qstiles.data.dao.TileLogDao
import android.zero.studio.qstiles.data.mapper.toDomain
import android.zero.studio.qstiles.data.model.TileLogEntity
import android.zero.studio.qstiles.domain.model.TileLog
import android.zero.studio.qstiles.domain.repository.TileLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TileLogRepositoryImpl @Inject constructor(
    private val dao: TileLogDao
) : TileLogRepository {

    override suspend fun insert(log: TileLog) {
        dao.insert(
            TileLogEntity(
                tileId = log.tileId,
                command = log.command,
                output = log.output,
                isSuccess = log.isSuccess,
                executionMode = log.executionMode,
                timestamp = log.timestamp,
                durationMs = log.durationMs,
                errorType = log.errorType
            )
        )
    }

    override fun getLogs(tileId: Int): Flow<List<TileLog>> {
        return dao.getLogsForTile(tileId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getAllLogs(): Flow<List<TileLog>> {
        return dao.getAllLogs().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getTotalExecutions() = dao.getTotalExecutions()

    override fun getSuccessCount() = dao.getSuccessCount()
}