/*
 * This file is part of ZeroStudio.
 *
 * ZeroStudio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ZeroStudio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ZeroStudio. If not, see <https://www.gnu.org/licenses/>.
 */

package com.zerostudio.webpreview.devtools

import android.net.LocalSocket
import android.net.LocalSocketAddress
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Unix domain socket → localhost TCP 桥接器。
 *
 * <p>
 * <b>背景</b>: 调用 `WebView.setWebContentsDebuggingEnabled(true)` 后, WebView 会在
 * Linux abstract namespace 暴露 CDP (Chrome DevTools Protocol) unix socket:
 * `@webview_devtools_remote_<pid>`。然而 Chrome DevTools 前端只能通过 HTTP/WS 走 TCP,
 * 不能直接连 unix socket。本类把 abstract unix socket 桥接到 `localhost:<tcpPort>`,
 * 让 HTTP/WS 可达。
 *
 * <p>
 * <b>实现</b>:
 * <ul>
 *   <li>`ServerSocket(tcpPort).accept()` 阻塞接收 TCP 连接</li>
 *   <li>每条 TCP 连接 → 建一个 [LocalSocket] 连到 abstract namespace socket</li>
 *   <li>两个线程 pump 双向字节流 (TCP→Local, Local→TCP)</li>
 * </ul>
 *
 * <p>
 * <b>abstract namespace</b>: socket 名以 `@` 开头 (如 `@webview_devtools_remote_1234`),
 * 属于 Linux abstract namespace, 不占文件系统, 也不受 SELinux 文件路径限制。
 *
 * @param abstractSocketName abstract socket 名 (**不含** `@` 前缀, 直接传给
 *        [LocalSocketAddress] with [LocalSocketAddress.Namespace.ABSTRACT])
 * @param tcpPort 转发到的 localhost TCP 端口 (如 9222)
 */
class LocalSocketForwarder(
    private val abstractSocketName: String,
    private val tcpPort: Int,
) {
    private val log = LoggerFactory.getLogger("LocalSocketForwarder")

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var running = false
    private val activePumps = mutableListOf<Thread>()

    /**
     * 启动转发器。
     *
     * <p>
     * 在新线程中 accept TCP 连接, 每个连接 spawn 两个 pump 线程。
     * 立即返回, 不阻塞调用方。
     *
     * @throws IOException 如果 ServerSocket 端口已被占用
     */
    fun start() {
        if (running) return
        val server = ServerSocket(tcpPort)
        server.soTimeout = 0  // 永远阻塞 accept
        serverSocket = server
        running = true
        log.info("Forwarder started: @{} → localhost:{}", abstractSocketName, tcpPort)

        thread(name = "LocalSocketForwarder-accept-${tcpPort}", isDaemon = true) {
            while (running) {
                val tcpClient = try {
                    server.accept()
                } catch (e: IOException) {
                    if (running) log.warn("Accept failed on {}", tcpPort, e)
                    break
                }
                log.debug("New TCP connection, forwarding to @{}", abstractSocketName)
                handleConnection(tcpClient)
            }
        }
    }

    /** 停止转发器, 关闭所有 socket 和 pump 线程 */
    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: IOException) {}
        serverSocket = null
        synchronized(activePumps) {
            activePumps.forEach { it.interrupt() }
            activePumps.clear()
        }
        log.info("Forwarder stopped: @{} → localhost:{}", abstractSocketName, tcpPort)
    }

    /** 当前是否运行中 */
    fun isRunning(): Boolean = running

    // ── 内部 ───────────────────────────────────────────────

    private fun handleConnection(tcpClient: Socket) {
        val local = LocalSocket()
        try {
            local.connect(LocalSocketAddress(abstractSocketName, LocalSocketAddress.Namespace.ABSTRACT))
        } catch (e: IOException) {
            log.error("Failed to connect to abstract socket @{}", abstractSocketName, e)
            try { tcpClient.close() } catch (_: IOException) {}
            return
        }

        val tcpIn = tcpClient.getInputStream()
        val tcpOut = tcpClient.getOutputStream()
        val localIn = local.getInputStream()
        val localOut = local.getOutputStream()

        // TCP → Local
        val t1 = thread(name = "pump-tcp→local", isDaemon = true) {
            pump(tcpIn, localOut)
            cleanupQuietly(tcpClient, local)
        }
        // Local → TCP
        val t2 = thread(name = "pump-local→tcp", isDaemon = true) {
            pump(localIn, tcpOut)
            cleanupQuietly(tcpClient, local)
        }
        synchronized(activePumps) {
            activePumps.add(t1)
            activePumps.add(t2)
        }
    }

    /** 从 [from] 读字节, 写到 [to], 直到流关闭或被中断 */
    private fun pump(from: java.io.InputStream, to: java.io.OutputStream) {
        val buf = ByteArray(BUF_SIZE)
        try {
            while (running) {
                val n = from.read(buf)
                if (n < 0) break
                if (n == 0) continue
                to.write(buf, 0, n)
                to.flush()
            }
        } catch (_: IOException) {
            // 流关闭, 正常退出
        } catch (_: InterruptedException) {
            // stop() 中断
        } finally {
            try { to.close() } catch (_: IOException) {}
        }
    }

    private fun cleanupQuietly(vararg closeables: AutoCloseable) {
        closeables.forEach { c ->
            try { c.close() } catch (_: Exception) {}
        }
    }

    private companion object {
        private const val BUF_SIZE = 8 * 1024  // 8KB pump 缓冲区
    }
}
