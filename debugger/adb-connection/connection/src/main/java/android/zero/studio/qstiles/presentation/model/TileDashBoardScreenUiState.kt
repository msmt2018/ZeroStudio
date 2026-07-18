package android.zero.studio.qstiles.presentation.model

import androidx.compose.runtime.Immutable
import android.zero.studio.qstiles.domain.model.RunningTileState
import android.zero.studio.qstiles.domain.model.TileConfig
import android.zero.studio.qstiles.domain.model.TileLog
import android.zero.studio.qstiles.presentation.screen.TileScreenTabs

@Immutable
data class TileDashBoardScreenUiState(
    val tiles: List<TileConfig> = emptyList(),
    val activeCount: Int = 0,
    val runningTiles: Map<Int, RunningTileState> = emptyMap(),
    val currentTab: Int = TileScreenTabs.TILES,
    val logs: List<TileLog> = emptyList(),
    val totalExecutions: Int = 0,
    val successRate: String = "0.0%",
    val selectedTileIdFilter: Int? = null,
    val tilesWithLogs: List<TileConfig> = emptyList()
)