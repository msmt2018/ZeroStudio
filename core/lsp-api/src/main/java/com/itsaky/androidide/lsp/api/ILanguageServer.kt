/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.lsp.api

import com.itsaky.androidide.lsp.models.CallHierarchyItem
import com.itsaky.androidide.lsp.models.CodeFormatResult
import com.itsaky.androidide.lsp.models.CodeLens
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import com.itsaky.androidide.lsp.models.DefinitionParams
import com.itsaky.androidide.lsp.models.DefinitionResult
import com.itsaky.androidide.lsp.models.DiagnosticResult
import com.itsaky.androidide.lsp.models.DidChangeTextDocumentParams
import com.itsaky.androidide.lsp.models.DidCloseTextDocumentParams
import com.itsaky.androidide.lsp.models.DidOpenTextDocumentParams
import com.itsaky.androidide.lsp.models.DidSaveTextDocumentParams
import com.itsaky.androidide.lsp.models.DocumentLink
import com.itsaky.androidide.lsp.models.DocumentDiagnosticParams
import com.itsaky.androidide.lsp.models.DocumentDiagnosticReport
import com.itsaky.androidide.lsp.models.ExecuteCommandParams
import com.itsaky.androidide.lsp.models.DocumentSymbolsResult
import com.itsaky.androidide.lsp.models.ExpandSelectionParams
import com.itsaky.androidide.lsp.models.FoldingRange
import com.itsaky.androidide.lsp.models.FormatCodeParams
import com.itsaky.androidide.lsp.models.InlayHint
import com.itsaky.androidide.lsp.models.InlayHintParams
import com.itsaky.androidide.lsp.models.LSPFailure
import com.itsaky.androidide.lsp.models.PrepareRenameResult
import com.itsaky.androidide.lsp.models.ReferenceParams
import com.itsaky.androidide.lsp.models.ReferenceResult
import com.itsaky.androidide.lsp.models.RenameParams
import com.itsaky.androidide.lsp.models.SelectionRange
import com.itsaky.androidide.lsp.models.CodeActionItem
import com.itsaky.androidide.lsp.models.SelectionRangesParams
import com.itsaky.androidide.lsp.models.SemanticTokens
import com.itsaky.androidide.lsp.models.SemanticTokensDelta
import com.itsaky.androidide.lsp.models.SemanticTokensParams
import com.itsaky.androidide.lsp.models.SignatureHelp
import com.itsaky.androidide.lsp.models.SignatureHelpParams
import com.itsaky.androidide.lsp.models.TypeHierarchyItem
import com.itsaky.androidide.lsp.models.WorkspaceEdit
import com.itsaky.androidide.lsp.models.WorkspaceSymbolsResult
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.projects.IWorkspace
import java.nio.file.Path

/**
 * A language server provides API for providing functions related to a specific file type.
 *
 * @author Akash Yadav
 */
interface ILanguageServer {

  val serverId: String?

  /**
   * Called by client to notify the server to shutdown. Language servers must release all the
   * resources in use.
   *
   * After this is called, clients must re-initialize the server.
   */
  fun shutdown()

  /**
   * Set the client to whom notifications and events must be sent.
   *
   * @param client The client to set.
   */
  fun connectClient(client: ILanguageClient?)

  /**
   * Get the instance of the language client connected to this server.
   *
   * @return The language client.
   */
  val client: ILanguageClient?

  /**
   * Apply settings to the language server. Its up to the language server how it applies these
   * settings to the language service providers.
   *
   * @param settings The new settings to use. Pass `null` to use default settings.
   */
  fun applySettings(settings: IServerSettings?)

  /**
   * Setup this language server with the given workspace. Servers are not expected to keep a
   * reference to the provided workspace. Instead, use
   * [getRootWorkspace()][com.itsaky.androidide.projects.IProjectManager.workspace] to obtain the
   * workspace instance.
   *
   * @param workspace The initialized workspace.
   */
  fun setupWorkspace(workspace: IWorkspace)

  /** Text document lifecycle notifications (LSP didOpen/didChange/didClose/didSave). */
  fun didOpen(params: DidOpenTextDocumentParams) {}

  fun didChange(params: DidChangeTextDocumentParams) {}

  fun didClose(params: DidCloseTextDocumentParams) {}

  fun didSave(params: DidSaveTextDocumentParams) {}

  /**
   * Compute code completions for the given completion params.
   *
   * @param params The completion params.
   * @param cancelChecker
   * @return The completion provider.
   */
  fun complete(params: CompletionParams?): CompletionResult

  /**
   * Find references using the given params.
   *
   * @param params The params to use for computing references.
   * @param cancelChecker
   * @return The result of the computation.
   */
  suspend fun findReferences(params: ReferenceParams): ReferenceResult

