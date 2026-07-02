/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  RootConnection: Root 方案 (子项目 4) 真实实现。
 *
 *  流程:
 *    1. resolve: 探测设备是否 root (exec `su -c 'id'`)
 *    2. connect: `su -c 'cmd activity' / host attach agent` 准备 JDWP
 *    3. attach:  走 `su` 拉一个 root 进程直接读 /proc/<host_pid>/fd/<jdwp_socket>
 *               (host runtime 配合) 或 attach-agent
 *
 *  跟 AIDL / Shizuku 的关键区别: 走 root 权限直接 attach host JDWP socket,
 *  不需要 host app 主动 reverse-connect, 也不需要 Shizuku 服务。
 *
 *  当前实现策略 (子项目 4 骨架, 等子项目 8 host runtime 完成):
 *    - resolve: 跑 `su -c 'id'` 探测 root
 *    - connect: 标记 connect 成功 (具体 attach agent 命令由子项目 8 一起提供)
 *    - attach: 通过 RootClient (新文件) 走 su exec 拿 socket
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
import com.itsaky.androidide.debugger.connection.root.RootClient
import com.itsaky.androidide.debugger.connection.root.RootProbe
import com.itsaky.androidide.utils.ILogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class RootConnection(
    target: DebugTarget,
    val settings: DebugConnectionSettings,
    private val rootProbe: RootProbe? = null,
    private val rootClient: RootClient? = null,
    private val retryPolicy: ConnectionRetryPolicy = ConnectionRetryPolicy(
        maxAttempts = settings.retryMaxAttempts,
        initialDelayMs = settings.retryInitialDelayMs,
    ),
) : BaseDebugConnection(ConnectionType.Root, target) {

    private val log = ILogger.ROOT

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.CanInstallInHost,   // root 可在 host 注入
        ConnectionCapability.CanReadProcNet,     // root 可读 /proc/<pid>/fd/
    )

    // ---- 注入解析 ----
    private val rootProbeImpl: RootProbe by lazy { rootProbe ?: RootProbe.create() }
    private val rootClientImpl: RootClient by lazy { rootClient ?: RootClient.create() }

    // ---- 运行时状态 ----
    // 子项目 4: Root 路径下走 InputStream/OutputStream (跟 AidlSocketConnection
    //   LocalBridge 路径同款), 不存 java.net.Socket 字段 (因 host jdwp 是
    //   abstract unix socket, 通过 su -c socat 转 stdin/stdout)
    @Volatile private var stream: com.itsaky.androidide.debugger.connection.root.RootJdwpStream? = null
    @Volatile private var input: java.io.InputStream? = null
    @Volatile private var output: java.io.OutputStream? = null
    @Volatile private var hostPid: Int = 0
    private val sessionIdGenerator = AtomicLong(System.currentTimeMillis())
    private val incoming = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)

    // ---- resolve: 探测 root ----

    override suspend fun resolve(): Result<ResolveInfo> {
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                val ok = rootProbeImpl.probeHasRoot(settings.root.suBinary)
                if (!ok) {
                    throw IOException("root not available (su=${settings.root.suBinary} not granted)")
                }
            }.onFailure { log.debug("resolve attempt failed: {}", it.message) }
        }
        return attempt.onSuccess {
            transitionTo(ConnectionState.Connecting)
            Result.success(
                ResolveInfo(
                    transportKind = "jdwp",
                    endpoint = "root:jdwp",
                    requiresHostRunning = false,
                )
            )
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapResolveError(t)))
        }.also { /* map result */ }
    }

    // ---- connect: 找 host pid + 准备 attach ----

    override suspend fun connect(): Result<Unit> {
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                val pid = rootClientImpl.findProcessId(
                    packageName = target.packageName,
                    suBin = settings.root.suBinary,
                    timeoutMs = settings.root.probeTimeoutMs,
                )
                if (pid <= 0) {
                    throw IOException("could not find pid for ${target.packageName}")
                }
                hostPid = pid
                log.info("RootConnection: host pid = {}", pid)
            }.onFailure { log.warn("connect attempt failed: {}", it.message) }
        }
        return attempt.onSuccess {
            transitionTo(ConnectionState.Handshaking)
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapConnectError(t)))
        }
    }

    // ---- attach: 走 su + socat 拿 host JDWP stdin/stdout ----

    override suspend fun attach(): Result<AttachInfo> {
        val pid = hostPid
        if (pid <= 0) {
            return Result.failure(IllegalStateException("attach() before connect()"))
        }
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                val s = rootClientImpl.openJdwpStream(
                    hostPid = pid,
                    suBin = settings.root.suBinary,
                    timeoutMs = settings.root.probeTimeoutMs,
                )
                // 走 JDWP 握手 + VM.Version
                val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(
                    output = s.output,
                    input = s.input,
                    commandId = (sessionIdGenerator.incrementAndGet() and 0x7fffffff).toInt(),
                )
                stream = s
                input = s.input
                output = s.output
                AttachInfo(
                    pid = pid,
                    jdwpSessionId = sessionIdGenerator.get(),
                    jdwpDescription = "${info.description} (${info.vmName} ${info.vmVersion}, jdwp ${info.jdwpVersion})",
                )
            }.onFailure { log.warn("attach attempt failed: {}", it.message) }
        }
        return attempt.onSuccess { info ->
            if (input == null || output == null) {
                transitionTo(ConnectionState.Closed(ConnectionError.IoFailure(IOException("attach returned but stream is null"))))
                return@onSuccess
            }
            transitionTo(ConnectionState.Attached(info.pid, info.jdwpSessionId))
            startReadLoopFromStream(input!!)
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapAttachError(t)))
        }
    }

    // ---- detach / 字节流 / 钩子 / 释放 ----

    override suspend fun detach() {
        val s = stream
        stream = null
        if (s != null) {
            runCatching {
                val out = output ?: s.output
                val cmd = AidlJdwpProtocol.buildVmVersionCommand(0)
                cmd[10] = 2 // VM.Dispose
                synchronized(out) {
                    out.write(cmd)
                    out.flush()
                }
            }.onFailure { log.debug("detach: VM.Dispose failed: {}", it.message) }
            runCatching { s.close() }
        }
        input = null
        output = null
        transitionTo(ConnectionState.Closed(null))
    }

    override suspend fun sendJdwp(bytes: ByteArray) {
        val out = output ?: error("not attached")
        check(currentState() is ConnectionState.Attached) {
            "sendJdwp requires Attached state, was ${currentState()}"
        }
        withContext(Dispatchers.IO) {
            synchronized(out) {
                out.write(bytes)
                out.flush()
            }
        }
    }

    override fun receiveJdwp(): Flow<ByteArray> = incoming.asSharedFlow()

    override fun attachedSocket(): Socket {
        // Root 路径下走 InputStream/OutputStream, 不返回 java.net.Socket
        throw UnsupportedOperationException(
            "RootConnection: attachedSocket() not available; use sendJdwp()/receiveJdwp() flow API"
        )
    }

    override fun release() {
        runCatching { stream?.close() }
        stream = null
        input = null
        output = null
        super.release()
    }

    // ---- 私有 ----

    private fun startReadLoopFromStream(ins: java.io.InputStream) {
        Thread({
            try {
                val buf = ByteArray(8192)
                while (currentState() is ConnectionState.Attached) {
                    val n = try { ins.read(buf) } catch (e: IOException) { -1 }
                    if (n <= 0) break
                    val chunk = ByteArray(n)
                    System.arraycopy(buf, 0, chunk, 0, n)
                    incoming.tryEmit(chunk)
                }
            } catch (t: Throwable) {
                log.debug("RootConnection: read loop ended: {}", t.message)
            }
        }, "RootConnection-read").apply { isDaemon = true; start() }
    }

    private fun mapResolveError(t: Throwable): ConnectionError {
        val msg = t.message.orEmpty()
        return when {
            msg.contains("root", ignoreCase = true) -> ConnectionError.PermissionDenied
            t is IOException -> ConnectionError.IoFailure(t)
            else -> ConnectionError.Unknown(t)
        }
    }

    private fun mapConnectError(t: Throwable): ConnectionError = when (t) {
        is IOException -> ConnectionError.IoFailure(t)
        else -> ConnectionError.Unknown(t)
    }

    private fun mapAttachError(t: Throwable): ConnectionError {
        val msg = t.message.orEmpty()
        return when {
            msg.contains("Bad handshake", ignoreCase = true) -> ConnectionError.JdwpHandshakeFailed
            msg.contains("timeout", ignoreCase = true) -> ConnectionError.Timeout
            t is IOException -> ConnectionError.IoFailure(t)
            else -> ConnectionError.Unknown(t)
        }
    }
}
