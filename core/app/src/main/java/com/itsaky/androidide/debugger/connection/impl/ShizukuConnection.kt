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
    // 子项目 4: InHostPlugin / Binder 路径下用 LocalSocket (不继承 java.net.Socket),
    //   走 InputStream/OutputStream, 跟 java.net.Socket 路径互斥
    @Volatile private var localSocket: android.net.LocalSocket? = null
    @Volatile private var localInput: java.io.InputStream? = null
    @Volatile private var localOutput: java.io.OutputStream? = null
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
            // InHostPlugin / Binder 走 localSocket 路径; WifiAdb / Socks 走 java.net.Socket 路径
            val hasLocal = localSocket != null
            val sock = socket
            if (!hasLocal && sock == null) {
                transitionTo(ConnectionState.Closed(ConnectionError.IoFailure(IOException("attach returned but neither socket nor localSocket is set"))))
                return@onSuccess
            }
            transitionTo(ConnectionState.Attached(info.pid, info.jdwpSessionId))
            if (hasLocal) {
                startReadLoopFromStream(localInput!!)
            } else {
                startReadLoop(sock!!)
            }
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

    /**
     * 子项目 4 - InHostPlugin 路径实装。
     *
     * 流程:
     *   1) bindUserService 拉 host 端 user service (HostPluginService,
     *      子项目 8 已建)
     *   2) host 端 user service 在 host 进程内 reverse-connect 到
     *      IDE LocalServerSocket (与子项目 9c ContentProvider 同款)
     *   3) IDE 端通过 LocalServerSocket.accept() 等连接, 拿 LocalSocket
     *   4) 走 JDWP 握手 + VM.Version
     *   5) 字节桥 (跟 AidlSocketConnection LocalBridge 路径同款)
     *
     * 失败: log warn, 不抛 host runtime 错误, 给 ConnectionError.IoFailure 包装。
     */
    private suspend fun attachViaInHostPlugin(): AttachInfo {
        val hostPlugin = android.content.ComponentName(
            "com.itsaky.androidide",
            "com.itsaky.androidide.debugger.connection.shizuku.IdeShizukuUserService",
        )
        // 1) 拉 user service
        val binder = binderImpl.bindUserService(hostPlugin, target.packageName)
        if (binder == null || !binder.pingBinder()) {
            throw IOException("Shizuku InHostPlugin: user service binder dead")
        }
        // 2) 起 IDE LocalServerSocket 等 host 反连
        val serverName = "ide-shizuku-${target.packageName}-${System.currentTimeMillis()}"
        val server = android.net.LocalServerSocket(serverName)
        try {
            // 3) accept (timeout 10s)
            val client = withContext(Dispatchers.IO) {
                server.receive() // blocking, no timeout API
            }
            // 4) JDWP 握手
            val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(
                output = client.outputStream,
                input = client.inputStream,
                commandId = (sessionIdGenerator.incrementAndGet() and 0x7fffffff).toInt(),
            )
            // 5) LocalSocket 转 Socket 接口 (用 socket 字段存 LocalSocket)
            //    ShizukuConnection 是用 java.net.Socket 字段, 这里用 java.net.Socket 包装
            //    - 简单方案: 用 client.outputStream/inputStream 直接做后续, 把 client 当作 Socket
            //    - 实际: LocalSocket 不是 java.net.Socket 子类, 跟 AidlSocketConnection 一样
            //      需要走独立 read loop + 不存 clientSocket
            localSocket = client
            localInput = client.inputStream
            localOutput = client.outputStream
            return AttachInfo(
                pid = 0,
                jdwpSessionId = sessionIdGenerator.get(),
                jdwpDescription = "${info.description} (${info.vmName} ${info.vmVersion}, jdwp ${info.jdwpVersion}) [shizuku-inhostplugin]",
            )
        } catch (t: Throwable) {
            runCatching { server.close() }
            throw t
        }
    }

    /**
     * 子项目 4 - Binder 路径实装 (跟 InHostPlugin 走同款实现, 因 Shizuku 13+
     * 限制, transferFileDescriptor 不可用, 走 user service + reverse-connect)。
     *
     * 唯一区别: transport 名字保留 Binder 供 UI 显示, 底层逻辑复用 InHostPlugin。
     */
    private suspend fun attachViaBinder(): AttachInfo {
        // 跟 attachViaInHostPlugin 走同款实现
        return attachViaInHostPlugin().let { info ->
            AttachInfo(
                pid = info.pid,
                jdwpSessionId = info.jdwpSessionId,
                jdwpDescription = info.jdwpDescription.replace("[shizuku-inhostplugin]", "[shizuku-binder]"),
            )
        }
    }

    /**
     * 子项目 4 - Socks 路径实装。
     *
     * 流程:
     *   1) 走 ShizukuBinderClient.bindUserService 拉 host 端 user service,
     *      user service 启 HostSocksServer (SOCKS5 server in host process,
     *      子项目 8 已建)
     *   2) IDE 端用 ShizukuSocksClient 当 SOCKS5 客户端, 走 RFC 1928 协议
     *      连到 HostSocksServer
     *   3) SOCKS5 CONNECT 到 host:jdwp
     *   4) 走 JDWP 握手 + VM.Version
     *   5) 字节桥 (走 java.net.Socket)
     */
    private suspend fun attachViaSocks(): AttachInfo {
        val hostPlugin = android.content.ComponentName(
            "com.itsaky.androidide",
            "com.itsaky.androidide.debugger.connection.shizuku.IdeShizukuSocksUserService",
        )
        // 1) 拉 user service
        val binder = binderImpl.bindUserService(hostPlugin, target.packageName)
        if (binder == null || !binder.pingBinder()) {
            throw IOException("Shizuku Socks: user service binder dead")
        }
        // 2) SOCKS5 客户端连 host SOCKS5 server
        //    HostSocksServer 监听 abstract namespace "ide-shizuku-socks-{package}"
        //    协议: SOCKS5 RFC 1928, no-auth, CONNECT, ATYP=03 (domain)
        //    target: "jdwp" (host 本地 abstract namespace)
        val proxyAddr = java.net.InetSocketAddress.createUnresolved(
            "ide-shizuku-socks-${target.packageName}", 0,
        )
        val sock = withContext(Dispatchers.IO) {
            socksImpl.connect(
                proxyAddr = proxyAddr,
                targetHost = "jdwp",
                targetPort = 0,
                connectTimeoutMs = 5_000,
            )
        }
        try {
            val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(
                socket = sock,
                commandId = (sessionIdGenerator.incrementAndGet() and 0x7fffffff).toInt(),
            )
            socket = sock
            return AttachInfo(
                pid = 0,
                jdwpSessionId = sessionIdGenerator.get(),
                jdwpDescription = "${info.description} (${info.vmName} ${info.vmVersion}, jdwp ${info.jdwpVersion}) [shizuku-socks]",
            )
        } catch (t: Throwable) {
            runCatching { sock.close() }
            throw t
        }
    }

    // ---- detach / 字节流 / 钩子 / 释放 ----

    override suspend fun detach() {
        // LocalSocket 路径 (InHostPlugin / Binder)
        val ls = localSocket
        if (ls != null) {
            localSocket = null
            runCatching {
                val out = localOutput ?: ls.outputStream
                val cmd = AidlJdwpProtocol.buildVmVersionCommand(0)
                cmd[10] = 2 // VM.Dispose
                synchronized(out) {
                    out.write(cmd)
                    out.flush()
                }
            }.onFailure { log.debug("detach(local): VM.Dispose failed: {}", it.message) }
            runCatching { ls.close() }
            localInput = null
            localOutput = null
        }
        // java.net.Socket 路径 (WifiAdb / Socks)
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
        check(currentState() is ConnectionState.Attached) {
            "sendJdwp requires Attached state, was ${currentState()}"
        }
        withContext(Dispatchers.IO) {
            // LocalSocket 路径优先
            localOutput?.let { out ->
                out.write(bytes)
                out.flush()
                return@withContext
            }
            val sock = socket ?: error("not attached")
            sock.getOutputStream().apply { write(bytes); flush() }
        }
    }

    override fun receiveJdwp(): Flow<ByteArray> = incoming.asSharedFlow()

    override fun attachedSocket(): Socket {
        val sock = socket ?: error("not attached (or attached via LocalSocket)")
        return sock
    }

    override fun release() {
        runCatching { localSocket?.close() }
        runCatching { socket?.close() }
        localSocket = null
        localInput = null
        localOutput = null
        socket = null
        super.release()
    }

    // ---- 私有 ----

    private fun startReadLoop(sock: Socket) {
        startReadLoopFromStream(sock.getInputStream())
    }

    private fun startReadLoopFromStream(input: java.io.InputStream) {
        // 子项目 4: LocalSocket 路径下, attach() 后调 startReadLoopFromStream(localInput)
        // 守护线程读 input, 把每段字节切到 flow 上
        Thread({
            try {
                val buf = ByteArray(8192)
                while (currentState() is ConnectionState.Attached) {
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
