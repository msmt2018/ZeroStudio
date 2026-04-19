package com.itsaky.androidide.editor.ui

import androidx.appcompat.app.AlertDialog
import com.itsaky.androidide.lsp.kotlin.KotlinLanguageServerImpl
import com.itsaky.androidide.lsp.kotlin.providers.KotlinCodeActionProvider
import com.itsaky.androidide.lsp.models.CodeActionItem
import com.itsaky.androidide.lsp.models.DiagnosticItem
import com.itsaky.androidide.lsp.models.PerformCodeActionParams
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import org.slf4j.LoggerFactory

/** Diagnostic handling extensions for IDEEditor - Simplified Version */
private val log = LoggerFactory.getLogger("IDEEditorDiagnostics")

// Storage key for diagnostic handler
private const val DIAGNOSTIC_HANDLER_KEY = "diagnostic_handler"

private class DiagnosticHandler {
  private val diagnosticsByLine = mutableMapOf<Int, MutableList<DiagnosticItem>>()

  fun updateDiagnostics(diagnostics: List<DiagnosticItem>) {
    diagnosticsByLine.clear()
    diagnostics.forEach { diagnostic ->
      val line = diagnostic.range.start.line
      diagnosticsByLine.getOrPut(line) { mutableListOf() }.add(diagnostic)
    }
    log.info("Updated diagnostics for {} lines", diagnosticsByLine.size)
  }

  fun getDiagnosticsAtLine(line: Int): List<DiagnosticItem> {
    return diagnosticsByLine[line] ?: emptyList()
  }

  fun getDiagnosticAt(line: Int, column: Int): DiagnosticItem? {
    return diagnosticsByLine[line]?.firstOrNull { diagnostic ->
      val range = diagnostic.range
      line == range.start.line &&
          column >= range.start.character &&
          column <= range.end.character
    }
  }

  fun clear() {
    diagnosticsByLine.clear()
  }
}

/** Get or create diagnostic handler for this editor */
private fun IDEEditor.getDiagnosticHandler(): DiagnosticHandler {
  var handler = getTag(DIAGNOSTIC_HANDLER_KEY.hashCode()) as? DiagnosticHandler
  if (handler == null) {
    handler = DiagnosticHandler()
    setTag(DIAGNOSTIC_HANDLER_KEY.hashCode(), handler)
  }
  return handler
}

/** Initialize diagnostic handling for the editor Simplified version - no touch listener */
fun IDEEditor.initDiagnosticHandling() {
  // Just create the handler
  getDiagnosticHandler()
  log.info("Diagnostic handling initialized for editor")
}

/** Update diagnostics in the editor */
fun IDEEditor.updateEditorDiagnostics(diagnostics: List<DiagnosticItem>) {
  getDiagnosticHandler().updateDiagnostics(diagnostics)
}

/**
 * Manually trigger import fix at cursor position Call this from a menu action or keyboard shortcut
 */
fun IDEEditor.applyImportFixAtCursor(): Boolean {
  val file = this.file ?: return false
  val languageServer = this.languageServer

  if (languageServer !is KotlinLanguageServerImpl) {
    return false
  }

  val cursor = this.cursor ?: return false
  val line = cursor.leftLine
  val column = cursor.leftColumn

  val diagnostic = getDiagnosticHandler().getDiagnosticAt(line, column)
  val diagnosticsList = if (diagnostic != null) listOf(diagnostic) else emptyList()

  val range = Range(start = Position(line, column), end = Position(line, column))

  try {
    val provider = KotlinCodeActionProvider()
    val actions = provider.computeCodeActions(file.toPath(), range, diagnosticsList)
    val importActions = actions.filter { it.title.contains("Import", ignoreCase = true) }

    return when {
      importActions.isEmpty() -> {
        log.debug("No import options available")
        false
      }
      importActions.size == 1 -> {
        // Single option - apply directly
        languageServer.client?.performCodeAction(PerformCodeActionParams(true, importActions[0]))
        log.info("Auto-imported: {}", importActions[0].title)
        true
      }
      else -> {
        // Multiple options - show dialog
        val options = importActions.map { it.title }
        showImportSelectionDialog(options, importActions, languageServer)
        true
      }
    }
  } catch (e: Exception) {
    log.error("Failed to apply import fix", e)
    return false
  }
}

/** Show dialog to select import */
private fun IDEEditor.showImportSelectionDialog(
    options: List<String>,
    actions: List<CodeActionItem>,
    languageServer: KotlinLanguageServerImpl,
) {
  AlertDialog.Builder(context)
      .setTitle("Choose Import")
      .setItems(options.toTypedArray()) { dialog, which ->
        try {
          languageServer.client?.performCodeAction(PerformCodeActionParams(true, actions[which]))
          log.info("User selected import: {}", options[which])
        } catch (e: Exception) {
          log.error("Failed to apply import", e)
        }
        dialog.dismiss()
      }
      .setNegativeButton("Cancel", null)
      .show()
}

/** Clear all diagnostics */
fun IDEEditor.clearDiagnostics() {
  getDiagnosticHandler().clear()
}
