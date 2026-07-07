/*
 *  ZeroStudio IDE - Host ADRT (Android Debug Runtime)
 *
 *  HostSocksServer: SOCKS5 出口 (子项目 3 里的 Socks 子路径 + 子项目 5 InnetVm)。
 *
 *  跟 HostAttachAgent 的差别:
 *    - 不直接反向连 IDE
 *    - 而是在 host 进程内启动一个 SOCKS5 server, 把进来的 CONNECT 请求都转
 *      接到 `localabstract:jdwp` 自己的 JDWP socket
 *    - IDE 端用 Socks5Client 主动连这个 server
 *
 *  为什么有这层抽象:
 *    - Shizuku 13+ 把 `newProcess` / `transferFileDescriptor` 都设成 private,
 *      子项目 3 的 Socks 路径不能像传统 attach-agent 那样 push 进程
 *    - 改方案: 在 host 进程内启 SOCKS5 server, IDE 端走通用 SOCKS5 客户端
 *    - Server 在 host 进程内, 不会破坏安全边界 (走 Unix domain abstract)
 *
 *  实现: 极简单连接 SOCKS5, 跟子项目 5 的 Socks5Client 配对 (RFC 1928):
 *    - 无认证 (0x00)
 *    - ATYP=01 (IPv4) + ATYP=03 (domain)
 *    - CONNECT 命令
 *    - 成功后双向 forward
 */

package com.itsaky.androidide.zerostudio.ide.debugger.host

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 在 host 进程内起 SOCKS5 server, 把 CONNECT 转到 `localabstract:jdwp`。
 *
 * 用法 (由 Shizuku 注入 / root 启动):
 *   - startOnAbstract("jdwp-socks"): 监听 abstract socket "jdwp-socks"
 *   - startOnTcp(0): 监听 127.0.0.1:0 (随机端口)
 */
class HostSocksServer {

    private val tag = "HostSocksServer"
    @Volatile private var abstractServer: LocalServerSocket? = null
    @Volatile private var tcpServer: ServerSocket? = null
    @Volatile private var acceptThread: Thread? = null
    /** stop() 时置 true, accept loop 据此区分 "被 stop 关闭" (预期) 与 "异常退出" (需 log). */
    @Volatile private var stopped = false

    /**
     * 防止 [startOnAbstract] / [startOnTcp] 二次启动泄漏前一个 server socket;
     * 调 stop() 后可再次 start (running 重置为 false)。
     */
    private val running = AtomicBoolean(false)

