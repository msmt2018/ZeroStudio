package android.zero.studio.shell.fastboot.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FastbootConnection {
    private val _state = MutableStateFlow<FastbootState>(FastbootState.Idle)
    val state = _state.asStateFlow()

    fun updateState(newState: FastbootState) {
        _state.value = newState
    }

    val currentState: FastbootState get() = _state.value
}
