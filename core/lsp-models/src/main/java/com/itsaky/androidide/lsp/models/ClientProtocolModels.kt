package com.itsaky.androidide.lsp.models

import com.itsaky.androidide.lsp.rpc.Range

/** Workspace edit request sent from server to client. */
data class ApplyWorkspaceEditParams(
    val label: String? = null,
    val edit: WorkspaceEdit,
)

/** Workspace edit response returned by client. */
data class ApplyWorkspaceEditResponse(
    val applied: Boolean,
    val failureReason: String? = null,
    val failedChange: Int? = null,
)

/** Action item wrapper used by ILanguageClient contract. */
data class CodeActionItem(
    var title: String = "",
    var edit: WorkspaceEdit? = null,
    var command: Command? = null,
    // Legacy fields kept for backward compatibility with existing Java actions.
    var kind: String? = null,
    var changes: List<DocumentChange>? = null,
)

/** Request to perform a specific code action. */
data class PerformCodeActionParams(
    val async: Boolean = true,
    val action: CodeActionItem,
)

/** LSP show document request payload. */
data class ShowDocumentParams(
    val uri: String,
    val external: Boolean = false,
    val takeFocus: Boolean = true,
    val selection: Range? = null,
)

/** Result for show document request. */
data class ShowDocumentResult(
    val success: Boolean,
)

/** Completion list model for mixed completion responses. */
data class CompletionList(
    val isIncomplete: Boolean = false,
    val items: List<CompletionItem> = emptyList(),
)

/** Semantic tokens delta response model. */
data class SemanticTokensDelta(
    val resultId: String? = null,
    val edits: List<SemanticTokensEdit> = emptyList(),
)

data class SemanticTokensEdit(
    val start: Int,
    val deleteCount: Int,
    val data: List<Int>? = null,
)
