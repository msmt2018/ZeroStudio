package com.itsaky.androidide.fragments.git

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal sealed class GitUiEvent {
  data class Operation(val section: String, val action: String) : GitUiEvent()
  data class Error(val message: String) : GitUiEvent()
}

internal class GitUiEventViewModel : ViewModel() {
  private val _events = MutableSharedFlow<GitUiEvent>(extraBufferCapacity = 32)
  val events: SharedFlow<GitUiEvent> = _events.asSharedFlow()

  fun emit(event: GitUiEvent) {
    _events.tryEmit(event)
  }
}

