package com.itsaky.androidide.lsp.manager.protocol

/**
 * Stable method identifiers for routing every standard Language Server Protocol surface.
 *
 * The manager keeps the protocol boundary string-based so feature modules can forward raw
 * LSP4J/JSON-RPC payloads without forcing editor UI code to depend on a concrete server process.
 */
enum class LspProtocolMethod(val methodName: String, val direction: LspMessageDirection) {
  Initialize("initialize", LspMessageDirection.ClientToServer),
  Initialized("initialized", LspMessageDirection.ClientToServer),
  Shutdown("shutdown", LspMessageDirection.ClientToServer),
  Exit("exit", LspMessageDirection.ClientToServer),
  CancelRequest("$/cancelRequest", LspMessageDirection.Bidirectional),
  Progress("$/progress", LspMessageDirection.Bidirectional),
  SetTrace("$/setTrace", LspMessageDirection.ClientToServer),
  LogTrace("$/logTrace", LspMessageDirection.ServerToClient),

  WindowShowMessage("window/showMessage", LspMessageDirection.ServerToClient),
  WindowShowMessageRequest("window/showMessageRequest", LspMessageDirection.ServerToClient),
  WindowLogMessage("window/logMessage", LspMessageDirection.ServerToClient),
  WindowWorkDoneProgressCreate("window/workDoneProgress/create", LspMessageDirection.ServerToClient),

  TelemetryEvent("telemetry/event", LspMessageDirection.ServerToClient),
  ClientRegisterCapability("client/registerCapability", LspMessageDirection.ServerToClient),
  ClientUnregisterCapability("client/unregisterCapability", LspMessageDirection.ServerToClient),

  WorkspaceWorkspaceFolders("workspace/workspaceFolders", LspMessageDirection.ServerToClient),
  WorkspaceDidChangeWorkspaceFolders("workspace/didChangeWorkspaceFolders", LspMessageDirection.ClientToServer),
  WorkspaceDidChangeConfiguration("workspace/didChangeConfiguration", LspMessageDirection.ClientToServer),
  WorkspaceConfiguration("workspace/configuration", LspMessageDirection.ServerToClient),
  WorkspaceDidChangeWatchedFiles("workspace/didChangeWatchedFiles", LspMessageDirection.ClientToServer),
  WorkspaceSymbol("workspace/symbol", LspMessageDirection.ClientToServer),
  WorkspaceSymbolResolve("workspaceSymbol/resolve", LspMessageDirection.ClientToServer),
  WorkspaceExecuteCommand("workspace/executeCommand", LspMessageDirection.ClientToServer),
  WorkspaceApplyEdit("workspace/applyEdit", LspMessageDirection.ServerToClient),
  WorkspaceWillCreateFiles("workspace/willCreateFiles", LspMessageDirection.ClientToServer),
  WorkspaceDidCreateFiles("workspace/didCreateFiles", LspMessageDirection.ClientToServer),
  WorkspaceWillRenameFiles("workspace/willRenameFiles", LspMessageDirection.ClientToServer),
  WorkspaceDidRenameFiles("workspace/didRenameFiles", LspMessageDirection.ClientToServer),
  WorkspaceWillDeleteFiles("workspace/willDeleteFiles", LspMessageDirection.ClientToServer),
  WorkspaceDidDeleteFiles("workspace/didDeleteFiles", LspMessageDirection.ClientToServer),

  TextDocumentDidOpen("textDocument/didOpen", LspMessageDirection.ClientToServer),
  TextDocumentDidChange("textDocument/didChange", LspMessageDirection.ClientToServer),
  TextDocumentWillSave("textDocument/willSave", LspMessageDirection.ClientToServer),
  TextDocumentWillSaveWaitUntil("textDocument/willSaveWaitUntil", LspMessageDirection.ClientToServer),
  TextDocumentDidSave("textDocument/didSave", LspMessageDirection.ClientToServer),
  TextDocumentDidClose("textDocument/didClose", LspMessageDirection.ClientToServer),
  TextDocumentPublishDiagnostics("textDocument/publishDiagnostics", LspMessageDirection.ServerToClient),
  TextDocumentDiagnostic("textDocument/diagnostic", LspMessageDirection.ClientToServer),
  WorkspaceDiagnostic("workspace/diagnostic", LspMessageDirection.ClientToServer),
  WorkspaceDiagnosticRefresh("workspace/diagnostic/refresh", LspMessageDirection.ServerToClient),

