package com.zerostudio.idea.editor.lsp

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageServer
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

enum class LspEngineStatus {
    DISCONNECTED,
    CONNECTING,
    INITIALIZED,
    RUNNING,
    ERROR
}

data class DiagnosticReport(
    val uri: String,
    val diagnostics: List<Diagnostic>
)

/**
 * 现代反应式 LSP 通信客户端核心
 */
class LspCoreClient(
    val serverIdentifier: String,
    val workspaceRootUri: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val _status = MutableStateFlow(LspEngineStatus.DISCONNECTED)
    val status: StateFlow<LspEngineStatus> = _status.asStateFlow()

    private val _diagnosticsFlow = MutableSharedFlow<DiagnosticReport>(extraBufferCapacity = 128)
    val diagnosticsFlow: SharedFlow<DiagnosticReport> = _diagnosticsFlow.asSharedFlow()

    private var languageServer: LanguageServer? = null
    private var clientLauncher: Launcher<LanguageServer>? = null

    /**
     * 连接本地 Socket 暴露的语言服务 (例如 127.0.0.1:2087)
     */
    fun connectViaSocket(host: String, port: Int) {
        scope.launch {
            _status.value = LspEngineStatus.CONNECTING
            try {
                val socket = Socket(host, port)
                startLspProtocolLoop(socket.getInputStream(), socket.getOutputStream())
            } catch (t: Throwable) {
                _status.value = LspEngineStatus.ERROR
            }
        }
    }

    /**
     * 连接本地命令行拉起的进程 (Stdin / Stdout 管道通信)
     */
    fun connectViaProcess(process: Process) {
        scope.launch {
            _status.value = LspEngineStatus.CONNECTING
            startLspProtocolLoop(process.inputStream, process.outputStream)
        }
    }

    private suspend fun startLspProtocolLoop(input: InputStream, output: OutputStream) = withContext(Dispatchers.IO) {
        val localClient = object : org.eclipse.lsp4j.services.LanguageClient {
            override fun telemetryEvent(`object`: Any?) {}
            override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
                _diagnosticsFlow.tryEmit(DiagnosticReport(diagnostics.uri, diagnostics.diagnostics))
            }
            override fun showMessage(messageParams: MessageParams) {}
            override fun showMessageRequest(requestParams: ShowMessageRequestParams) = null
            override fun logMessage(message: MessageParams) {}
        }

        val launcher = Launcher.createLauncher(localClient, LanguageServer::class.java, input, output)
        this@LspCoreClient.clientLauncher = launcher
        val server = launcher.remoteProxy
        this@LspCoreClient.languageServer = server

        launcher.startListening()

        // 发送 Initialize 请求
        val initParams = InitializeParams().apply {
            rootUri = workspaceRootUri
            capabilities = ClientCapabilities()
        }

        val initResult = server.initialize(initParams).get()
        server.initialized(InitializedParams())
        _status.value = LspEngineStatus.RUNNING
    }

    /**
     * 获取跳转定义
     */
    suspend fun getDefinitions(fileUri: String, line: Int, column: Int): List<Location> = withContext(Dispatchers.IO) {
        val server = languageServer ?: return@withContext emptyList()
        val params = DefinitionParams(
            TextDocumentIdentifier(fileUri),
            Position(line, column)
        )
        val result = server.textDocumentService.definition(params).get()
        return@withContext if (result.isLeft) {
            result.left
        } else {
            result.right.map { Location(it.targetUri, it.targetRange) }
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                languageServer?.shutdown()?.get()
                languageServer?.exit()
            }
            _status.value = LspEngineStatus.DISCONNECTED
            scope.cancel()
        }
    }
}