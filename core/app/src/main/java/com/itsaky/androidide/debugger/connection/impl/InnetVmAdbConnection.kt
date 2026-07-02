/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  InnetVmAdbConnection: 内网虚拟机 (光速虚拟机/VMOS/虚拟大师) ADB
 *  端口转发方案 (子项目 6)。
 *
 *  流程:
 *    1) resolve: 探测 adb 端口可达 (settings.innetAdb.adbHost/adbPort)
 *                走 TCP connect 一次; 不真起 adb client
 *    2) connect: 走 AdbRunner
 *                  a) adb connect <adbHost>:<adbPort>     // 拿到 device
 *                  b) adb [-s serial] shell pidof <pkg>    // 拿 host 进程 PID
 *                  c) bind ServerSocket(0) 拿 L = localPort
 *                  d) adb [-s serial] forward tcp:L localabstract:jdwp-<pid>
 *    3) attach:  accept() on ServerSocket, 走 JDWP 握手 + VM.Version
 *    4) detach:  adb forward --remove tcp:L + close socket
 *
 *  跟 InnetVmSocks 的区别:
 *    - 走 ADB forward 通道, 不需要 SOCKS5 server
 *    - host app 必须 android:debuggable=true
 *    - 需要 ADB binary 可用 (assets/platform-tools/adb)
 *    - 多设备时用 settings.innetAdb.adbSerial 锁定
 *
 *  依赖: AdbRunner (抽象 adb 命令执行) - 可注入, 便于单测
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

class InnetVmAdbConnection(
    target: DebugTarget,
    val settings: DebugConnectionSettings,
    private val adbRunner: AdbRunner? = null,
    private val retryPolicy: ConnectionRetryPolicy = ConnectionRetryPolicy(
        maxAttempts = settings.retryMaxAttempts,
        initialDelayMs = settings.retryInitialDelayMs,
    ),
) : BaseDebugConnection(ConnectionType.InnetVmAdb, target) {

    private val log = ILogger.ROOT

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.CanReadProcNet,
    )

    // ---- 依赖懒加载 ----
    private val adbRunnerImpl: AdbRunner by lazy { adbRunner ?: AdbRunner.create() }

    // ---- 运行时状态 ----
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var clientSocket: Socket? = null
    @Volatile private var localPort: Int = 0
    @Volatile private var hostPid: Int = 0
    private val sessionIdGenerator = AtomicLong(System.currentTimeMillis())
    private val incoming = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    private val outgoingLock = Any()

    // ---- resolve: TCP 探测 adb 端口可达 ----

    override suspend fun resolve(): Result<ResolveInfo> {
        val cfg = settings.innetAdb
        if (cfg.adbHost.isBlank() || cfg.adbPort <= 0) {
            transitionTo(ConnectionState.Closed(ConnectionError.PortResolveFailed))
            return Result.failure(IOException("InnetVmAdb host/port not configured"))
        }
        val addr = InetSocketAddress(cfg.adbHost, cfg.adbPort)
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                val probe = Socket()
                try {
                    probe.connect(addr, cfg.connectTimeoutMs.toInt())
                } finally {
                    runCatching { probe.close() }
                }
            }.onFailure { log.debug("resolve: probe {} failed: {}", addr, it.message) }
        }
        return attempt.onSuccess {
            transitionTo(ConnectionState.Connecting)
            Result.success(
                ResolveInfo(
                    transportKind = "adb-forward",
                    endpoint = "adb://${cfg.adbHost}:${cfg.adbPort}",
                    requiresHostRunning = true,
                )
            )
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapConnectError(t)))
        }
    }

    // ---- connect: adb connect + 拿 host pid + bind + adb forward ----

    override suspend fun connect(): Result<Unit> {
        val cfg = settings.innetAdb
        transitionTo(ConnectionState.Connecting)
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                // 1) adb connect
                val connectArgs = listOf("connect", "${cfg.adbHost}:${cfg.adbPort}")
                val r = runAdb(connectArgs)
                if (!r.isSuccess) {
                    throw IOException("adb connect failed: exit=${r.exitCode}, stderr=${r.stderr.trim()}")
                }
                log.info("InnetVmAdbConnection: adb connect ok: {}", r.stdout.trim())

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
                log.info("InnetVmAdbConnection: bound 127.0.0.1:{} (host pid={})", port, pid)

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
                log.info("InnetVmAdbConnection: adb forward {} localabstract:jdwp-{} ok", forwardSpec, pid)
            }.onFailure { t ->
                log.warn("InnetVmAdbConnection: connect attempt failed: {}", t.message)
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
                log.warn("InnetVmAdbConnection: attach attempt failed: {}", t.message)
            }
        }
        return attempt.onSuccess { info ->
            val finalSock = clientSocket
            if (finalSock == null) {
                transitionTo(ConnectionState.Closed(ConnectionError.IoFailure(IOException("client socket missing"))))
                return@onSuccess
            }
            transitionTo(ConnectionState.Attached(info.pid, info.jdwpSessionId))
            startReadLoop(finalSock)
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapAttachError(t)))
        }
    }

    // ---- detach: VM.Dispose + adb forward --remove + close ----

    override suspend fun detach() {
        val sock = clientSocket
        clientSocket = null
        if (sock != null) {
            runCatching {
                val out = sock.getOutputStream()
                val cmd = AidlJdwpProtocol.buildVmVersionCommand(0)
                cmd[10] = 2 // VM.Dispose
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

    // ---- 私有 ----

    private fun runAdb(args: List<String>): AdbResult {
        val cfg = settings.innetAdb
        val effectiveArgs = if (!cfg.adbSerial.isNullOrBlank()) {
            listOf("-s", cfg.adbSerial) + args
        } else {
            args
        }
        return adbRunnerImpl.run(effectiveArgs, cfg.connectTimeoutMs)
    }

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
                log.debug("InnetVmAdbConnection: read loop ended: {}", t.message)
            }
        }, "InnetVmAdbConnection-read").apply { isDaemon = true; start() }
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
