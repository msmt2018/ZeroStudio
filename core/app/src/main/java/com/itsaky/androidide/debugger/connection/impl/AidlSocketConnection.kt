/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  AidlSocketConnection: AIDL+Socket 方案的真实实现 (子项目 2)。
 *
 *  流程: 打开 ServerSocket(127.0.0.1, 0) -> startActivity(target)
 *        -> serverSocket.accept() 等 host 端 reverse-connect
 *        -> JDWP 握手 + VM.Version -> state = Attached
 *
 *  - resolve/connect/attach 三段各自走 ConnectionRetryPolicy (单方案内重试)
 *  - sendJdwp / receiveJdwp / attachedSocket 走底层 java.net.Socket
 *  - detach 走 VM.Dispose 命令 + 关闭 socket;失败也强制关
 *
 *  依赖: aidl/AidlJdwpProtocol, aidl/AidlHostLauncher, aidl/AidlProcessProbe
 *        三个辅助类,都可注入便于单测。
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
import com.itsaky.androidide.debugger.connection.aidl.AidlHostLauncher
import com.itsaky.androidide.debugger.connection.aidl.AidlJdwpProtocol
import com.itsaky.androidide.debugger.connection.aidl.AidlProcessProbe
import com.itsaky.androidide.utils.ILogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class AidlSocketConnection(
    target: DebugTarget,
    val settings: DebugConnectionSettings,
    private val hostLauncher: AidlHostLauncher? = null,
    private val processProbe: AidlProcessProbe? = null,
    private val retryPolicy: ConnectionRetryPolicy = ConnectionRetryPolicy(
        maxAttempts = settings.retryMaxAttempts,
        initialDelayMs = settings.retryInitialDelayMs,
    ),
) : BaseDebugConnection(ConnectionType.AidlSocket, target) {

    private val log = ILogger.ROOT

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.NeedsHostForeground,
    )

    // ---- 运行时状态(只有 attach 后才有值) ----
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var clientSocket: Socket? = null
    @Volatile private var resolvedEndpoint: String? = null
    private val sessionIdGenerator = AtomicLong(System.currentTimeMillis())

    // ---- JDWP 字节流(Attached 状态时) ----
    private val incoming = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    private val outgoingLock = Any()

    // ---- 注入解析: 优先用构造注入,其次懒加载生产实现 ----
    private val hostLauncherImpl: AidlHostLauncher by lazy {
        hostLauncher ?: AidlHostLauncher.create(
            com.itsaky.androidide.app.IDEApplication.instance
        )
    }
    private val processProbeImpl: AidlProcessProbe by lazy {
        processProbe ?: AidlProcessProbe.create(
            com.itsaky.androidide.app.IDEApplication.instance
        )
    }

    // ---- resolve: 探测 host app 状态 ----

    override suspend fun resolve(): Result<ResolveInfo> {
        if (settings.aidlSocket.requireHostForeground) {
            val attempt = retryPolicy.retry { _ ->
                runCatching {
                    val info = processProbeImpl.findAppProcessInfo(target.packageName)
                    if (info == null) {
                        throw IOException("host app not running: ${target.packageName}")
                    }
                    ResolveInfo(
                        transportKind = "tcp",
                        endpoint = "127.0.0.1:0",
                        requiresHostRunning = true,
                    )
                }.onFailure { log.debug("resolve attempt failed: {}", it.message) }
            }
            return attempt.onSuccess {
                transitionTo(ConnectionState.Connecting)
            }.onFailure {
                transitionTo(ConnectionState.Closed(ConnectionError.HostAppNotRunning))
            }
        }
        // 不要求 host 前台:直接进入 Connecting,等 connect() 拉起 host。
        transitionTo(ConnectionState.Connecting)
        return Result.success(
            ResolveInfo(
                transportKind = "tcp",
                endpoint = "127.0.0.1:0",
                requiresHostRunning = false,
            )
        )
    }

    // ---- connect: 起 ServerSocket + startActivity(target) ----

    override suspend fun connect(): Result<Unit> {
        transitionTo(ConnectionState.Connecting)
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                // 1) 起 ServerSocket (0 = 系统分配)
                val ss = ServerSocket().apply {
                    bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
                    soTimeout = ATTACH_TIMEOUT_MS.toInt()
                }
                serverSocket = ss
                val port = ss.localPort
                resolvedEndpoint = "127.0.0.1:$port"
                log.info("AidlSocketConnection: bound ServerSocket on 127.0.0.1:{}", port)

                // 2) startActivity 把端口塞进 intent extra
                val launched = hostLauncherImpl.launch(
                    packageName = target.packageName,
                    mainActivity = target.mainActivity,
                    port = port,
                )
                if (!launched) {
                    ss.close()
                    serverSocket = null
                    throw IOException("hostLauncher.launch returned false for ${target.packageName}")
                }
            }.onFailure { t ->
                // 失败时回收 ServerSocket,避免端口泄漏
                runCatching { serverSocket?.close() }
                serverSocket = null
                log.warn("AidlSocketConnection: connect attempt failed: {}", t.message)
            }
        }
        return attempt.onSuccess {
            transitionTo(ConnectionState.Handshaking)
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapConnectError(t)))
        }
    }

    // ---- attach: accept() + 握手 + VM.Version ----

    override suspend fun attach(): Result<AttachInfo> {
        val ss = serverSocket
        if (ss == null) {
            return Result.failure(IllegalStateException("attach() called before connect()"))
        }
        transitionTo(ConnectionState.Handshaking)
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                // accept 在 IO 线程做,免阻塞协程调度
                val sock = withContext(Dispatchers.IO) { ss.accept() }
                try {
                    val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(
                        socket = sock,
                        commandId = (sessionIdGenerator.incrementAndGet() and 0x7fffffff).toInt(),
                    )
                    clientSocket = sock
                    AttachInfo(
                        pid = 0, // IDE 端不一定能拿到 host 进程 pid;先 0,后续 DebugSessionLauncher 注入
                        jdwpSessionId = sessionIdGenerator.get(),
                        jdwpDescription = "${info.description} (${info.vmName} ${info.vmVersion}, jdwp ${info.jdwpVersion})",
                    )
                } catch (t: Throwable) {
                    runCatching { sock.close() }
                    clientSocket = null
                    throw t
                }
            }.onFailure { t ->
                log.warn("AidlSocketConnection: attach attempt failed: {}", t.message)
            }
        }
        return attempt.onSuccess { info ->
            val finalSock = clientSocket
            if (finalSock == null) {
                transitionTo(ConnectionState.Closed(ConnectionError.IoFailure(IOException("client socket missing"))))
                return@onSuccess
            }
            transitionTo(ConnectionState.Attached(info.pid, info.jdwpSessionId))
            // 启动一个读循环,把 host 端过来的 JDWP 帧发到 incoming flow
            startReadLoop(finalSock)
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapAttachError(t)))
        }
    }

    // ---- detach: 走 VM.Dispose + 关闭 socket ----

    override suspend fun detach() {
        val sock = clientSocket
        clientSocket = null
        if (sock != null) {
            runCatching {
                // 走 VM.Dispose 命令 (1, 2): data 字段为空
                val out = sock.getOutputStream()
                val cmd = AidlJdwpProtocol.buildVmVersionCommand(0)
                // 把 command 字节改成 VM.Dispose = 2
                cmd[10] = 2
                synchronized(outgoingLock) {
                    out.write(cmd)
                    out.flush()
                }
            }.onFailure { log.debug("detach: VM.Dispose send failed: {}", it.message) }
            runCatching { sock.close() }
        }
        runCatching { serverSocket?.close() }
        serverSocket = null
        transitionTo(ConnectionState.Closed(null))
    }

    // ---- 字节流(Attached 状态时) ----

    override suspend fun sendJdwp(bytes: ByteArray) {
        val sock = clientSocket ?: error("not attached")
        check(currentState() is ConnectionState.Attached) {
            "sendJdwp requires Attached state, was ${currentState()}"
        }
        withContext(Dispatchers.IO) {
            synchronized(outgoingLock) {
                sock.getOutputStream().apply {
                    write(bytes)
                    flush()
                }
            }
        }
    }

    override fun receiveJdwp(): Flow<ByteArray> = incoming.asSharedFlow()

    // ---- 集成钩子:ConnectionBackedDebugger 用 ----

    override fun attachedSocket(): Socket {
        val sock = clientSocket ?: error("not attached")
        return sock
    }

    // ---- 释放 ----

    override fun release() {
        runCatching { clientSocket?.close() }
        runCatching { serverSocket?.close() }
        clientSocket = null
        serverSocket = null
        super.release()
    }

    // ---- 私有 ----

    private fun startReadLoop(sock: Socket) {
        // 用守护线程读 socket,把每段字节切到 flow 上;ConnectionBackedDebugger
        // 会接走这个 flow 但本身 JdwpClient 不再依赖 — 这里只服务于通过
        // sendJdwp/receiveJdwp 调用的上层。
        Thread({
            try {
                val input = sock.getInputStream()
                val buf = ByteArray(8192)
                while (!sock.isClosed) {
                    val n = try {
                        input.read(buf)
                    } catch (e: IOException) {
                        -1
                    }
                    if (n <= 0) break
                    val chunk = ByteArray(n)
                    System.arraycopy(buf, 0, chunk, 0, n)
                    incoming.tryEmit(chunk)
                }
            } catch (t: Throwable) {
                log.debug("AidlSocketConnection: read loop ended: {}", t.message)
            }
        }, "AidlSocketConnection-read").apply {
            isDaemon = true
            start()
        }
    }

    private fun mapConnectError(t: Throwable): ConnectionError = when (t) {
        is IOException -> ConnectionError.IoFailure(t)
        else -> ConnectionError.Unknown(t)
    }

    private fun mapAttachError(t: Throwable): ConnectionError {
        val msg = t.message.orEmpty()
        return when {
            msg.contains("Bad handshake", ignoreCase = true) ||
                msg.contains("EOF during handshake", ignoreCase = true) ->
                ConnectionError.JdwpHandshakeFailed
            msg.contains("reply timeout", ignoreCase = true) ->
                ConnectionError.Timeout
            t is IOException -> ConnectionError.IoFailure(t)
            else -> ConnectionError.Unknown(t)
        }
    }

    companion object {
        /** attach() 阶段 accept 的最长等待时间。3 次重试共约 30s。 */
        const val ATTACH_TIMEOUT_MS: Long = 10_000L
    }
}
