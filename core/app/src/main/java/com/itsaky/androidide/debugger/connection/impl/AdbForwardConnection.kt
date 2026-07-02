/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  AdbForwardConnection: USB / LAN / 内网 VM ADB 端口转发方案的共享实现。
 *
 *  三个 ConnectionType 都走同一条主路径:
 *    1) resolve: TCP 探测 adb 端口可达
 *    2) connect: adb 前置检查 + 拿 host pid + bind ServerSocket + adb forward
 *    3) attach:  accept + JDWP 握手 + VM.Version
 *    4) detach:  VM.Dispose + adb forward --remove + close
 *
 *  差异点 (子类各自实现 runPreConnectCheck):
 *    - InnetVmAdb: 必须先 `adb connect <vm>:<port>` (VM 还没在 adb server 里)
 *    - UsbLan: 假定设备已在 adb devices 列表 (USB 已插 / LAN 已 `adb connect`),
 *      走 `adb devices` 校验
 *    - 如果 adbSerial 已配置, 所有 adb 命令都加 -s <serial> 前缀
 *
 *  本类是 abstract - 真正的 IDebugConnection 实体类只有:
 *    - InnetVmAdbConnection (子项目 6)
 *    - UsbLanConnection (子项目 7)
 *
 *  共享的依赖:
 *    - AdbRunner (子项目 6 引入): 抽象 adb 命令执行
 *    - AidlJdwpProtocol: 14 字节 handshake + VM.Version
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
import com.itsaky.androidide.debugger.connection.adb.AdbResult
import com.itsaky.androidide.debugger.connection.adb.AdbRunner
import com.itsaky.androidide.debugger.connection.aidl.AidlJdwpProtocol
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

/**
 * 共享抽象基类, 子类 (InnetVmAdbConnection / UsbLanConnection) 通过:
 *  - [type]:              ConnectionType 区分
 *  - [resolveInfoKind]:   resolve() 返回的 transportKind 描述
 *  - [runPreConnectCheck]: connect() 阶段的前置 adb 检查 (各方案不同)
 * 实现具体方案。
 */
