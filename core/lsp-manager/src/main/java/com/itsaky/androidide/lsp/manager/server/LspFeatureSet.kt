package com.itsaky.androidide.lsp.manager.server

/** LSP feature groups exposed for dispatching, UI contribution, and capability gating. */
enum class LspFeatureGroup {
  Lifecycle,
  TextSynchronization,
  Completion,
  Hover,
  SignatureHelp,
  Diagnostics,
  CodeAction,
  CodeLens,
  Formatting,
  Rename,
  Definition,
  Declaration,
  TypeDefinition,
  Implementation,
  References,
  DocumentHighlight,
  DocumentSymbol,
  WorkspaceSymbol,
  FoldingRange,
  SelectionRange,
  SemanticTokens,
  InlayHint,
  InlineValue,
  InlineCompletion,
  CallHierarchy,
  TypeHierarchy,
  LinkedEditingRange,
  DocumentLink,
  DocumentColor,
  Moniker,
  Progress,
  Window,
  WorkspaceFolders,
  ExecuteCommand,
  FileOperations,
  NotebookDocument,
}

data class LspFeatureSet(val groups: Set<LspFeatureGroup>) {
  operator fun contains(group: LspFeatureGroup): Boolean = group in groups

  companion object {
    fun all(): LspFeatureSet = LspFeatureSet(LspFeatureGroup.entries.toSet())
    fun of(vararg groups: LspFeatureGroup): LspFeatureSet = LspFeatureSet(groups.toSet())
  }
}
