// FILE: editor/impl/src/main/java/com/itsaky/androidide/editor/lsp/LspCommandExecutor.kt
/*
 *  This file is part of AndroidIDE.
 *  @author android_zero
 */

package com.itsaky.androidide.editor.lsp

import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.models.Command
import org.slf4j.LoggerFactory

class LspCommandExecutor(private val server: ILanguageServer) {
    private val log = LoggerFactory.getLogger(LspCommandExecutor::class.java)

    fun execute(command: Command) {
        try {
            server.executeCommand(command)
            log.info("LSP Command executed: ${command.title}")
        } catch (e: Exception) {
            log.error("Failed to execute workspace command", e)
        }
    }
}
