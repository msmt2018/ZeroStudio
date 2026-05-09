package com.itsaky.androidide.lsp.models

import com.itsaky.androidide.models.Range
import java.nio.file.Path

/** Pull-diagnostics request for a single document. */
data class DocumentDiagnosticParams(
    var file: Path,
    var previousResultId: String? = null,
)

/** LSP pull-diagnostics report kind. */
enum class DocumentDiagnosticReportKind {
  FULL,
  UNCHANGED,
}

/** Pull-diagnostics report normalized into IDE model layer. */
data class DocumentDiagnosticReport(
    var kind: DocumentDiagnosticReportKind,
    var resultId: String? = null,
    var diagnostics: List<DiagnosticItem> = emptyList(),
)

data class DiagnosticRelatedInformation(
    var locationFile: Path,
    var locationRange: Range,
    var message: String,
)

