package android.zero.studio.qstiles.domain.executor

import android.zero.studio.qstiles.domain.model.TileErrorType

data class CommandResult(
    val output: String,
    val isSuccess: Boolean,
    val errorType: TileErrorType,
    val durationMs: Long
)
