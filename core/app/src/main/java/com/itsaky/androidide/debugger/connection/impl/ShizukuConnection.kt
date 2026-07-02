/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuConnection: Shizuku 4 子路径 (Auto / WifiAdb / Binder / InHostPlugin / Socks)
 *  的真实实现 (子项目 3)。
 *
 *  - WifiAdb / InHostPlugin 复用 AidlSocketConnection 的 ServerSocket + startActivity 模式
 *  - Binder 走 Shizuku binder 把 host JDWP fd 转回 IDE
 *  - Socks 走 Shizuku newProcess 启动 SOCKS5 server + IDE 当 SOCKS5 客户端
 *
 *  resolve(): 探测 Shizuku 状态 + 选 subPath
 *  connect(): 准备 (启动 SOCKS5 / 拉 host plugin / 开 ServerSocket)
 *  attach():  拿 socket (Shizuku 4 路径之一) + JDWP 握手 + VM.Version
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
import com.itsaky.androidide.debugger.connection.ShizukuConfig
import com.itsaky.androidide.debugger.connection.aidl.AidlJdwpProtocol
import com.itsaky.androidide.debugger.connection.shizuku.DefaultShizukuFdTransporter
import com.itsaky.androidide.debugger.connection.shizuku.DefaultShizukuProbe
import com.itsaky.androidide.debugger.connection.shizuku.ShizukuBinderClient
import com.itsaky.androidide.debugger.connection.shizuku.ShizukuFdTransporter
import com.itsaky.androidide.debugger.connection.shizuku.ShizukuProbe
import com.itsaky.androidide.debugger.connection.shizuku.ShizukuSocksClient
import com.itsaky.androidide.debugger.connection.shizuku.ShizukuSubPath
import com.itsaky.androidide.debugger.connection.shizuku.ShizukuSubPathResolver
import com.itsaky.androidide.utils.ILogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class ShizukuConnection(
    target: DebugTarget,
    val settings: DebugConnectionSettings,
    private val probe: ShizukuProbe? = null,
    private val binderClient: ShizukuBinderClient? = null,
    private val fdTransporter: ShizukuFdTransporter? = null,
    private val socksClient: ShizukuSocksClient? = null,
    private val resolver: ShizukuSubPathResolver? = null,
    private val retryPolicy: ConnectionRetryPolicy = ConnectionRetryPolicy(
        maxAttempts = settings.retryMaxAttempts,
        initialDelayMs = settings.retryInitialDelayMs,
    ),
) : BaseDebugConnection(ConnectionType.Shizuku, target) {

    private val log = ILogger.ROOT

    override val capabilities: Set<ConnectionCapability> = setOf(
        ConnectionCapability.CanInstallInHost,    // 注入 host plugin (InHostPlugin 路径)
        ConnectionCapability.CanReadProcNet,      // 读 /proc/<host_pid>/fd/ (Binder 路径)
        ConnectionCapability.CanExposeSocks,      // 起 SOCKS5 出口 (Socks 路径)
        ConnectionCapability.NeedsHostForeground,  // WifiAdb / InHostPlugin 路径
    )

    // ---- 注入解析: 优先用构造注入,其次懒加载生产实现 ----
    private val probeImpl: ShizukuProbe by lazy { probe ?: DefaultShizukuProbe() }
    private val binderImpl: ShizukuBinderClient by lazy { binderClient ?: ShizukuBinderClient.create() }
    private val fdImpl: ShizukuFdTransporter by lazy { fdTransporter ?: ShizukuFdTransporter.create() }
    private val socksImpl: ShizukuSocksClient by lazy { socksClient ?: ShizukuSocksClient() }
    private val resolverImpl: ShizukuSubPathResolver by lazy {
        resolver ?: ShizukuSubPathResolver(probeImpl, listOf())
    }

    // ---- 运行时状态 ----
    @Volatile private var resolvedSubPath: ShizukuSubPath? = null
    @Volatile private var socket: Socket? = null
    private val sessionIdGenerator = AtomicLong(System.currentTimeMillis())
    private val incoming = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)

    // ---- resolve ----

    override suspend fun resolve(): Result<ResolveInfo> {
        // 1) 探测 Shizuku 状态
        val status = probeImpl.probe()
        if (!status.isRunning) {
            transitionTo(ConnectionState.Closed(ConnectionError.PermissionDenied))
            return Result.failure(IOException(status.notRunningReason ?: "Shizuku not running"))
        }
        if (!status.isGranted) {
            // 拉权限请求
            val granted = probeImpl.requestPermissionIfNeeded(
                timeoutMs = settings.shizuku.binderTimeoutMs,
            )
            if (!granted) {
                transitionTo(ConnectionState.Closed(ConnectionError.PermissionDenied))
                return Result.failure(IOException("Shizuku permission denied"))
            }
        }
        // 2) 选 subPath
        val subPath = resolverImpl.resolve(settings.shizuku.subPath, target)
        resolvedSubPath = subPath
        log.info("ShizukuConnection: resolved subPath={}", subPath)
        transitionTo(ConnectionState.Connecting)
        return Result.success(
            ResolveInfo(
                transportKind = subPath.name,
                endpoint = "shizuku:${subPath.name}",
                requiresHostRunning = subPath != ShizukuSubPath.WifiAdb,
            )
        )
    }

    // ---- connect ----

    override suspend fun connect(): Result<Unit> {
        val subPath = resolvedSubPath
            ?: return Result.failure(IllegalStateException("connect() before resolve()"))
        transitionTo(ConnectionState.Connecting)
        // connect() 阶段只做"准备",具体 attach 留到 attach()。
        // 4 条路径的准备:
        //   WifiAdb: 不需要, 让 attach() 走 AidlSocketConnection
        //   Binder: 不需要, 让 attach() 调 ShizukuBinderClient.newProcess
        //   InHostPlugin: 调 ShizukuBinderClient.bindUserService 拉 host plugin
        //   Socks: 调 ShizukuBinderClient.newProcess 启动 SOCKS5 server
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                when (subPath) {
                    ShizukuSubPath.WifiAdb -> { /* nothing to do */ }
                    ShizukuSubPath.Binder -> { /* nothing to do, prepare in attach */ }
                    ShizukuSubPath.InHostPlugin -> {
                        // host plugin 注入: Shizuku 把 IDE 的 user service 注入 host 进程,
                        // 进程名 = target package name。ComponentName 是 IDE 自己定义的
                        // ShizukuServiceConnection 实现 (子项目 8 一起提供)。
                        val hostPlugin = android.content.ComponentName(
                            "com.itsaky.androidide",
                            "com.itsaky.androidide.debugger.connection.shizuku.IdeShizukuUserService",
                        )
                        binderImpl.bindUserService(hostPlugin, target.packageName)
                    }
                    ShizukuSubPath.Socks -> {
                        // 由 attach 阶段 Socks 客户端连接
                    }
                    ShizukuSubPath.Auto -> error("Auto should have been resolved by resolve()")
                }
            }.onFailure { log.warn("ShizukuConnection: connect attempt failed: {}", it.message) }
        }
        return attempt.onSuccess {
            transitionTo(ConnectionState.Handshaking)
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapConnectError(t)))
        }
    }

    // ---- attach ----

    override suspend fun attach(): Result<AttachInfo> {
        val subPath = resolvedSubPath
            ?: return Result.failure(IllegalStateException("attach() before resolve()"))
        transitionTo(ConnectionState.Handshaking)
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                when (subPath) {
                    ShizukuSubPath.WifiAdb -> attachViaAidlStyle()
                    ShizukuSubPath.Binder -> attachViaBinder()
                    ShizukuSubPath.InHostPlugin -> attachViaInHostPlugin()
                    ShizukuSubPath.Socks -> attachViaSocks()
                    ShizukuSubPath.Auto -> error("Auto should have been resolved")
                }
            }.onFailure { log.warn("ShizukuConnection: attach attempt failed: {}", it.message) }
        }
        return attempt.onSuccess { info ->
            val sock = socket
            if (sock == null) {
                transitionTo(ConnectionState.Closed(ConnectionError.IoFailure(IOException("attach returned but socket is null"))))
                return@onSuccess
            }
            transitionTo(ConnectionState.Attached(info.pid, info.jdwpSessionId))
            startReadLoop(sock)
        }.onFailure { t ->
            transitionTo(ConnectionState.Closed(mapAttachError(t)))
        }
    }

    // ---- 4 个子路径的 attach 细节 ----

    private suspend fun attachViaAidlStyle(): AttachInfo {
        // 复用 AidlSocketConnection 同款流程
        // 这里简化: 临时构造一个 AidlSocketConnection 走一遍 resolve/connect/attach
        val aidlSettings = settings.copy(
            aidlSocket = settings.aidlSocket.copy(requireHostForeground = false)
        )
        val aidl = AidlSocketConnection(
            target = target,
            settings = aidlSettings,
            hostLauncher = null,
            processProbe = null,
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 0L),
        )
        try {
            aidl.resolve()
            val cr = aidl.connect()
            if (cr.isFailure) throw cr.exceptionOrNull()!!
            val ar = aidl.attach()
            if (ar.isFailure) throw ar.exceptionOrNull()!!
            socket = aidl.attachedSocket()
            return ar.getOrNull()!!
        } catch (t: Throwable) {
            // cleanup aidl 自己的 ServerSocket, 避免端口泄漏
            runCatching { aidl.release() }
            throw t
        }
    }

    private suspend fun attachViaBinder(): AttachInfo {
        // 走 Shizuku newProcess 起 attach agent 进程 + 收 fd
        // host runtime 部分依赖子项目 8, 当前 stub: 抛 NotImplementedError
        throw UnsupportedOperationException(
            "Shizuku Binder 子路径需要 host runtime (子项目 8) 配合, 暂未实装"
        )
    }

    private suspend fun attachViaInHostPlugin(): AttachInfo {
        // 走 Shizuku bindUserService 拉 host plugin + plugin reverse-connect
        // host plugin 部分依赖子项目 8, 当前 stub: 抛 NotImplementedError
        throw UnsupportedOperationException(
            "Shizuku InHostPlugin 子路径需要 host runtime (子项目 8) 配合, 暂未实装"
        )
    }

    private suspend fun attachViaSocks(): AttachInfo {
        // 走 Shizuku newProcess 启动 SOCKS5 server + IDE 当 SOCKS5 客户端
        // SOCKS5 server 部分依赖子项目 8, 当前 stub: 抛 NotImplementedError
        throw UnsupportedOperationException(
            "Shizuku Socks 子路径需要 host runtime (子项目 8) 配合, 暂未实装"
        )
    }

    // ---- detach / 字节流 / 钩子 / 释放 ----

    override suspend fun detach() {
        val sock = socket
        socket = null
        if (sock != null) {
            runCatching {
                val out = sock.getOutputStream()
                val cmd = AidlJdwpProtocol.buildVmVersionCommand(0)
                cmd[10] = 2 // VM.Dispose
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
                log.debug("ShizukuConnection: read loop ended: {}", t.message)
            }
        }, "ShizukuConnection-read").apply { isDaemon = true; start() }
    }

    private fun mapConnectError(t: Throwable): ConnectionError = when (t) {
        is IOException -> ConnectionError.IoFailure(t)
        else -> ConnectionError.Unknown(t)
    }

    private fun mapAttachError(t: Throwable): ConnectionError {
        val msg = t.message.orEmpty()
        return when {
            msg.contains("Bad handshake", ignoreCase = true) -> ConnectionError.JdwpHandshakeFailed
            msg.contains("permission", ignoreCase = true) -> ConnectionError.PermissionDenied
            t is IOException -> ConnectionError.IoFailure(t)
            else -> ConnectionError.Unknown(t)
        }
    }
}
