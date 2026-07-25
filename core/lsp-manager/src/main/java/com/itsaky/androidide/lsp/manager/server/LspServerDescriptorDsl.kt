package com.itsaky.androidide.lsp.manager.server

import io.github.rosemoe.sora.lsp.client.connection.ConnectionDefinition
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition

/** DSL entry point for registering a detachable language server. */
fun lspServerDescriptor(block: LspServerDescriptorBuilder.() -> Unit): LspServerDescriptor {
  return LspServerDescriptorBuilder().apply(block).build()
}

class LspServerDescriptorBuilder {
  var id: String = ""
  var displayName: String = ""
  var priority: Int = 0
  var languageIds: Set<String> = emptySet()
  var fileExtensions: Set<String> = emptySet()
  var capabilities: LspFeatureSet = LspFeatureSet.all()
  private var factory: LspServerDefinitionFactory? = null

  fun languages(vararg values: String) {
    languageIds = values.toSet()
  }

  fun extensions(vararg values: String) {
    fileExtensions = values.map { if (it.startsWith('.')) it else ".$it" }.toSet()
  }

  fun features(vararg values: LspFeatureGroup) {
    capabilities = LspFeatureSet.of(*values)
  }

  fun definition(factory: LspServerDefinitionFactory) {
    this.factory = factory
  }

  fun connection(connection: ConnectionDefinition) {
    definition { _ ->
      CustomLanguageServerDefinition(
        ext = fileExtensions.firstOrNull()?.removePrefix(".") ?: languageIds.first(),
        serverConnectProvider = connection.asServerConnectProvider(),
        name = displayName.ifBlank { id },
        extensionsOverride = fileExtensions.toList(),
      ).also { it.ext = languageIds.firstOrNull() ?: it.ext }
    }
  }

  fun build(): LspServerDescriptor {
    val actualFactory = requireNotNull(factory) { "Language server definition factory is required." }
    val actualDisplayName = displayName.ifBlank { id }
    require(id.isNotBlank()) { "Language server id is required." }
    require(languageIds.isNotEmpty() || fileExtensions.isNotEmpty()) {
      "At least one language id or file extension is required."
    }
    return LspServerDescriptor(
      id = id,
      displayName = actualDisplayName,
      languageIds = languageIds,
      fileExtensions = fileExtensions,
      priority = priority,
      definitionFactory = actualFactory,
      capabilities = capabilities,
    )
  }
}