abstract class AdbForwardConnection(
    type: ConnectionType,
    target: DebugTarget,
    val settings: DebugConnectionSettings,
    private val adbRunner: AdbRunner? = null,
    private val retryPolicy: ConnectionRetryPolicy = ConnectionRetryPolicy(
        maxAttempts = settings.retryMaxAttempts,
        initialDelayMs = settings.retryInitialDelayMs,
    ),
) : BaseDebugConnection(type, target) {

    protected val log = ILogger.ROOT

    /** 子类在 [resolve] 成功时填到 ResolveInfo.transportKind 的描述 (e.g. "adb-forward-vm", "adb-forward-usb") */
    protected abstract val resolveInfoKind: String

    /** 当前方案用的 InnetAdbConfig 还是 UsbLanConfig (子类各自暴露) */
    protected abstract val adbHost: String
    protected abstract val adbPort: Int
    protected abstract val adbSerial: String?
    protected abstract val connectTimeoutMs: Long

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.CanReadProcNet,
    )

    // ---- 依赖懒加载 ----
    private val adbRunnerImpl: AdbRunner by lazy { adbRunner ?: AdbRunner.create() }

    // ---- 运行时状态 ----
    @Volatile protected var serverSocket: ServerSocket? = null
    @Volatile protected var clientSocket: Socket? = null
    @Volatile protected var localPort: Int = 0
    @Volatile protected var hostPid: Int = 0
    private val sessionIdGenerator = AtomicLong(System.currentTimeMillis())
    private val incoming = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    private val outgoingLock = Any()

    // ---- 子类 hook: connect 阶段在 adb forward 之前做一次额外检查 ----
    /**
     * 子类各自实现:
     *   - InnetVmAdb: adb connect <host>:<port>
     *   - UsbLan: adb devices (验证设备已在列表)
     * 失败抛 IOException, 成功返回 Unit。
     */
    @Throws(IOException::class)
    protected abstract fun runPreConnectCheck()

    // ---- resolve: TCP 探测 adb 端口可达 ----

    override suspend fun resolve(): Result<ResolveInfo> {
        if (adbHost.isBlank() || adbPort <= 0) {
            transitionTo(ConnectionState.Closed(ConnectionError.PortResolveFailed))
            return Result.failure(IOException("$type host/port not configured"))
        }
        val addr = InetSocketAddress(adbHost, adbPort)
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                val probe = Socket()
                try {
                    probe.connect(addr, connectTimeoutMs.toInt())
                } finally {
                    runCatching { probe.close() }
                }
            }.onFailure { log.debug("resolve: probe {} failed: {}", addr, it.message) }
        }
        return attempt.onSuccess {
            transitionTo(ConnectionState.Connecting)
            Result.success(
                ResolveInfo(
                    transportKind = resolveInfoKind,
                    endpoint = "adb://$adbHost:$adbPort",
                    requiresHostRunning = true,
                )
            )
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapConnectError(t)))
        }
    }

    // ---- connect: 子类前置检查 + 拿 host pid + bind + adb forward ----

    override suspend fun connect(): Result<Unit> {
        transitionTo(ConnectionState.Connecting)
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                // 1) 子类前置检查 (adb connect / adb devices)
                runPreConnectCheck()

                // 2) 拿 host pid
                val pidofArgs = listOf("shell", "pidof", "-s", target.packageName)
                val pidofRes = runAdb(pidofArgs)
                if (!pidofRes.isSuccess) {
                    throw IOException("adb shell pidof failed: exit=${pidofRes.exitCode}, stderr=${pidofRes.stderr.trim()}")
                }
                val pid = pidofRes.stdout.trim().toIntOrNull()
                    ?: throw IOException("adb shell pidof: empty/non-numeric stdout: ${pidofRes.stdout.trim()}")
                if (pid <= 0) {
                    throw IOException("host app ${target.packageName} not running (pidof returned '$pid')")
                }
                hostPid = pid

                // 3) bind ServerSocket(0) 拿 L
                val ss = ServerSocket().apply {
                    bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
                    soTimeout = ATTACH_TIMEOUT_MS.toInt()
                }
                val port = ss.localPort
                serverSocket = ss
                localPort = port
                log.info("{}: bound 127.0.0.1:{} (host pid={})", type, port, pid)

                // 4) adb forward tcp:L localabstract:jdwp-<pid>
                val forwardSpec = "tcp:$port"
                val forwardArgs = listOf(
                    "forward",
                    forwardSpec,
                    "localabstract:jdwp-$pid",
                )
                val fwdRes = runAdb(forwardArgs)
                if (!fwdRes.isSuccess) {
                    ss.close()
                    serverSocket = null
                    localPort = 0
                    throw IOException("adb forward failed: exit=${fwdRes.exitCode}, stderr=${fwdRes.stderr.trim()}")
                }
                log.info("{}: adb forward {} localabstract:jdwp-{} ok", type, forwardSpec, pid)
            }.onFailure { t ->
                log.warn("{}: connect attempt failed: {}", type, t.message)
                runCatching { serverSocket?.close() }
                serverSocket = null
                localPort = 0
            }
        }
        return attempt.onSuccess {
            transitionTo(ConnectionState.Handshaking)
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapConnectError(t)))
        }
    }

    // ---- attach: accept + JDWP 握手 + VM.Version ----

    override suspend fun attach(): Result<AttachInfo> {
        val ss = serverSocket
        if (ss == null) {
            return Result.failure(IllegalStateException("attach() called before connect()"))
        }
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                val sock = withContext(Dispatchers.IO) { ss.accept() }
                try {
                    val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(
                        socket = sock,
                        commandId = (sessionIdGenerator.incrementAndGet() and 0x7fffffff).toInt(),
                    )
                    clientSocket = sock
                    AttachInfo(
                        pid = hostPid,
                        jdwpSessionId = sessionIdGenerator.get(),
                        jdwpDescription = "${info.description} (${info.vmName} ${info.vmVersion}, jdwp ${info.jdwpVersion})",
                    )
                } catch (t: Throwable) {
                    runCatching { sock.close() }
                    throw t
                }
            }.onFailure { t ->
                log.warn("{}: attach attempt failed: {}", type, t.message)
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
            // Phase 12m: 移除默认启 read loop。attachedSocket() 会被 JdwpClient
            // 拿去 (走 ConnectionBackedDebugger.run() -> debugger.forClient(client)),
            // 之前默认 onAttached 调 startReadLoop 跟 JdwpClient 内部 read 抢同
            // socket.inputStream, 字节被 split 丢失。
            onAttached = { /* JdwpClient 接管, 不启 read loop */ },
        )
    }

    // ---- detach: VM.Dispose + adb forward --remove + close ----

    override suspend fun detach() {
        val sock = clientSocket
        clientSocket = null
        if (sock != null) {
            runCatching {
                val out = sock.getOutputStream()
                val cmd = AidlJdwpProtocol.buildVmDisposeCommand(0)
                synchronized(outgoingLock) {
                    out.write(cmd)
                    out.flush()
                }
            }.onFailure { log.debug("detach: VM.Dispose send failed: {}", it.message) }
            runCatching { sock.close() }
        }
        // 清理 adb forward
        if (localPort > 0) {
            runCatching {
                val r = runAdb(listOf("forward", "--remove", "tcp:$localPort"))
                if (!r.isSuccess) {
                    log.warn("detach: adb forward --remove tcp:{} failed: {}", localPort, r.stderr.trim())
                }
            }.onFailure { log.debug("detach: adb forward --remove threw: {}", it.message) }
        }
        runCatching { serverSocket?.close() }
        serverSocket = null
        localPort = 0
        transitionTo(ConnectionState.Closed(null))
    }

    // ---- 字节流 (Attached 状态时) ----

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

    override fun attachedSocket(): Socket {
        val sock = clientSocket ?: error("not attached")
        return sock
    }

    // ---- 释放 ----

    override fun release() {
        runCatching { clientSocket?.close() }
        runCatching { serverSocket?.close() }
        // release 时也尝试清 forward (可能 detach 失败)
        if (localPort > 0) {
            runCatching { runAdb(listOf("forward", "--remove", "tcp:$localPort")) }
            localPort = 0
        }
        clientSocket = null
        serverSocket = null
        super.release()
    }

    // ---- protected helpers for subclasses ----

    protected fun runAdb(args: List<String>): AdbResult {
        val effectiveArgs = if (!adbSerial.isNullOrBlank()) {
            listOf("-s", adbSerial) + args
        } else {
            args
        }
        return adbRunnerImpl.run(effectiveArgs, connectTimeoutMs)
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
                log.debug("{}: read loop ended: {}", type, t.message)
            }
        }, "$type-read").apply { isDaemon = true; start() }
    }

    protected open fun mapConnectError(t: Throwable): ConnectionError = when (t) {
        is IOException -> ConnectionError.IoFailure(t)
        else -> ConnectionError.Unknown(t)
    }

    protected open fun mapAttachError(t: Throwable): ConnectionError {
        val msg = t.message.orEmpty()
        return when {
            msg.contains("Bad handshake", ignoreCase = true) ||
                msg.contains("EOF during handshake", ignoreCase = true) ->
                ConnectionError.JdwpHandshakeFailed
            msg.contains("reply timeout", ignoreCase = true) -> ConnectionError.Timeout
            t is IOException -> ConnectionError.IoFailure(t)
            else -> ConnectionError.Unknown(t)
        }
    }

    companion object {
        /** attach() 阶段 accept 的最长等待时间。3 次重试共约 30s。 */
        const val ATTACH_TIMEOUT_MS: Long = 10_000L
    }
}
