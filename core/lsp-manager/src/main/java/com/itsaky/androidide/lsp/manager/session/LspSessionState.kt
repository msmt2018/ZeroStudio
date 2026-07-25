package com.itsaky.androidide.lsp.manager.session

/** Lifecycle states for detachable LSP sessions. */
enum class LspSessionState {
  Created,
  Starting,
  Initializing,
  Running,
  Stopping,
  Stopped,
  Failed,
}
