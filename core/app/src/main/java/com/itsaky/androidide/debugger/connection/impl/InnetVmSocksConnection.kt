/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  InnetVmSocksConnection: 虚拟机 (光速虚拟机/VMOS/虚拟大师) SOCKS5 代理
 *  方案 (子项目 5)。
 *
 *  流程:
 *    1. resolve: 探测 SOCKS5 server 可达 (settings.innetSocks.host/port)
 *    2. connect: TCP socket connect 到 SOCKS5 server
 *    3. attach: 走 SOCKS5 协议 (no-auth) 握手, 转发到 host JDWP 端口
 *
 *  关键: 跟 Shizuku Socks 路径的区别:
 *    - InnetVmSocks 不需要 Shizuku 服务, SOCKS5 server 由虚拟机自带
 *    - InnetVmSocks 走配置里 hardcoded 的 host:port (用户在偏好里填)
 *    - InnetVmSocks 不需要探测/请求 Shizuku 权限
 *
 *  当前实现: 走通用 Socks5Client 协议, 不依赖任何 host runtime。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.AttachInfo
import com.itsaky.androidide.debugger.connection.BaseDebugConnection
import com.itsaky.androidide.debugger.connection.ConnectionCapability
import com.itsaky.androidide.debugger.connection.ConnectionError
import com.itsaky.androidide.debugger.connection.ConnectionRetryPolicy
import com.itsaky.androidide.debugger.connection.ConnectionState
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ResolveInfo
import com.itsaky.androidide.debugger.connection.aidl.AidlJdwpProtocol
import com.itsaky.androidide.debugger.connection.socks5.Socks5Client
import com.itsaky.androidide.utils.ILogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class InnetVmSocksConnection(
    target: DebugTarget,
    val settings: DebugConnectionSettings,
    private val socksClient: Socks5Client = Socks5Client(),
    private val retryPolicy: ConnectionRetryPolicy = ConnectionRetryPolicy(
        maxAttempts = settings.retryMaxAttempts,
        initialDelayMs = settings.retryInitialDelayMs,
    ),
) : BaseDebugConnection(ConnectionType.InnetVmSocks, target) {

    private val log = ILogger.ROOT

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.CanExposeSocks,
    )

    // ---- 运行时状态 ----
    @Volatile private var socket: Socket? = null
    @Volatile private var proxyAddr: InetSocketAddress? = null
    private val sessionIdGenerator = AtomicLong(System.currentTimeMillis())
    private val incoming = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)

    // ---- resolve: 探测 SOCKS5 server 可达 ----

    override suspend fun resolve(): Result<ResolveInfo> {
        val cfg = settings.innetSocks
        if (cfg.socksHost.isBlank() || cfg.socksPort <= 0) {
            transitionTo(ConnectionState.Closed(ConnectionError.PortResolveFailed))
            return Result.failure(IOException("InnetVmSocks host/port not configured"))
        }
        val addr = InetSocketAddress(cfg.socksHost, cfg.socksPort)
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                // 尝试一次 TCP connect, 失败抛 IOException
                val probe = Socket()
                try {
                    probe.connect(addr, cfg.connectTimeoutMs.toInt())
                } finally {
                    runCatching { probe.close() }
                }
            }.onFailure { log.debug("resolve: probe {} failed: {}", addr, it.message) }
        }
        return attempt.onSuccess {
            proxyAddr = addr
            transitionTo(ConnectionState.Connecting)
            Result.success(
                ResolveInfo(
                    transportKind = "socks5",
                    endpoint = "socks5://${cfg.socksHost}:${cfg.socksPort}",
                    requiresHostRunning = false,
                )
            )
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapConnectError(t)))
        }
    }

    // ---- connect: 不做事, 留到 attach ----

    override suspend fun connect(): Result<Unit> {
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                // 留空: attach() 阶段才真发 SOCKS5 握手
            }
        }
        return attempt.onSuccess {
            transitionTo(ConnectionState.Handshaking)
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapConnectError(t)))
        }
    }

    // ---- attach: SOCKS5 握手 + JDWP 握手 + VM.Version ----

    override suspend fun attach(): Result<AttachInfo> {
        val addr = proxyAddr
            ?: return Result.failure(IllegalStateException("attach() before resolve()"))
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                // 1) SOCKS5 握手
                //    Socks5Client 现在允许 targetPort = 0 (RFC 1928 "server-side routing"):
                //    InnetVmSocks 路径下 SOCKS5 server (虚拟机自带) 固定转发到 host:jdwp,
                //    client 不需要知道具体端口, 写 0x0000 让 server 自行决定。
                //    (之前 require 1..65535 把这条路径直接挡在 IllegalArgumentException)
                val sock = socksClient.connect(
                    proxyAddr = addr,
                    targetHost = "127.0.0.1",  // SOCKS5 server 自己转发到 host JDWP
                    targetPort = 0,            // 0 = SOCKS5 server-side routing
                    connectTimeoutMs = settings.innetSocks.connectTimeoutMs.toInt(),
                )
                // 2) 走 JDWP 握手 + VM.Version
                val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(
                    socket = sock,
                    commandId = (sessionIdGenerator.incrementAndGet() and 0x7fffffff).toInt(),
                )
                socket = sock
                AttachInfo(
                    pid = 0,
                    jdwpSessionId = sessionIdGenerator.get(),
                    jdwpDescription = "${info.description} (${info.vmName} ${info.vmVersion}, jdwp ${info.jdwpVersion})",
                )
            }.onFailure { log.warn("attach attempt failed: {}", it.message) }
        }
        // 失败: 走 mapAttachError
        attempt.exceptionOrNull()?.let { t ->
            transitionTo(ConnectionState.Closed(mapAttachError(t)))
            return Result.failure(t)
        }
        // 成功但 post-condition 失败: 走 finishAttach (ok=false 分支)
        val info = attempt.getOrNull()!!
        val sock = socket
        return finishAttach(
            info = info,
            ok = sock != null,
            failureMsg = "attach returned but socket is missing",
            // Phase 12m: 移除默认启 read loop (理由同 AdbForwardConnection)。
            onAttached = { /* JdwpClient 接管, 不启 read loop */ },
        )
    }

    // ---- detach / 字节流 / 钩子 / 释放 ----

    override suspend fun detach() {
        val sock = socket
        socket = null
        if (sock != null) {
            runCatching {
                val out = sock.getOutputStream()
                val cmd = AidlJdwpProtocol.buildVmDisposeCommand(0)
                synchronized(out) {
                    out.write(cmd)
                    out.flush()
                }
            }.onFailure { log.debug("detach: VM.Dispose failed: {}", it.message) }
            runCatching { sock.close() }
        }
        transitionTo(ConnectionState.Closed(null))
    }

    override suspend fun sendJdwp(bytes: ByteArray) {
        val sock = socket ?: error("not attached")
        check(currentState() is ConnectionState.Attached) {
            "sendJdwp requires Attached state, was ${currentState()}"
        }
        withContext(Dispatchers.IO) {
            sock.getOutputStream().apply { write(bytes); flush() }
        }
    }

    override fun receiveJdwp(): Flow<ByteArray> = incoming.asSharedFlow()

    override fun attachedSocket(): Socket {
        val sock = socket ?: error("not attached")
        return sock
    }

    override fun release() {
        runCatching { socket?.close() }
        socket = null
        super.release()
    }

    // ---- 私有 ----

    private fun startReadLoop(sock: Socket) {
        Thread({
            try {
                val input = sock.getInputStream()
                val buf = ByteArray(8192)
                while (!sock.isClosed) {
                    val n = try { input.read(buf) } catch (e: IOException) { -1 }
                    if (n <= 0) break
                    val chunk = ByteArray(n)
                    System.arraycopy(buf, 0, chunk, 0, n)
                    incoming.tryEmit(chunk)
                }
            } catch (t: Throwable) {
                log.debug("InnetVmSocksConnection: read loop ended: {}", t.message)
            }
        }, "InnetVmSocksConnection-read").apply { isDaemon = true; start() }
    }

    private fun mapConnectError(t: Throwable): ConnectionError = when (t) {
        is IOException -> ConnectionError.IoFailure(t)
        else -> ConnectionError.Unknown(t)
    }

    private fun mapAttachError(t: Throwable): ConnectionError {
        val msg = t.message.orEmpty()
        return when {
            msg.contains("Bad handshake", ignoreCase = true) -> ConnectionError.JdwpHandshakeFailed
            msg.contains("SOCKS5 CONNECT failed", ignoreCase = true) -> ConnectionError.NetworkUnreachable
            msg.contains("timeout", ignoreCase = true) -> ConnectionError.Timeout
            t is IOException -> ConnectionError.IoFailure(t)
            else -> ConnectionError.Unknown(t)
        }
    }
}
