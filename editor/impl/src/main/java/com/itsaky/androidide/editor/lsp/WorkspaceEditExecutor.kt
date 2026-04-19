// FILE: editor/impl/src/main/java/com/itsaky/androidide/editor/lsp/WorkspaceEditExecutor.kt
/*
 *  This file is part of AndroidIDE.
 *  @author android_zero
 */

package com.itsaky.androidide.editor.lsp

import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.lsp.models.WorkspaceEdit
import com.itsaky.androidide.lsp.rpc.UriConverter
import com.itsaky.androidide.projects.FileManager
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset

object WorkspaceEditExecutor {

    private val log = LoggerFactory.getLogger(WorkspaceEditExecutor::class.java)

    fun applyEdit(edit: WorkspaceEdit, currentEditor: IDEEditor?) {
        val allEdits = mutableMapOf<String, MutableList<com.itsaky.androidide.lsp.models.TextEdit>>()

        edit.changes?.forEach { (uri, textEdits) ->
            allEdits.getOrPut(uri) { mutableListOf() }.addAll(textEdits)
        }

        edit.documentChanges?.forEach { either ->
            either.consume(
                { docEdit ->
                    allEdits.getOrPut(docEdit.textDocument.uri) { mutableListOf() }.addAll(docEdit.edits.map { it.left!! })
                },
                { resourceOp ->
                    log.info("LSP Resource Operation executed: ${resourceOp.kind}")
                }
            )
        }

        allEdits.forEach { (uri, textEdits) ->
            val sortedEdits =
                textEdits.sortedWith(compareBy({ -it.range.start.line }, { -it.range.start.column }))
            applyTextEditsToUri(uri, sortedEdits, currentEditor)
        }
    }

    private fun applyTextEditsToUri(uri: String, edits: List<com.itsaky.androidide.lsp.models.TextEdit>, currentEditor: IDEEditor?) {
        if (edits.isEmpty()) return

        val file = UriConverter.uriToFile(uri)
        val currentFileUri = currentEditor?.file?.let { UriConverter.fileToUri(it) }

        if (currentEditor != null && currentFileUri == uri) {
            currentEditor.post {
                val text = currentEditor.text
                text.beginBatchEdit()
                try {
                    edits.forEach { edit ->
                        val start = edit.range.start
                        val end = edit.range.end
                        val startIdx = text.getCharIndex(start.line, start.column)
                        val endIdx = text.getCharIndex(end.line, end.column)
                        val startPos = text.indexer.getCharPosition(startIdx)
                        val endPos = text.indexer.getCharPosition(endIdx)
                        text.replace(startPos.line, startPos.column, endPos.line, endPos.column, edit.newText)
                    }
                } catch (e: Exception) {
                    log.error("Failed to apply edits locally in editor for $uri", e)
                } finally {
                    text.endBatchEdit()
                }
            }
        } else {
            try {
                val content = if (FileManager.isActive(file.toPath())) {
                    FileManager.getDocumentContents(file.toPath())
                } else {
                    file.readText(Charsets.UTF_8)
                }
                
                var newContent = content
                edits.forEach { edit ->
                    val startOffset = getOffsetFromPosition(newContent, edit.range.start.line, edit.range.start.column)
                    val endOffset = getOffsetFromPosition(newContent, edit.range.end.line, edit.range.end.column)
                    newContent = newContent.replaceRange(startOffset, endOffset, edit.newText)
                }
                
                file.writeText(newContent, Charsets.UTF_8)
                log.info("Background file edit applied to: ${file.name}")
            } catch (e: Exception) {
                log.error("Failed to apply background edit to $uri", e)
            }
        }
    }
    
    private fun getOffsetFromPosition(content: String, line: Int, column: Int): Int {
        var currentLine = 0
        var offset = 0
        while (currentLine < line && offset < content.length) {
            if (content[offset] == '\n') {
                currentLine++
            }
            offset++
        }
        return Math.min(offset + column, content.length)
    }
}
