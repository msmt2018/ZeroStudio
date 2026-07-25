package com.itsaky.androidide.lsp.manager.protocol

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Type-erased protocol envelope for LSP4J objects, JSON objects, or editor-core DTOs. */
data class LspProtocolEnvelope<T : Any?>(
  val method: LspProtocolMethod,
  val payload: T,
  val documentUri: String? = null,
  val languageId: String? = null,
  val sessionId: String? = null,
)

fun interface LspProtocolHandler<T : Any?> {
  fun handle(envelope: LspProtocolEnvelope<T>): CompletableFuture<Any?>
}

/** Pluggable router used by manager, client, relay, and server implementations. */
class LspProtocolRouter {
  private val handlers = ConcurrentHashMap<LspProtocolMethod, LspProtocolHandler<Any?>>()

  fun register(method: LspProtocolMethod, handler: LspProtocolHandler<Any?>) {
    handlers[method] = handler
  }

  fun unregister(method: LspProtocolMethod): Boolean = handlers.remove(method) != null

  fun canHandle(method: LspProtocolMethod): Boolean = method in handlers

  fun dispatch(envelope: LspProtocolEnvelope<Any?>): CompletableFuture<Any?> {
    val handler = handlers[envelope.method]
      ?: return CompletableFuture.failedFuture(UnsupportedOperationException("No LSP handler registered for ${envelope.method.methodName}."))
    return handler.handle(envelope)
  }
}
