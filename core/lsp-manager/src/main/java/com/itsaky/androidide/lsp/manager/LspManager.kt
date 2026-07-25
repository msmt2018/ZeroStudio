package com.itsaky.androidide.lsp.manager

import com.itsaky.androidide.lsp.manager.registry.LspServerRegistry
import com.itsaky.androidide.lsp.manager.server.LspServerDescriptor
import com.itsaky.androidide.lsp.manager.session.LspSessionManager
import com.itsaky.androidide.lsp.manager.ui.LspUiContribution
import com.itsaky.androidide.lsp.manager.ui.LspUiRegistry
import com.itsaky.androidide.lsp.manager.window.LspWindowEventBus

/**
 * Public facade for applications embedding the ZeroStudio LSP stack.
 *
 * Keep editor widgets, protocol handlers, and language server providers detachable by registering
 * them through this facade before binding individual Sora editors.
 */
class LspManager private constructor(
  val sessions: LspSessionManager,
  val servers: LspServerRegistry,
  val ui: LspUiRegistry,
  val windows: LspWindowEventBus,
) : AutoCloseable {

  fun registerServer(descriptor: LspServerDescriptor): LspManager = apply {
    servers.register(descriptor)
  }

  fun registerUi(contribution: LspUiContribution): LspManager = apply {
    ui.register(contribution)
  }

  override fun close() {
    sessions.close()
  }

  companion object {
    fun create(block: Builder.() -> Unit = {}): LspManager = Builder().apply(block).build()
  }

  class Builder {
    private val servers = LspServerRegistry()
    private val windows = LspWindowEventBus()
    private val uiRegistry = LspUiRegistry()

    fun server(descriptor: LspServerDescriptor) {
      servers.register(descriptor)
    }

    fun ui(contribution: LspUiContribution) {
      uiRegistry.register(contribution)
    }

    fun build(): LspManager {
      val sessions = LspSessionManager(
        registry = servers,
        windowEvents = windows,
        uiRegistry = uiRegistry,
      )
      return LspManager(
        sessions = sessions,
        servers = servers,
        ui = uiRegistry,
        windows = windows,
      )
    }
  }
}
