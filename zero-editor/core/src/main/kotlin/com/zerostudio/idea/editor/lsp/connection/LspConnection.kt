package com.zerostudio.idea.editor.lsp.connection

import com.zerostudio.idea.editor.lsp.protocol.LoggingInputStream
import com.zerostudio.idea.editor.lsp.protocol.LoggingOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

interface LspConnection {
    val inputStream: InputStream
    val outputStream: OutputStream
    val isClosed: Boolean
    fun start()
    fun close()
}

class SocketLspConnection(
    private val host: String = "localhost",
    private val port: Int,
    private val onLogFrame: ((String, Boolean) -> Unit)? = null
) : LspConnection {
    private var socket: Socket? = null
    private var loggingIn: InputStream? = null
    private var loggingOut: OutputStream? = null

    override val inputStream: InputStream get() = loggingIn ?: error("Socket not connected")
    override val outputStream: OutputStream get() = loggingOut ?: error("Socket not connected")
    override val isClosed: Boolean get() = socket == null || socket?.isClosed == true

    override fun start() {
        if (socket != null) return
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 5000)
        s.tcpNoDelay = true
        socket = s

        loggingIn = LoggingInputStream(s.getInputStream()) { json -> onLogFrame?.invoke(json, true) }
        loggingOut = LoggingOutputStream(s.getOutputStream()) { json -> onLogFrame?.invoke(json, false) }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        loggingIn = null
        loggingOut = null
    }
}

class ProcessLspConnection(
    private val command: Array<String>,
    private val workingDir: File? = null,
    private val environment: Map<String, String> = emptyMap(),
    private val onLogFrame: ((String, Boolean) -> Unit)? = null
) : LspConnection {
    private var process: Process? = null
    private var loggingIn: InputStream? = null
    private var loggingOut: OutputStream? = null

    override val inputStream: InputStream get() = loggingIn ?: error("Process not started")
    override val outputStream: OutputStream get() = loggingOut ?: error("Process not started")
    override val isClosed: Boolean get() = process == null || process?.isAlive == false

    override fun start() {
        if (process != null) return
        val pb = ProcessBuilder(*command)
        workingDir?.let { if (it.exists()) pb.directory(it) }
        pb.environment().putAll(environment)

        val p = pb.start()
        process = p

        loggingIn = LoggingInputStream(p.inputStream) { json -> onLogFrame?.invoke(json, true) }
        loggingOut = LoggingOutputStream(p.outputStream) { json -> onLogFrame?.invoke(json, false) }
    }

    override fun close() {
        process?.destroy()
        process = null
        loggingIn = null
        loggingOut = null
    }
}