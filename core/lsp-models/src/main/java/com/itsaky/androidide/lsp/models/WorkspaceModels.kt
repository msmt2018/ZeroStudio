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
 *  @author android_zero
 */
package com.itsaky.androidide.lsp.models

import java.nio.file.Path

/**
 * 代表整个工作区的修改集合 (LSP 3.17)
 */
data class WorkspaceEdit(
    val changes: Map<String, List<TextEdit>>? = null,
    val documentChanges: List<Either<TextDocumentEdit, ResourceOperation>>? = null,
    val changeAnnotations: Map<String, ChangeAnnotation>? = null
)

data class TextDocumentEdit(
    val textDocument: VersionedTextDocumentIdentifier,
    val edits: List<Either<TextEdit, AnnotatedTextEdit>>
)

data class AnnotatedTextEdit(
    val range: com.itsaky.androidide.lsp.rpc.Range,
    val newText: String,
    val annotationId: String
)

data class ChangeAnnotation(
    val label: String,
    val needsConfirmation: Boolean = false,
    val description: String? = null
)

data class ResourceOperation(val kind: String, val uri: String)

/**
 * Legacy single-document change model retained for compatibility with existing code-action
 * producers.
 */
data class DocumentChange(
    var file: Path? = null,
    var edits: List<TextEdit> = emptyList(),
)
