// FILE: editor/impl/src/main/java/com/itsaky/androidide/editor/lsp/LspSignatureHelpManager.kt
/*
 *  This file is part of AndroidIDE.
 *  @author android_zero
 */

package com.itsaky.androidide.editor.lsp

import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.lsp.rpc.UriConverter
import com.itsaky.androidide.lsp.rpc.Position as RpcPosition
import org.slf4j.LoggerFactory

class LspSignatureHelpManager(
    private val editor: IDEEditor,
    private val server: ILanguageServer
) {
    private val log = LoggerFactory.getLogger(LspSignatureHelpManager::class.java)

    fun requestSignatureHelp(line: Int, column: Int, char: String? = null, isRetrigger: Boolean = false) {
        val params = SignatureHelpParams(
            textDocument = TextDocumentIdentifier(UriConverter.fileToUri(editor.file!!)),
            position = RpcPosition.newBuilder().setLine(line).setCharacter(column).build(),
            context = SignatureHelpContext(
                triggerKind = if (char != null) 2 else 1,
                triggerCharacter = char,
                isRetrigger = isRetrigger
            )
        )

        server.signatureHelpAsync(params).thenAccept { help ->
            if (help == null || help.signatures.isEmpty()) {
                editor.post { editor.signatureHelpWindow.dismiss() }
                return@thenAccept
            }
            
            editor.post {
                editor.showSignatureHelp(help)
            }
        }.exceptionally {
            log.error("Signature help failed", it)
            null
        }
    }
}
