package com.itsaky.androidide.lsp.manager.window

import java.util.concurrent.CopyOnWriteArrayList

/** Minimal window/progress/message hub mirroring LSP window notifications. */
class LspWindowEventBus {
  private val listeners = CopyOnWriteArrayList<(LspWindowEvent) -> Unit>()

  fun subscribe(listener: (LspWindowEvent) -> Unit): AutoCloseable {
    listeners += listener
    return AutoCloseable { listeners -= listener }
  }

  fun publish(event: LspWindowEvent) {
    listeners.forEach { it(event) }
  }
}

sealed interface LspWindowEvent {
  data class Message(val level: Level, val message: String) : LspWindowEvent
  data class Progress(val token: String, val title: String, val message: String?, val percentage: Int?) : LspWindowEvent
  data class DiagnosticsChanged(val uri: String, val count: Int) : LspWindowEvent

  enum class Level { Error, Warning, Info, Log, Debug }
}
