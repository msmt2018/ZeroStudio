/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.runtime

import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * v2.5 P0 P3-FE-05: 远程预览服务器.
 *
 * ## 协议
 *
 * 每个连接 = 一次请求 / 响应:
 *
 * 1. Client → Server: 1 字节命令类型 (CMD_RENDER = 0x01)
 * 2. Client → Server: 4 字节 big-endian 长度 N, 跟随 N 字节 UTF-8 JSON
 *    负载字段:
 *    ```
 *    {
 *      "function": "com.example.MyComposable",
 *      "profileId": "phone-medium",
 *      "theme": "light",
 *    }
 *    ```
 * 3. Server → Client: 4 字节状态码 (0=OK, 1=ERR), 4 字节长度 M, M 字节响应
 *    - 0x00 OK: M 字节 PNG
 *    - 0x01 ERR: M 字节 UTF-8 错误信息
 * 4. 连接关闭.
 *
 * ## 线程模型
 *
 * 主线程 `accept()` 循环, 每次连接交给 worker thread 处理. `close()` 关闭 server
 * socket 后 `accept()` 抛异常退出循环.
 *
 * @param handler 命令处理回调, 由 IDE 端注入实际渲染逻辑 (例如: 调用 ComposableRenderer
 *                渲染指定 function, 返回 PNG bytes).
 */
class PreviewServer(
    private val port: Int = DEFAULT_PORT,
    private val handler: PreviewHandler = NoopPreviewHandler,
) {

    private val LOG = LoggerFactory.getLogger(PreviewServer::class.java)

    /** 单命令处理回调. */
    fun interface PreviewHandler {
        fun handle(command: PreviewCommand): PreviewResponse
    }

    /** 客户端请求. */
    data class PreviewCommand(
        val functionFqn: String,
        val profileId: String,
        val theme: String,
    )

    /** 服务端响应. */
    sealed class PreviewResponse {
        data class Ok(val png: ByteArray) : PreviewResponse()
        data class Err(val message: String) : PreviewResponse()
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "preview-server-worker").apply { isDaemon = true }
    }
    private val acceptCount = AtomicLong(0L)

    /**
     * 启动服务. 幂等: 已运行时返回 false.
     *
     * @return true 成功绑定端口; false 已运行或绑定失败
     */
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) {
            LOG.warn("PreviewServer already running")
            return false
        }
        return try {
            val sock = ServerSocket(port, 50, InetAddress.getLoopbackAddress())
            this.serverSocket = sock
            LOG.info("PreviewServer listening on 127.0.0.1:{}", sock.localPort)
            executor.submit { acceptLoop(sock) }
            true
        } catch (e: Throwable) {
            running.set(false)
            LOG.error("PreviewServer failed to start on port {}: {}", port, e.message)
            false
        }
    }

    fun isRunning(): Boolean = running.get()

    fun acceptCount(): Long = acceptCount.get()

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching {
            // 触发 accept 抛异常退出
            java.net.Socket().use { it.connect(InetAddress.getLoopbackAddress(), port) }
        }
        executor.shutdownNow()
        LOG.info("PreviewServer stopped")
    }

    private fun acceptLoop(sock: ServerSocket) {
        while (running.get()) {
            val client = try {
                sock.accept()
            } catch (e: Throwable) {
                if (running.get()) LOG.error("accept failed: {}", e.message)
                break
            }
            acceptCount.incrementAndGet()
            executor.submit { handleClient(client) }
        }
        runCatching { sock.close() }
    }

    private fun handleClient(client: Socket) {
        client.use { c ->
            try {
                val input = DataInputStream(c.getInputStream())
                val output = DataOutputStream(c.getOutputStream())
                val cmd = input.readByte().toInt()
                if (cmd != CMD_RENDER) {
                    sendErr(output, "unsupported command: $cmd")
                    return
                }
                val len = input.readInt()
                if (len < 0 || len > MAX_PAYLOAD) {
                    sendErr(output, "invalid payload length: $len")
                    return
                }
                val payload = ByteArray(len)
                input.readFully(payload)
                val parsed = parseCommand(String(payload, Charsets.UTF_8))
                    ?: run {
                        sendErr(output, "malformed command json")
                        return
                    }

                val response = try {
                    handler.handle(parsed)
                } catch (e: Throwable) {
                    PreviewResponse.Err("handler exception: ${e.message}")
                }

                when (response) {
                    is PreviewResponse.Ok -> sendOk(output, response.png)
                    is PreviewResponse.Err -> sendErr(output, response.message)
                }
            } catch (e: Throwable) {
                LOG.warn("client handler error: {}", e.message)
            }
        }
    }

    private fun sendOk(output: DataOutputStream, png: ByteArray) {
        output.writeInt(STATUS_OK)
        output.writeInt(png.size)
        output.write(png)
        output.flush()
    }

    private fun sendErr(output: DataOutputStream, msg: String) {
        val bytes = msg.toByteArray(Charsets.UTF_8)
        output.writeInt(STATUS_ERR)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    /** 极简 JSON 解析, 仅支持 3 个字符串字段. 不引入 gson 依赖. */
    internal fun parseCommand(json: String): PreviewCommand? {
        val function = extractString(json, "function") ?: return null
        val profileId = extractString(json, "profileId") ?: "phone-medium"
        val theme = extractString(json, "theme") ?: "light"
        return PreviewCommand(function, profileId, theme)
    }

    private fun extractString(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
        val match = pattern.find(json) ?: return null
        return match.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
    }

    /** 默认 noop handler, 用于测试 / 启动时占位. */
    object NoopPreviewHandler : PreviewHandler {
        override fun handle(command: PreviewCommand): PreviewResponse =
            PreviewResponse.Err("no handler installed: ${command.functionFqn}")
    }

    companion object {
        const val DEFAULT_PORT: Int = 9876
        const val CMD_RENDER: Int = 0x01
        const val STATUS_OK: Int = 0x00
        const val STATUS_ERR: Int = 0x01
        const val MAX_PAYLOAD: Int = 4 * 1024 * 1024  // 4 MB JSON 上限
    }
}
