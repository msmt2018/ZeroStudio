package com.zerostudio.idea.editor.lsp.sync

import com.zerostudio.idea.editor.lsp.bridge.ZeroLspConnector
import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.event.ContentChangeEvent
import kotlinx.coroutines.*
import org.eclipse.lsp4j.*
import java.util.concurrent.atomic.AtomicInteger

class LspDocumentSyncManager(
    private val editor: ZeroCodeEditorView,
    private val connectorProvider: () -> ZeroLspConnector?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val docVersion = AtomicInteger(1)
    private var syncJob: Job? = null

    fun notifyDidOpen(languageId: String) {
        scope.launch {
            val connector = connectorProvider() ?: return@launch
            val server = connector.getRemoteServer() ?: return@launch

            val text = withContext(Dispatchers.Main) { editor.text.toString() }
            val item = TextDocumentItem(connector.fileUri, languageId, docVersion.get(), text)
            server.textDocumentService.didOpen(DidOpenTextDocumentParams(item))
        }
    }

    fun attachIncrementalListener() {
        editor.subscribeAlways(ContentChangeEvent::class.java) { event ->
            val v = docVersion.incrementAndGet()
            val start = event.changeStart
            val end = event.changeEnd

            // 构造 LSP 增量范围 (Range)
            val range = Range(
                Position(start.line, start.column),
                Position(end.line, end.column)
            )

            val textToInsert = if (event.action == ContentChangeEvent.ACTION_INSERT) {
                event.changedText.toString()
            } else ""

            val changeEvent = TextDocumentContentChangeEvent(range, textToInsert)
            dispatchDidChangeDebounced(v, listOf(changeEvent))
        }
    }

    private fun dispatchDidChangeDebounced(version: Int, changes: List<TextDocumentContentChangeEvent>) {
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(150) // 150ms 聚合输入
            val connector = connectorProvider() ?: return@launch
            val server = connector.getRemoteServer() ?: return@launch

            val params = DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(connector.fileUri, version),
                changes
            )
            server.textDocumentService.didChange(params)
        }
    }

    fun notifyDidSave() {
        scope.launch {
            val connector = connectorProvider() ?: return@launch
            val server = connector.getRemoteServer() ?: return@launch
            val text = withContext(Dispatchers.Main) { editor.text.toString() }
            val params = DidSaveTextDocumentParams(TextDocumentIdentifier(connector.fileUri), text)
            server.textDocumentService.didSave(params)
        }
    }

    fun notifyDidClose() {
        scope.launch {
            val connector = connectorProvider() ?: return@launch
            val server = connector.getRemoteServer() ?: return@launch
            server.textDocumentService.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(connector.fileUri)))
        }
    }

    fun release() {
        syncJob?.cancel()
        scope.cancel()
    }
}