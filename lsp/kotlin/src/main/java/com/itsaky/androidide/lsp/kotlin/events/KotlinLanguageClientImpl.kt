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

package com.itsaky.androidide.lsp.kotlin.events

import android.widget.Toast
import com.blankj.utilcode.util.ActivityUtils
import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.interfaces.IEditorHandler
import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.kotlin.utils.KlsUriDecoder
import com.itsaky.androidide.lsp.kotlin.utils.KotlinEditorEditInterceptor
import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Location
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.utils.Logger
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Kotlin LSP 语言客户端实现
 *
 * @author android_zero
 */
class KotlinLanguageClientImpl : ILanguageClient {

  private val diagnosticsCache = ConcurrentHashMap<String, List<DiagnosticItem>>()

  companion object {
    private val log = Logger.instance("KotlinLanguageClientImpl")
  }

  override fun publishDiagnostics(result: DiagnosticResult) {
    if (result === DiagnosticResult.NO_UPDATE) return

    val pathStr = result.file.toString()
    diagnosticsCache[pathStr] = result.diagnostics

    log.info("Received ${result.diagnostics.size} diagnostics for $pathStr")

    com.blankj.utilcode.util.ThreadUtils.runOnUiThread {
      val handler = ActivityUtils.getTopActivity() as? IEditorHandler
      val currentEditor = handler?.let {
        try {
          it.javaClass.getMethod("getCurrentEditor").invoke(it)
              as? io.github.rosemoe.sora.widget.CodeEditor
        } catch (e: Exception) {
          null
        }
      }
      val currentFile = handler?.let {
        try {
          it.javaClass.getMethod("getCurrentFile").invoke(it) as? java.io.File
        } catch (e: Exception) {
          null
        }
      }

      if (
          currentFile?.absolutePath == result.file.toAbsolutePath().toString() &&
              currentEditor != null
      ) {
        val container = io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer()
        val text = currentEditor.text
        result.diagnostics.forEach { diag ->
          container.addDiagnostic(diag.asDiagnosticRegion(text))
        }
        currentEditor.diagnostics = container
      }
    }
  }

  override fun getDiagnosticAt(file: File, line: Int, column: Int): DiagnosticItem? {
    val items = diagnosticsCache[file.absolutePath] ?: return null
    return items
        .filter { contains(it.range, line, column) }
        .minByOrNull { it.severityValue }
  }

  override fun performCodeAction(params: PerformCodeActionParams) {
    val action = params.action
    if (action.edit != null) {
      applyWorkspaceEdit(action.edit!!)
      return
    }
    val changes = action.changes
    if (!changes.isNullOrEmpty()) {
      val mapped =
          changes.associate { change ->
            (change.file?.toUri()?.toString() ?: "") to change.edits
          }
      applyWorkspaceEdit(WorkspaceEdit(changes = mapped))
    }
  }

  override fun showDocument(params: ShowDocumentParams): ShowDocumentResult {
    val uriStr = params.uri

    val targetFile =
        if (KlsUriDecoder.isKlsUri(uriStr)) {
          KlsUriDecoder.createTempReadOnlyFileForKls(uriStr)
        } else {
          File(URI(uriStr))
        }

    if (targetFile != null && targetFile.exists()) {
      com.blankj.utilcode.util.ThreadUtils.runOnUiThread {
        val handler = ActivityUtils.getTopActivity() as? IEditorHandler
        handler?.openFileAndSelect(targetFile, params.selection?.let(::toIdeRange))
      }
      return ShowDocumentResult(true)
    }

    return ShowDocumentResult(false)
  }

  override fun showLocations(locations: List<Location>) {
    // 交由具体 Action 自身通过 Dialog 展现，见 KotlinFindReferencesAction
  }

  override fun applyWorkspaceEdit(edit: WorkspaceEdit): Boolean {
    val handler = ActivityUtils.getTopActivity() as? IEditorHandler
    val currentEditor = handler?.let {
      try {
        it.javaClass.getMethod("getCurrentEditor").invoke(it)
            as? io.github.rosemoe.sora.widget.CodeEditor
      } catch (e: Exception) {
        null
      }
    }
    val currentFile = handler?.let {
      try {
        it.javaClass.getMethod("getCurrentFile").invoke(it) as? java.io.File
      } catch (e: Exception) {
        null
      }
    }

    return KotlinEditorEditInterceptor.applyEdit(currentEditor, currentFile?.absolutePath, edit)
  }

  override fun showMessage(params: ShowMessageParams) {
    com.blankj.utilcode.util.ThreadUtils.runOnUiThread {
      val ctx = BaseApplication.getBaseInstance()
      Toast.makeText(ctx, "Kotlin LSP: ${params.message}", Toast.LENGTH_SHORT).show()
    }
  }

  override fun logMessage(params: LogMessageParams) {
    when (params.type) {
      MessageType.Error -> log.error(params.message)
      MessageType.Warning -> log.warn(params.message)
      MessageType.Info -> log.info(params.message)
      else -> log.debug(params.message)
    }
  }

  private fun contains(
      range: com.itsaky.androidide.lsp.rpc.Range,
      line: Int,
      column: Int,
  ): Boolean {
    val startBefore =
        line > range.start.line || (line == range.start.line && column >= range.start.character)
    val endAfter =
        line < range.end.line || (line == range.end.line && column <= range.end.character)
    return startBefore && endAfter
  }

  private fun toIdeRange(range: com.itsaky.androidide.lsp.rpc.Range): Range {
    return Range(
        Position(range.start.line, range.start.character),
        Position(range.end.line, range.end.character),
    )
  }
}