    /**
     * 在 abstract namespace 监听 (Shizuku InHostPlugin 路径用)。
     *
     * 重复调用: 抛 [IllegalStateException] (避免泄漏前一个 server socket)。
     */
    @Throws(IOException::class)
    fun startOnAbstract(name: String): String {
        if (!running.compareAndSet(false, true)) {
            throw IllegalStateException("HostSocksServer already started; call stop() first")
        }
        stopped = false
        try {
            val lss = LocalServerSocket(name)
            abstractServer = lss
            log("listening on abstract=$name")
            acceptThread = thread(name = "HostSocksServer-abstract", isDaemon = false) {
                try {
                    while (!stopped) {
                        val client = lss.accept()
                        thread(name = "HostSocksServer-handler-${name}", isDaemon = true) {
                            try {
                                handleSocksClient(client.inputStream, client.outputStream)
                            } catch (t: Throwable) {
                                log("handler ended: ${t.message}")
                            } finally {
                                runCatching { client.close() }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    if (!stopped) log("abstract accept loop ended: ${t.message}")
                } finally {
                    running.set(false)
                }
            }
            return name
        } catch (t: Throwable) {
            running.set(false)
            throw t
        }
    }

    /**
     * 在 TCP 端口监听 (InnetVmSocks VM 内 SOCKS5 server 走这个)。
     * 返回 listen port, 失败抛 IOException。
     *
     * 重复调用: 抛 [IllegalStateException] (避免泄漏前一个 server socket)。
     */
    @Throws(IOException::class)
    fun startOnTcp(host: String = "127.0.0.1", port: Int = 0): Int {
        if (!running.compareAndSet(false, true)) {
            throw IllegalStateException("HostSocksServer already started; call stop() first")
        }
        stopped = false
        try {
            val addr: SocketAddress = java.net.InetSocketAddress(host, port)
            val ss = ServerSocket()
            ss.bind(addr)
            tcpServer = ss
            log("listening on tcp=${ss.localPort}")
            acceptThread = thread(name = "HostSocksServer-tcp", isDaemon = false) {
                try {
                    while (!ss.isClosed) {
                        val client = ss.accept()
                        val handlerTag = "tcp:$host:${ss.localPort}"
                        thread(name = "HostSocksServer-handler-$handlerTag", isDaemon = true) {
                            try {
                                handleSocksClient(client.getInputStream(), client.getOutputStream())
                            } catch (t: Throwable) {
                                log("handler ended: ${t.message}")
                            } finally {
                                runCatching { client.close() }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    if (!ss.isClosed) log("tcp accept loop ended: ${t.message}")
                } finally {
                    running.set(false)
                }
            }
            return ss.localPort
        } catch (t: Throwable) {
            running.set(false)
            throw t
        }
    }

    /**
     * 停止 server: close 两种 server socket, 等 accept thread 收尾 (带超时)。
     * handler thread 是 daemon, JVM exit 自动 kill; 这里不等 handler 收尾。
     */
    fun stop() {
        stopped = true
        runCatching { abstractServer?.close() }
        runCatching { tcpServer?.close() }
        abstractServer = null
        tcpServer = null
        // 等 accept thread 退出循环 (close server socket 触发 accept 抛 SocketException)
        acceptThread?.let { t ->
            t.join(STOP_JOIN_TIMEOUT_MS)
        }
        acceptThread = null
    }

    /** Whether server is currently listening (after start, before stop). */
    fun isRunning(): Boolean = running.get()

    private companion object {
        /** stop() 等 accept thread 收尾的超时 (ms)。 */
        const val STOP_JOIN_TIMEOUT_MS = 2_000L
    }

    // ---- 私有: SOCKS5 协议实现 ----

    /**
     * SOCKS5 协议 (RFC 1928) 单连接处理:
     *   1) client -> VER(1) NMETHODS(1) METHODS(1..255)
     *      选择 0x00 (no auth) 并 reply VER+METHOD
     *   2) client -> VER CMD RSV ATYP DST.ADDR DST.PORT
     *      我们无视 CMD / DST.ADDR / DST.PORT, 全部 CONNECT 到 localabstract:jdwp
     *   3) reply VER REP RSV ATYP(1=0.0.0.0) BND.ADDR(4 bytes 0) BND.PORT(2 bytes 0)
     *   4) 双向 forward
     *
     * 早期 EOF (greeting / request 阶段 client 直接断开) 视作正常 client 行为,
     * 静默退出不打 error log。SOCKS5 client 协议不合法 (ver/ATYP/CMD) 仍抛 IOException。
     */
    @Throws(IOException::class)
    private fun handleSocksClient(input: InputStream, output: OutputStream) {
        // 1) greeting
        val ver = readByteOrNull(input) ?: return
        if (ver != 0x05.toByte()) throw IOException("not SOCKS5: ver=$ver")
        val nMethods = readByteOrNull(input)?.toInt()?.and(0xff) ?: return
        if (nMethods < 0) throw IOException("invalid SOCKS5 NMETHODS=$nMethods")
        if (!readFullyOrNull(input, nMethods)) return
        output.write(byteArrayOf(0x05, 0x00))
        output.flush()

        // 2) request
        val reqVer = readByteOrNull(input) ?: return
        if (reqVer != 0x05.toByte()) throw IOException("not SOCKS5 request: ver=$reqVer")
        val cmd = readByteOrNull(input) ?: return
        val rsv = readByteOrNull(input) ?: return
        val atyp = readByteOrNull(input)?.toInt()?.and(0xff) ?: return
        // skip DST.ADDR + DST.PORT - 我们直接接到 jdwp, 不关心客户端想要的目标
        when (atyp) {
            0x01 -> { if (!readFullyOrNull(input, 4)) return }
            0x03 -> {
                val len = readByteOrNull(input)?.toInt()?.and(0xff) ?: return
                if (len < 0) throw IOException("invalid SOCKS5 domain length=$len")
                if (!readFullyOrNull(input, len)) return
            }
            0x04 -> { if (!readFullyOrNull(input, 16)) return }
            else -> throw IOException("unsupported SOCKS5 ATYP=$atyp")
        }
        if (!readFullyOrNull(input, 2)) return  // DST.PORT
        if (cmd != 0x01.toByte()) {
            // 仅支持 CONNECT
            output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))  // command not supported
            output.flush()
            throw IOException("unsupported SOCKS5 CMD=$cmd (only CONNECT=0x01 supported)")
        }
        // 3) reply success
        output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()

        // 4) 接 jdwp, 双向 forward (用 stream-based bridgeBytes overload)
        val jdwp = HostAttachAgent.openLocalAbstractJdwpSocket()
        try {
            HostAttachAgent.bridgeBytes(
                ideIn = input,
                ideOut = output,
                jdwpIn = jdwp.inputStream,
                jdwpOut = jdwp.outputStream,
            )
        } finally {
            runCatching { jdwp.close() }
        }
    }

    /**
     * 读 1 字节, EOF 时返 null (表示 client 正常断开, 不当 error)。
     * 其他 IOException 仍向上抛。
     */
    private fun readByteOrNull(input: InputStream): Byte? {
        val b = input.read()
        return if (b < 0) null else b.toByte()
    }

    /**
     * 读 [n] 字节, EOF 时返 false (表示 client 正常断开, 不当 error)。
     * 其他 IOException 仍向上抛。
     */
    private fun readFullyOrNull(input: InputStream, n: Int): Boolean {
        if (n == 0) return true
        var off = 0
        val buf = ByteArray(n)
        while (off < n) {
            val read = input.read(buf, off, n - off)
            if (read < 0) return false
            off += read
        }
        return true
    }

    private fun log(msg: String) = Log.i(tag, msg)
}
