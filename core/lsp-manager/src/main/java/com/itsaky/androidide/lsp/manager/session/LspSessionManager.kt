package com.itsaky.androidide.lsp.manager.session

import com.itsaky.androidide.lsp.manager.registry.LspServerRegistry
import com.itsaky.androidide.lsp.manager.server.LspServerDescriptor
import com.itsaky.androidide.lsp.manager.server.LspServerLaunchContext
import com.itsaky.androidide.lsp.manager.protocol.LspProtocolRouter
import com.itsaky.androidide.lsp.manager.ui.LspUiRegistry
import com.itsaky.androidide.lsp.manager.window.LspWindowEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap

/** Coordinates editor documents, server descriptors, and lifecycle ownership. */
class LspSessionManager(
  val registry: LspServerRegistry = LspServerRegistry(),
  val windowEvents: LspWindowEventBus = LspWindowEventBus(),
  val protocolRouter: LspProtocolRouter = LspProtocolRouter(),
  val uiRegistry: LspUiRegistry = LspUiRegistry(),
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AutoCloseable {
  private val sessions = ConcurrentHashMap<String, LspSession>()

  fun open(request: LspSessionRequest): LspSession {
    val descriptor = request.descriptor ?: resolveDescriptor(request)
    val definition = descriptor.definitionFactory.create(
      LspServerLaunchContext(
        projectRoot = request.projectRoot,
        workspaceFolders = request.workspaceFolders,
        environment = request.environment,
      )
    )
    return LspSession(
      id = "${request.projectRoot}:${request.documentUri}:${descriptor.id}",
      descriptor = descriptor,
      definition = definition,
      documentUri = request.documentUri,
      languageId = request.languageId,
      projectRoot = request.projectRoot,
      state = LspSessionState.Running,
    ).also { sessions[it.id] = it }
  }

  fun get(sessionId: String): LspSession? = sessions[sessionId]

  fun close(sessionId: String): Boolean = sessions.remove(sessionId) != null

  fun activeSessions(): List<LspSession> = sessions.values.sortedBy { it.id }

  override fun close() {
    sessions.clear()
    scope.cancel()
  }

  private fun resolveDescriptor(request: LspSessionRequest): LspServerDescriptor {
    return registry.findByLanguageId(request.languageId).firstOrNull()
      ?: registry.findByFileName(request.documentUri).firstOrNull()
      ?: error("No language server registered for ${request.languageId} (${request.documentUri}).")
  }
}
