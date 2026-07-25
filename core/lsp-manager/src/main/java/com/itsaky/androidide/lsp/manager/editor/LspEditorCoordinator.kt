package com.itsaky.androidide.lsp.manager.editor

import com.itsaky.androidide.lsp.manager.session.LspSession
import com.itsaky.androidide.lsp.manager.session.LspSessionManager
import com.itsaky.androidide.lsp.manager.session.LspSessionRequest
import io.github.rosemoe.sora.widget.CodeEditor

/** Bridges the editor core (sora CodeEditor) with managed LSP sessions. */
class LspEditorCoordinator(private val sessionManager: LspSessionManager) {
  fun attachEditor(editor: CodeEditor, request: LspSessionRequest): LspSession {
    val session = sessionManager.open(request)
    editor.tag = session.id
    return session
  }

  fun detachEditor(editor: CodeEditor) {
    (editor.tag as? String)?.let { sessionManager.close(it) }
    editor.tag = null
  }
}
