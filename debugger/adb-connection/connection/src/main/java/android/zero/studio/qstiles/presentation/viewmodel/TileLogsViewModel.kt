package android.zero.studio.qstiles.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.zero.studio.qstiles.domain.repository.TileLogRepository
import android.zero.studio.qstiles.presentation.model.TileLogsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TileLogsViewModel @Inject constructor(
    private val logRepository: TileLogRepository
) : ViewModel() {

    val state: StateFlow<TileLogsState> =
        logRepository.getAllLogs()
            .map { logs ->
                val total = logs.size
                val success = logs.count { it.isSuccess }

                TileLogsState(
                    logs = logs,
                    totalExecutions = total,
                    successRate = if (total == 0) 0f else success.toFloat() / total
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                TileLogsState()
            )
}