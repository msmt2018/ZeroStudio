package com.itsaky.androidide.editor.ui

import androidx.appcompat.app.AlertDialog
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.models.DiagnosticItem
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import java.nio.file.Path
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
      line == range.start.line && column >= range.start.column && column <= range.end.column
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

  if (languageServer == null) {
    return false
  }

  val cursor = this.cursor ?: return false
  val line = cursor.leftLine
  val column = cursor.leftColumn

  val diagnostic = getDiagnosticHandler().getDiagnosticAt(line, column)
  if (diagnostic?.code != "missing_import") {
    log.debug("No import fix available at cursor position")
    return false
  }

  val range = Range(start = Position(line, column), end = Position(line, column))

  try {
    val options = languageServer.getImportOptionsCompat(file.toPath(), range)

    return when {
      options.isEmpty() -> {
        log.debug("No import options available")
        false
      }
      options.size == 1 -> {
        // Single option - apply directly
        languageServer.handleDiagnosticClickCompat(file.toPath(), range).also { success ->
          if (success) {
            log.info("Auto-imported: {}", options[0])
          }
        }
      }
      else -> {
        // Multiple options - show dialog
        showImportSelectionDialog(options, file.toPath(), range, languageServer)
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
    filePath: Path,
    range: Range,
    languageServer: ILanguageServer,
) {
  AlertDialog.Builder(context)
      .setTitle("Choose Import")
      .setItems(options.toTypedArray()) { dialog, which ->
        try {
          languageServer.handleDiagnosticClickCompat(filePath, range)
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

private fun ILanguageServer.getImportOptionsCompat(file: Path, range: Range): List<String> {
  val method =
      javaClass.methods.firstOrNull { method ->
        method.name == "getImportOptions" &&
            method.parameterTypes.contentEquals(arrayOf(Path::class.java, Range::class.java))
      } ?: return emptyList()

  val result = runCatching { method.invoke(this, file, range) }.getOrNull()
  @Suppress("UNCHECKED_CAST")
  return (result as? List<*>)?.filterIsInstance<String>().orEmpty()
}

private fun ILanguageServer.handleDiagnosticClickCompat(file: Path, range: Range): Boolean {
  val method =
      javaClass.methods.firstOrNull { method ->
        method.name == "handleDiagnosticClick" &&
            method.parameterTypes.contentEquals(arrayOf(Path::class.java, Range::class.java))
      } ?: return false
  return (runCatching { method.invoke(this, file, range) }.getOrNull() as? Boolean) == true
}
