/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  AidlSocketConnection: AIDL+Socket 方案的真实实现 (子项目 2)。
 *
 *  流程 (TCP 默认路径): 打开 ServerSocket(127.0.0.1, 0) -> startActivity(target)
 *        -> serverSocket.accept() 等 host 端 reverse-connect
 *        -> JDWP 握手 + VM.Version -> state = Attached
 *
 *  流程 (子项目 9e LocalBridge 路径): 走 [HostBridgeServer] 收到的 host 端
 *    反向连接 (host 端 HostAttachAgentBootstrap 自动反连), 不需要 IDE
 *    主动 launch host app。两条路径互斥: 配置了 hostBridge 就走 LocalBridge,
 *    否则走 TCP 默认路径。
 *
 *  - resolve/connect/attach 三段各自走 ConnectionRetryPolicy (单方案内重试)
 *  - TCP 路径: sendJdwp / receiveJdwp / attachedSocket 走底层 java.net.Socket
 *  - LocalBridge 路径: sendJdwp / receiveJdwp 走 LocalSocket 的 input/output stream
 *  - detach 走 VM.Dispose 命令 + 关闭 socket;失败也强制关
 *
 *  依赖: aidl/AidlJdwpProtocol, aidl/AidlHostLauncher, aidl/AidlProcessProbe
 *        三个辅助类,都可注入便于单测。
 *  子项目 9e 新增: host/HostBridgeServer (IDE 端 LocalServerSocket)
 */

package com.itsaky.androidide.debugger.connection.impl

