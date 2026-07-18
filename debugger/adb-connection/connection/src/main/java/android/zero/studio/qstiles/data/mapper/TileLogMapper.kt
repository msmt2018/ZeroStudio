package android.zero.studio.qstiles.data.mapper

import android.zero.studio.qstiles.data.model.TileLogEntity
import android.zero.studio.qstiles.domain.model.TileLog

fun TileLogEntity.toDomain(): TileLog {
    return TileLog(
        id = id,
        tileId = tileId,
        command = command,
        output = output,
        isSuccess = isSuccess,
        executionMode = executionMode,
        timestamp = timestamp,
        durationMs = durationMs,
        errorType = errorType
    )
}