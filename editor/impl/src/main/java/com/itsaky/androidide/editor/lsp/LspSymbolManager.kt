// FILE: editor/impl/src/main/java/com/itsaky/androidide/editor/lsp/LspSymbolManager.kt
/*
 *  This file is part of AndroidIDE.
 *  @author android_zero
 */

package com.itsaky.androidide.editor.lsp

import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.models.*
import org.slf4j.LoggerFactory
import com.itsaky.androidide.lsp.api.AbstractLanguageServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LspSymbolManager(
    private val editor: IDEEditor,
    private val server: ILanguageServer
) {
    private val log = LoggerFactory.getLogger(LspSymbolManager::class.java)

    fun fetchDocumentSymbols(callback: (List<DocumentSymbol>) -> Unit) {
        if (server !is AbstractLanguageServer) return
        
        editor.editorScope.launch(Dispatchers.IO) {
            try {
                val filePath = editor.file?.toPath() ?: return@launch
                val result = server.documentSymbols(filePath)
                val symbols = LspFeatureBridge.flattenSymbols(result)
                editor.post {
                    callback(symbols)
                }
            } catch(e: Exception) {
                log.error("Failed to fetch document symbols", e)
            }
        }
    }

    fun searchWorkspaceSymbols(query: String, callback: (List<WorkspaceSymbol>) -> Unit) {
        if (server !is AbstractLanguageServer) return
        
        editor.editorScope.launch(Dispatchers.IO) {
            try {
                val result = server.workspaceSymbols(query)
                editor.post {
                    callback(result.symbols)
                }
            } catch(e: Exception) {
                log.error("Failed to fetch workspace symbols", e)
            }
        }
    }
}