import android.net.LocalSocket
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
import java.io.InputStream
import java.io.OutputStream
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

    // ---- 子项目 9e: LocalBridge 路径状态(与 clientSocket 互斥) ----
    @Volatile private var localBridgeSocket: LocalSocket? = null
    @Volatile private var localBridgeInput: InputStream? = null
    @Volatile private var localBridgeOutput: OutputStream? = null

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

    /** 子项目 9e: 注入 HostBridgeServer (由 AppReadyAutoConnect 设置) */
    @Volatile var hostBridge: com.itsaky.androidide.debugger.connection.host.HostBridgeServer? = null

    // ---- resolve: 探测 host app 状态 ----

    override suspend fun resolve(): Result<ResolveInfo> {
        // 子项目 9e: 走 LocalBridge 路径时不要求 host 前台
        // (host 端 HostAttachAgentBootstrap 自己保证有反连到来)
        if (hostBridge != null) {
            transitionTo(ConnectionState.Connecting)
            return Result.success(
                ResolveInfo(
                    transportKind = "aidl-socket-local-bridge",
                    endpoint = "local://${hostBridge!!.localSocketName}",
                    requiresHostRunning = true,
                )
            )
        }
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

    // ---- connect: 起 ServerSocket + startActivity(target),或 LocalBridge 准备 ----

    override suspend fun connect(): Result<Unit> {
        // 子项目 9e: LocalBridge 路径下 connect 是 no-op
        // (HostBridgeServer 已经在监听)
        if (hostBridge != null) {
            transitionTo(ConnectionState.Handshaking)
            return Result.success(Unit)
        }
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

    // ---- attach: LocalBridge 路径 OR TCP accept() 路径 ----

    override suspend fun attach(): Result<AttachInfo> {
        val bridge = hostBridge
        if (bridge != null) {
            return attachLocalBridge(bridge)
        }
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
        // 失败: 走 mapAttachError
        attempt.exceptionOrNull()?.let { t ->
            transitionTo(ConnectionState.Closed(mapAttachError(t)))
            return Result.failure(t)
        }
        // 成功但 post-condition 失败: 走 finishAttach (ok=false 分支)
        val info = attempt.getOrNull()!!
        val finalSock = clientSocket
        return finishAttach(
            info = info,
            ok = finalSock != null,
            failureMsg = "attach returned but client socket is missing",
            onAttached = { startReadLoopFromSocket(finalSock!!) },
        )
    }

    /**
     * 子项目 9e: LocalBridge 路径下的 attach。
     * 不需要 IDE 主动 launch host app, 走 HostBridgeServer 收到的 host 端
     * 反向连接 (host 端 HostAttachAgentBootstrap 自动反连)。
     *
     * 跟默认 attach() 的差别:
     *   - 不需要 startActivity (host 端自己启动了)
     *   - 不需要 bind ServerSocket (HostBridgeServer 在子项目 9a 里已经 bind)
     *   - 不需要等 host 端"收到端口再连" (host 端已经反连了)
     *
     * 失败: HostBridgeServer 没启动 / 反连超时 -> 走既有 retry 策略。
     */
    private suspend fun attachLocalBridge(
        bridge: com.itsaky.androidide.debugger.connection.host.HostBridgeServer,
        timeoutMs: Long = 5_000L,
    ): Result<AttachInfo> {
        transitionTo(ConnectionState.Handshaking)
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                val conn = bridge.awaitNextConnection(timeoutMs)
                    ?: throw IOException("no host connection within ${timeoutMs}ms")
                // 注: conn.socket 是 LocalSocket, 不是 java.net.Socket。
                // 必须走 InputStream/OutputStream 重载, 不能用 Socket 重载
                // (LocalSocket 不继承 java.net.Socket)。
                val ls = conn.socket
                val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(
                    output = ls.outputStream,
                    input = ls.inputStream,
                    commandId = (sessionIdGenerator.incrementAndGet() and 0x7fffffff).toInt(),
                )
                localBridgeSocket = ls
                localBridgeInput = ls.inputStream
                localBridgeOutput = ls.outputStream
                AttachInfo(
                    pid = conn.hello.pid,
                    jdwpSessionId = sessionIdGenerator.get(),
                    jdwpDescription = "${info.description} (${info.vmName} ${info.vmVersion}, jdwp ${info.jdwpVersion}) [local-bridge]",
                )
            }.onFailure { t ->
                log.warn("AidlSocketConnection: attachLocalBridge failed: {}", t.message)
            }
        }
        // 失败: 走 mapAttachError
        attempt.exceptionOrNull()?.let { t ->
            transitionTo(ConnectionState.Closed(mapAttachError(t)))
            return Result.failure(t)
        }
        // 成功但 post-condition 失败: 走 finishAttach (ok=false 分支)
        val info = attempt.getOrNull()!!
        val ls = localBridgeSocket
        val ins = localBridgeInput
        return finishAttach(
            info = info,
            ok = ls != null && ins != null,
            failureMsg = "attachLocalBridge returned but local bridge socket/stream is missing",
            onAttached = { startReadLoopFromStream(ins!!) },
        )
    }

    // ---- detach: 走 VM.Dispose + 关闭 socket(LocalBridge / TCP 分支) ----

    override suspend fun detach() {
        // LocalBridge 路径
        val ls = localBridgeSocket
        if (ls != null) {
            localBridgeSocket = null
            runCatching {
                val out = localBridgeOutput ?: ls.outputStream
                val cmd = AidlJdwpProtocol.buildVmVersionCommand(0)
                cmd[10] = 2 // VM.Dispose
                synchronized(outgoingLock) {
                    out.write(cmd)
                    out.flush()
                }
            }.onFailure { log.debug("detach(local-bridge): VM.Dispose send failed: {}", it.message) }
            runCatching { ls.close() }
            localBridgeInput = null
            localBridgeOutput = null
        }
        // TCP 路径
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
        check(currentState() is ConnectionState.Attached) {
            "sendJdwp requires Attached state, was ${currentState()}"
        }
        withContext(Dispatchers.IO) {
            synchronized(outgoingLock) {
                // LocalBridge 路径
                localBridgeOutput?.let { out ->
                    out.write(bytes)
                    out.flush()
                    return@withContext
                }
                // TCP 路径
                val sock = clientSocket ?: error("not attached")
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
        // 子项目 9e: LocalBridge 路径下没有 java.net.Socket
        if (localBridgeSocket != null) {
            throw UnsupportedOperationException(
                "AidlSocketConnection[local-bridge]: attachedSocket() not available; " +
                "use sendJdwp()/receiveJdwp() flow API"
            )
        }
        val sock = clientSocket ?: error("not attached")
        return sock
    }

    // ---- 释放 ----

    override fun release() {
        runCatching { localBridgeSocket?.close() }
        runCatching { clientSocket?.close() }
        runCatching { serverSocket?.close() }
        localBridgeSocket = null
        localBridgeInput = null
        localBridgeOutput = null
        clientSocket = null
        serverSocket = null
        super.release()
    }

    // ---- 私有 ----

    private fun startReadLoopFromSocket(sock: Socket) {
        startReadLoopFromStream(sock.getInputStream())
    }

    private fun startReadLoopFromStream(input: InputStream) {
        // 用守护线程读 input, 把每段字节切到 flow 上;ConnectionBackedDebugger
        // 会接走这个 flow 但本身 JdwpClient 不再依赖 — 这里只服务于通过
        // sendJdwp/receiveJdwp 调用的上层。
        Thread({
            try {
                val buf = ByteArray(8192)
                while (currentState() is ConnectionState.Attached) {
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