  TextDocumentCompletion("textDocument/completion", LspMessageDirection.ClientToServer),
  CompletionItemResolve("completionItem/resolve", LspMessageDirection.ClientToServer),
  TextDocumentHover("textDocument/hover", LspMessageDirection.ClientToServer),
  TextDocumentSignatureHelp("textDocument/signatureHelp", LspMessageDirection.ClientToServer),
  TextDocumentDeclaration("textDocument/declaration", LspMessageDirection.ClientToServer),
  TextDocumentDefinition("textDocument/definition", LspMessageDirection.ClientToServer),
  TextDocumentTypeDefinition("textDocument/typeDefinition", LspMessageDirection.ClientToServer),
  TextDocumentImplementation("textDocument/implementation", LspMessageDirection.ClientToServer),
  TextDocumentReferences("textDocument/references", LspMessageDirection.ClientToServer),
  TextDocumentDocumentHighlight("textDocument/documentHighlight", LspMessageDirection.ClientToServer),
  TextDocumentDocumentSymbol("textDocument/documentSymbol", LspMessageDirection.ClientToServer),
  TextDocumentCodeAction("textDocument/codeAction", LspMessageDirection.ClientToServer),
  CodeActionResolve("codeAction/resolve", LspMessageDirection.ClientToServer),
  TextDocumentCodeLens("textDocument/codeLens", LspMessageDirection.ClientToServer),
  CodeLensResolve("codeLens/resolve", LspMessageDirection.ClientToServer),
  CodeLensRefresh("workspace/codeLens/refresh", LspMessageDirection.ServerToClient),
  TextDocumentDocumentLink("textDocument/documentLink", LspMessageDirection.ClientToServer),
  DocumentLinkResolve("documentLink/resolve", LspMessageDirection.ClientToServer),
  TextDocumentDocumentColor("textDocument/documentColor", LspMessageDirection.ClientToServer),
  TextDocumentColorPresentation("textDocument/colorPresentation", LspMessageDirection.ClientToServer),
  TextDocumentFormatting("textDocument/formatting", LspMessageDirection.ClientToServer),
  TextDocumentRangeFormatting("textDocument/rangeFormatting", LspMessageDirection.ClientToServer),
  TextDocumentRangesFormatting("textDocument/rangesFormatting", LspMessageDirection.ClientToServer),
  TextDocumentOnTypeFormatting("textDocument/onTypeFormatting", LspMessageDirection.ClientToServer),
  TextDocumentRename("textDocument/rename", LspMessageDirection.ClientToServer),
  TextDocumentPrepareRename("textDocument/prepareRename", LspMessageDirection.ClientToServer),
  TextDocumentFoldingRange("textDocument/foldingRange", LspMessageDirection.ClientToServer),
  TextDocumentSelectionRange("textDocument/selectionRange", LspMessageDirection.ClientToServer),
  TextDocumentPrepareCallHierarchy("textDocument/prepareCallHierarchy", LspMessageDirection.ClientToServer),
  CallHierarchyIncomingCalls("callHierarchy/incomingCalls", LspMessageDirection.ClientToServer),
  CallHierarchyOutgoingCalls("callHierarchy/outgoingCalls", LspMessageDirection.ClientToServer),
  TextDocumentSemanticTokensFull("textDocument/semanticTokens/full", LspMessageDirection.ClientToServer),
  TextDocumentSemanticTokensFullDelta("textDocument/semanticTokens/full/delta", LspMessageDirection.ClientToServer),
  TextDocumentSemanticTokensRange("textDocument/semanticTokens/range", LspMessageDirection.ClientToServer),
  SemanticTokensRefresh("workspace/semanticTokens/refresh", LspMessageDirection.ServerToClient),
  TextDocumentLinkedEditingRange("textDocument/linkedEditingRange", LspMessageDirection.ClientToServer),
  TextDocumentMoniker("textDocument/moniker", LspMessageDirection.ClientToServer),
  TextDocumentPrepareTypeHierarchy("textDocument/prepareTypeHierarchy", LspMessageDirection.ClientToServer),
  TypeHierarchySupertypes("typeHierarchy/supertypes", LspMessageDirection.ClientToServer),
  TypeHierarchySubtypes("typeHierarchy/subtypes", LspMessageDirection.ClientToServer),
  TextDocumentInlineValue("textDocument/inlineValue", LspMessageDirection.ClientToServer),
  InlineValueRefresh("workspace/inlineValue/refresh", LspMessageDirection.ServerToClient),
  TextDocumentInlayHint("textDocument/inlayHint", LspMessageDirection.ClientToServer),
  InlayHintResolve("inlayHint/resolve", LspMessageDirection.ClientToServer),
  InlayHintRefresh("workspace/inlayHint/refresh", LspMessageDirection.ServerToClient),
  TextDocumentInlineCompletion("textDocument/inlineCompletion", LspMessageDirection.ClientToServer),

  NotebookDocumentDidOpen("notebookDocument/didOpen", LspMessageDirection.ClientToServer),
  NotebookDocumentDidChange("notebookDocument/didChange", LspMessageDirection.ClientToServer),
  NotebookDocumentDidSave("notebookDocument/didSave", LspMessageDirection.ClientToServer),
  NotebookDocumentDidClose("notebookDocument/didClose", LspMessageDirection.ClientToServer);

  companion object {
    private val byName = entries.associateBy { it.methodName }
    fun from(methodName: String): LspProtocolMethod? = byName[methodName]
  }
}

enum class LspMessageDirection { ClientToServer, ServerToClient, Bidirectional }