  /**
   * Find definition using the given params.
   *
   * @param params The params to use for computing the definition.
   * @param cancelChecker
   * @return The result of the computation.
   */
  suspend fun findDefinition(params: DefinitionParams): DefinitionResult

  /**
   * Request the server to provide an expanded selection range for the current selection.
   *
   * @param params The params for computing the expanded selection range.
   * @return The expanded range or same selection range if computation was failed.
   */
  suspend fun expandSelection(params: ExpandSelectionParams): Range

  /**
   * Compute signature help with the given params.
   *
   * @param params The params to compute signature help.
   * @return The signature help.
   */
  suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp

  /** Request hover/tooltip documentation at a position. Default returns empty content. */
  suspend fun hover(params: DefinitionParams): com.itsaky.androidide.lsp.models.MarkupContent {
    return com.itsaky.androidide.lsp.models.MarkupContent()
  }

  /**
   * Analyze the given file and provide diagnostics from the analyze result.
   *
   * @param file The file to analyze.
   * @return The diagnostic result. Points to [DiagnosticResult.NO_UPDATE] if no diagnotic items are
   *   available.
   */
  suspend fun analyze(file: Path): DiagnosticResult

  /**
   * Whether this server supports pull-based diagnostics (textDocument/diagnostic).
   */
  fun supportsDocumentDiagnostics(): Boolean = false

  /**
   * Pull diagnostics for a document. Default returns null when unsupported.
   */
  suspend fun documentDiagnostics(params: DocumentDiagnosticParams): DocumentDiagnosticReport? = null


  /**
   * Format the given source code input.
   *
   * @param params The code formatting parameters.
   * @return The formatted source.
   */
  fun formatCode(params: FormatCodeParams?): CodeFormatResult {
    return CodeFormatResult(false, mutableListOf())
  }

  /**
   * Handle failure caused by LSP
   *
   * @param failure [LSPFailure] describing the failure.
   * @return `true` if the failure was handled. `false` otherwise.
   */

  /** Document symbols for outline/tree view. */
  suspend fun documentSymbols(file: Path): DocumentSymbolsResult {
    return DocumentSymbolsResult()
  }

  /** Workspace-wide symbol search. */
  suspend fun workspaceSymbols(query: String): WorkspaceSymbolsResult {
    return WorkspaceSymbolsResult()
  }

  /** Prepare rename at cursor. */
  suspend fun prepareRename(params: DefinitionParams): PrepareRenameResult? {
    return null
  }

  /** Apply rename and return workspace edits. */
  suspend fun rename(params: RenameParams): WorkspaceEdit {
    return WorkspaceEdit()
  }

  /** Folding ranges in a document. */
  suspend fun foldingRanges(file: Path): List<FoldingRange> {
    return emptyList()
  }

  /** Selection ranges for multiple positions. */
  suspend fun selectionRanges(params: SelectionRangesParams): List<SelectionRange> {
    return emptyList()
  }

  /** Full semantic tokens. */
  suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens {
    return SemanticTokens()
  }

  /** Semantic tokens in range. */
  suspend fun semanticTokensRange(params: SemanticTokensParams): SemanticTokens {
    return SemanticTokens()
  }

  /** Delta semantic tokens update. */
  suspend fun semanticTokensDelta(params: SemanticTokensParams): SemanticTokensDelta {
    return SemanticTokensDelta()
  }

  /** Inlay hints in range. */
  suspend fun inlayHints(params: InlayHintParams): List<InlayHint> {
    return emptyList()
  }

  /** Document links with optional targets. */
  suspend fun documentLinks(file: Path): List<DocumentLink> {
    return emptyList()
  }

  /** Code lens entries for document. */
  suspend fun codeLens(file: Path): List<CodeLens> {
    return emptyList()
  }

  /** Call hierarchy prepare. */
  suspend fun callHierarchy(params: DefinitionParams): List<CallHierarchyItem> {
    return emptyList()
  }

  /** Type hierarchy prepare. */
  suspend fun typeHierarchy(params: DefinitionParams): List<TypeHierarchyItem> {
    return emptyList()
  }

  /**
   * Execute a workspace command on server side (LSP: workspace/executeCommand).
   *
   * Implementations should return any protocol-compatible value (including `null`).
   */
  suspend fun executeCommand(params: ExecuteCommandParams): Any? {
    return null
  }

  /**
   * Resolve additional code action information lazily (LSP: codeAction/resolve).
   */
  suspend fun resolveCodeAction(action: CodeActionItem): CodeActionItem {
    return action
  }

  fun handleFailure(failure: LSPFailure?): Boolean {
    return false
  }
}
