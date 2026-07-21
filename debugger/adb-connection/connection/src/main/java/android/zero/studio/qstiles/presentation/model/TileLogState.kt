package android.zero.studio.qstiles.presentation.model

import android.zero.studio.qstiles.domain.model.TileLog

data class TileLogsState(
    val logs: List<TileLog> = emptyList(),
    val totalExecutions: Int = 0,
    val successRate: Float = 0f
)