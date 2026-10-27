package com.zerostudio.idea.editor.lsp.model

import com.zerostudio.idea.editor.lsp.connection.LspConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import java.io.File

enum class LspConnectionStatus {
    NOT_RUNNING,
    STARTING,
    RUNNING,
    STOPPING,
    RESTARTING,
    CRASHED,
    TIMEOUT
}

enum class MessageSource { RPC, LSP, CLIENT, RUNTIME }

data class LspLogEntry(
    val source: MessageSource,
    val type: MessageType?,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class LspServerInstance(
    val id: String,
    val serverName: String,
    val projectRoot: File,
    private val connectionProvider: () -> LspConnection
) {
    private val _status = MutableStateFlow(LspConnectionStatus.NOT_RUNNING)
    val status = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<LspLogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    var activeConnection: LspConnection? = null
        private set

    fun addLog(source: MessageSource, type: MessageType?, message: String) {
        val entry = LspLogEntry(source, type, message)
        _logs.value = (_logs.value + entry).takeLast(2000)
    }

    fun start() {
        if (_status.value == LspConnectionStatus.RUNNING) return
        _status.value = LspConnectionStatus.STARTING
        try {
            val conn = connectionProvider()
            conn.start()
            activeConnection = conn
            _status.value = LspConnectionStatus.RUNNING
            addLog(MessageSource.CLIENT, MessageType.Info, "LSP server connected: $serverName")
        } catch (e: Exception) {
            _status.value = LspConnectionStatus.CRASHED
            addLog(MessageSource.CLIENT, MessageType.Error, "LSP failed to start: ${e.message}")
        }
    }

    fun stop() {
        _status.value = LspConnectionStatus.STOPPING
        runCatching { activeConnection?.close() }
        activeConnection = null
        _status.value = LspConnectionStatus.NOT_RUNNING
        addLog(MessageSource.CLIENT, MessageType.Info, "LSP server stopped")
    }

    fun restart() {
        stop()
        start()
    }
}