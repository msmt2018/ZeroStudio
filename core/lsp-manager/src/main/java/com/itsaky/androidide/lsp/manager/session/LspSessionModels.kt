package com.itsaky.androidide.lsp.manager.session

import com.itsaky.androidide.lsp.manager.server.LspServerDescriptor
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.LanguageServerDefinition

data class LspSessionRequest(
  val projectRoot: String,
  val documentUri: String,
  val languageId: String,
  val workspaceFolders: List<String> = listOf(projectRoot),
  val environment: Map<String, String> = emptyMap(),
  val descriptor: LspServerDescriptor? = null,
)

data class LspSession(
  val id: String,
  val descriptor: LspServerDescriptor,
  val definition: LanguageServerDefinition,
  val documentUri: String,
  val languageId: String,
  val projectRoot: String,
)
