package com.zerostudio.idea.editor.lsp

import com.zerostudio.idea.editor.ui.view.ZeroCodeEditorView
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

enum class LspEngineStatus { DISCONNECTED, CONNECTING, RUNNING, ERROR }

data class DiagnosticReport(val uri: String, val diagnostics: List<Diagnostic>)

class LspCoreClient(
    val serverId: String,
    val workspaceRootUri: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val _status = MutableStateFlow(LspEngineStatus.DISCONNECTED)
    val status = _status.asStateFlow()

    private val _diagnosticsFlow = MutableSharedFlow<DiagnosticReport>(extraBufferCapacity = 64)
    val diagnosticsFlow = _diagnosticsFlow.asSharedFlow()

    private var server: LanguageServer? = null

    fun connectViaProcess(process: Process) {
        scope.launch {
            _status.value = LspEngineStatus.CONNECTING
            startProtocolLoop(process.inputStream, process.outputStream)
        }
    }

    fun connectViaSocket(host: String, port: Int) {
        scope.launch {
            _status.value = LspEngineStatus.CONNECTING
            val socket = Socket(host, port)
            startProtocolLoop(socket.getInputStream(), socket.getOutputStream())
        }
    }

    private suspend fun startProtocolLoop(input: InputStream, output: OutputStream) = withContext(Dispatchers.IO) {
        val client = object : LanguageClient {
            override fun telemetryEvent(`object`: Any?) {}
            override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
                _diagnosticsFlow.tryEmit(DiagnosticReport(diagnostics.uri, diagnostics.diagnostics))
            }
            override fun showMessage(messageParams: MessageParams) {}
            override fun showMessageRequest(requestParams: ShowMessageRequestParams) = null
            override fun logMessage(message: MessageParams) {}
        }

        val launcher = Launcher.createLauncher(client, LanguageServer::class.java, input, output)
        this@LspCoreClient.server = launcher.remoteProxy
        launcher.startListening()

        val initParams = InitializeParams().apply {
            rootUri = workspaceRootUri
            capabilities = ClientCapabilities()
        }
        server?.initialize(initParams)?.get()
        server?.initialized(InitializedParams())
        _status.value = LspEngineStatus.RUNNING
    }

    fun getLanguageServer(): LanguageServer? = server

    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                server?.shutdown()?.get()
                server?.exit()
            }
            _status.value = LspEngineStatus.DISCONNECTED
            scope.cancel()
        }
    }
}

class ZeroLspEditorBridge(
    private val editor: ZeroCodeEditorView,
    private val client: LspCoreClient,
    private val fileUri: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private val diagnosticContainer = DiagnosticsContainer()

    fun attach() {
        scope.launch {
            client.diagnosticsFlow.collect { report ->
                if (report.uri == fileUri) {
                    applyDiagnostics(report.diagnostics)
                }
            }
        }
    }

    private fun applyDiagnostics(diagnostics: List<Diagnostic>) {
        diagnosticContainer.reset()
        val indexer = editor.text.indexer

        for (d in diagnostics) {
            val startLine = d.range.start.line
            val startCol = d.range.start.character
            val endLine = d.range.end.line
            val endCol = d.range.end.character

            val startIndex = indexer.getCharIndex(startLine, startCol)
            val endIndex = indexer.getCharIndex(endLine, endCol)

            val severity = when (d.severity) {
                DiagnosticSeverity.Error -> DiagnosticRegion.SEVERITY_ERROR
                DiagnosticSeverity.Warning -> DiagnosticRegion.SEVERITY_WARNING
                DiagnosticSeverity.Information -> DiagnosticRegion.SEVERITY_TYPO
                else -> DiagnosticRegion.SEVERITY_NONE
            }

            diagnosticContainer.addDiagnostic(
                DiagnosticRegion(startIndex, endIndex, severity, 0, DiagnosticDetail(d.message))
            )
        }
        editor.diagnostics = diagnosticContainer
        editor.invalidateDiagnostics()
    }

    fun detach() {
        scope.cancel()
    }
}