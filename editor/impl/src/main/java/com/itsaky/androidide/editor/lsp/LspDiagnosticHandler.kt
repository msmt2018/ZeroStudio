// FILE: editor/impl/src/main/java/com/itsaky/androidide/editor/lsp/LspDiagnosticHandler.kt
/*
 *  This file is part of AndroidIDE.
 *  @author android_zero
 */

package com.itsaky.androidide.editor.lsp

import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.lsp.models.PublishDiagnosticsParams
import com.itsaky.androidide.lsp.rpc.UriConverter
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.slf4j.LoggerFactory

class LspDiagnosticHandler(private val editor: IDEEditor) {

    private val log = LoggerFactory.getLogger(LspDiagnosticHandler::class.java)
    private val fileUri by lazy { UriConverter.fileToUri(editor.file!!) }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDiagnosticsReceived(params: PublishDiagnosticsParams) {
        if (editor.file == null || params.uri != fileUri) return

        val container = DiagnosticsContainer()
        val text = editor.text
        
        params.diagnostics.forEach { diagnostic ->
            try {
                container.addDiagnostic(diagnostic.asDiagnosticRegion(text))
            } catch (e: Exception) {
                log.warn("Invalid diagnostic range", e)
            }
        }
        editor.setDiagnostics(container)
    }
}
