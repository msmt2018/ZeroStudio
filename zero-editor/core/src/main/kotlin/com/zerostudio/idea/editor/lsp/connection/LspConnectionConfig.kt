package com.zerostudio.idea.editor.lsp.connection

import android.os.Build
import com.zerostudio.idea.editor.lsp.model.LspLogEntry
import com.zerostudio.idea.editor.lsp.model.LspServerInstance
import com.zerostudio.idea.editor.lsp.model.MessageSource
import com.zerostudio.idea.editor.lsp.protocol.LoggingInputStream
import com.zerostudio.idea.editor.lsp.protocol.LoggingOutputStream
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import org.eclipse.lsp4j.MessageType
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

sealed interface LspConnectionConfig {
    fun createProvider(instance: LspServerInstance): StreamConnectionProvider

    data class Socket(val host: String = "localhost", val port: Int) : LspConnectionConfig {
        override fun createProvider(instance: LspServerInstance) = object : StreamConnectionProvider {
            private var socket: java.net.Socket? = null
            private var logIn: InputStream? = null
            private var logOut: OutputStream? = null

            override fun getInputStream(): InputStream = logIn ?: error("Socket closed")
            override fun getOutputStream(): OutputStream = logOut ?: error("Socket closed")

            override fun start() {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5000)
                socket = s
                logIn = LoggingInputStream(s.getInputStream()) { json ->
                    instance.addLog(LspLogEntry(MessageSource.RPC, null, "→ $json"))
                }
                logOut = LoggingOutputStream(s.getOutputStream()) { json ->
                    instance.addLog(LspLogEntry(MessageSource.RPC, null, "← $json"))
                }
            }

            override fun close() {
                runCatching { socket?.close() }
                socket = null
            }
        }
    }

    data class Process(val command: Array<String>) : LspConnectionConfig {
        override fun createProvider(instance: LspServerInstance) = object : StreamConnectionProvider {
            private var process: java.lang.Process? = null
            private var logIn: InputStream? = null
            private var logOut: OutputStream? = null

            override fun getInputStream(): InputStream = logIn ?: error("Process closed")
            override fun getOutputStream(): OutputStream = logOut ?: error("Process closed")

            override fun start() {
                val pb = ProcessBuilder(*command).apply {
                    if (instance.projectRoot.exists()) directory(instance.projectRoot)
                }
                val p = pb.start()
                process = p
                logIn = LoggingInputStream(p.inputStream) { json ->
                    instance.addLog(LspLogEntry(MessageSource.RPC, null, "→ $json"))
                }
                logOut = LoggingOutputStream(p.outputStream) { json ->
                    instance.addLog(LspLogEntry(MessageSource.RPC, null, "← $json"))
                }
            }

            override fun close() {
                process?.destroy()
                process = null
            }
        }
    }

    data class AndroidProcess(
        val command: Array<String>,
        val localBinDir: File,
        val localLibDir: File
    ) : LspConnectionConfig {
        override fun createProvider(instance: LspServerInstance) = object : StreamConnectionProvider {
            private var process: java.lang.Process? = null
            private var logIn: InputStream? = null
            private var logOut: OutputStream? = null

            override fun getInputStream(): InputStream = logIn ?: error("Process closed")
            override fun getOutputStream(): OutputStream = logOut ?: error("Process closed")

            override fun start() {
                val pb = ProcessBuilder(*command).apply {
                    if (instance.projectRoot.exists()) directory(instance.projectRoot)
                }
                // 注入完整的 Android ART 与 Linux 运行时环境变数
                pb.environment().apply {
                    put("ANDROID_ROOT", System.getenv("ANDROID_ROOT").orEmpty())
                    put("ANDROID_DATA", System.getenv("ANDROID_DATA").orEmpty())
                    put("PATH", "${System.getenv("PATH")}:${localBinDir.absolutePath}")
                    put("LD_LIBRARY_PATH", localLibDir.absolutePath)
                }
                val p = pb.start()
                process = p

                logIn = LoggingInputStream(p.inputStream) { json ->
                    instance.addLog(LspLogEntry(MessageSource.RPC, null, "→ $json"))
                }
                logOut = LoggingOutputStream(p.outputStream) { json ->
                    instance.addLog(LspLogEntry(MessageSource.RPC, null, "← $json"))
                }
            }

            override fun close() {
                process?.destroy()
                process = null
            }
        }
    }
}