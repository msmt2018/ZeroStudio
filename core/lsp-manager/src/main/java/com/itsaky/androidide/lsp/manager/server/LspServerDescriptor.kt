package com.itsaky.androidide.lsp.manager.server

import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.LanguageServerDefinition

/**
 * Plug-in descriptor for a language server implementation.
 *
 * Implementations may be backed by an in-process LSP4J server, a stdio process,
 * a socket, or any custom [LanguageServerDefinition] supported by sora-editor-lsp.
 */
data class LspServerDescriptor(
  val id: String,
  val displayName: String,
  val languageIds: Set<String>,
  val fileExtensions: Set<String> = emptySet(),
  val priority: Int = 0,
  val definitionFactory: LspServerDefinitionFactory,
  val capabilities: LspFeatureSet = LspFeatureSet.all(),
)

fun interface LspServerDefinitionFactory {
  fun create(context: LspServerLaunchContext): LanguageServerDefinition
}

data class LspServerLaunchContext(
  val projectRoot: String,
  val workspaceFolders: List<String> = listOf(projectRoot),
  val environment: Map<String, String> = emptyMap(),
)
