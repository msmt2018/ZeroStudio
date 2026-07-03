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
import com.itsaky.androidide.debugger.connection.shizuku.SocksControlTransact
import android.os.IBinder
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

    /**
     * InHostPlugin 路径下等 host 端 reverse-connect 的超时。
     * server.receive() 是阻塞且无 timeout API, 必须用 coroutine withTimeoutOrNull
     * 保护, 否则 host 端 user service 启动后但 reverse-connect 失败时 IDE 端会
     * 无限阻塞。
     */
    private val INHOSTPLUGIN_ACCEPT_TIMEOUT_MS = 10_000L

    /**
     * InHostPlugin 路径下 IDE LocalServerSocket 名字的根名, host 端
     * [com.itsaky.androidide.zerostudio.ide.debugger.host.HostPluginService.computeIdeSocketName]
     * 拼上 host 进程包名 (target.packageName) 后拼成完整名 (e.g.
     * "ide-shizuku-inhostplugin-com.foo.A")。拼包名避免多 host app 并发
     * 调试时 race (固定名多个 LocalServerSocket bind 会失败)。
     */
    private val INHOSTPLUGIN_SOCKET_NAME_PREFIX = "ide-shizuku-inhostplugin"

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
        // Phase 13a: 之前传 listOf() 空 capabilities, Auto 模式 for 循环空迭代
        // 永远走 fallback WifiAdb, 4 个 ShizukuSubPathCapability 实装完全没接入。
        // 现在传 defaultShizukuSubPathCapabilities(serverApiVersion) 4 个
        // capability, Auto 模式按 WifiAdb / Binder / InHostPlugin / Socks 顺序探测。
        // serverApiVersion 走 lazy probe 后从 ShizukuStatus 取。
        val apiVersion = runCatching { probeImpl.probe().serverApiVersion }.getOrDefault(-1)
        resolver ?: ShizukuSubPathResolver(
            probeImpl,
            defaultShizukuSubPathCapabilities(serverApiVersion = apiVersion),
        )
    }

    // ---- 运行时状态 ----
    @Volatile private var resolvedSubPath: ShizukuSubPath? = null
    @Volatile private var socket: Socket? = null
    // 子项目 4: InHostPlugin / Binder 路径下用 LocalSocket (不继承 java.net.Socket),
    //   走 InputStream/OutputStream, 跟 java.net.Socket 路径互斥
    @Volatile private var localSocket: android.net.LocalSocket? = null
    @Volatile private var localInput: java.io.InputStream? = null
    @Volatile private var localOutput: java.io.OutputStream? = null
    // 子项目 4 修复: InHostPlugin 路径下 accept 用的 LocalServerSocket 必须
    //   在 detach/release 时 close, 否则 abstract namespace 的 socket name 不会
    //   被释放, 下次 attach 会卡住或冲突。
    @Volatile private var inHostPluginServer: android.net.LocalServerSocket? = null
    // Phase 12y + 13c: Socks 路径下走 ISocksControl binder transact 协议,
    //   attachViaSocks 拿 binder 后保存到 socksControlBinderRef, detach / release
    //   时调 socksControlTransact.stopSocks 释放 host 端 SOCKS5 server。
    @Volatile private var socksControlBinderRef: IBinder? = null
    private val socksControlTransact = SocksControlTransact()
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
        //   Binder: 不需要 (跟 InHostPlugin 走同款实装, bindUserService 在 attach)
        //   InHostPlugin: bindUserService 在 attach() 阶段做 (避免重复调用)
        //   Socks: 由 attach 阶段 Socks 客户端连接
        val attempt = retryPolicy.retry { _ ->
            runCatching {
                when (subPath) {
                    ShizukuSubPath.WifiAdb -> { /* nothing to do */ }
                    ShizukuSubPath.Binder -> { /* nothing to do, prepare in attach */ }
                    ShizukuSubPath.InHostPlugin -> { /* bindUserService 在 attach() 阶段做, 避免重复 */ }
                    ShizukuSubPath.Socks -> { /* 由 attach 阶段 Socks 客户端连接 */ }
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
        // 失败: 走 mapAttachError
        attempt.exceptionOrNull()?.let { t ->
            transitionTo(ConnectionState.Closed(mapAttachError(t)))
            return Result.failure(t)
        }
        // 成功但 post-condition 失败: 走 finishAttach (ok=false 分支)
        val info = attempt.getOrNull()!!
        // InHostPlugin / Binder 走 localSocket 路径; WifiAdb / Socks 走 java.net.Socket 路径
        val hasLocal = localSocket != null
        val sock = socket
        return finishAttach(
            info = info,
            ok = hasLocal || sock != null,
            failureMsg = "attach returned but neither socket nor localSocket is set",
            // Phase 12m: 移除默认启 read loop (理由同 AidlSocketConnection /
            // AdbForwardConnection / InnetVmSocksConnection)。attachedSocket()
            // 会被 JdwpClient 拿去, 跟 read loop 抢同 socket.inputStream
            // 会导致字节被 split 丢失。receiveJdwp() flow 仍保留 (接口签名),
            // 没人 collect 时永不 emit, 不影响主路径。
            onAttached = { /* JdwpClient 接管, 不启 read loop */ },
        )
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
        // host 端 user service FQN (定义在 ide-debugger-host AAR, 被 host APK
        // 打包, Shizuku 13+ 通过反射在 host 进程内 load 这个 class)。
        // ComponentName.package 必须是 host 的包名 (target.packageName),
        // 不是 IDE 的包名 - 之前用 "com.itsaky.androidide" 是错的, 改成 target.packageName
        // 后 Shizuku 才能在 host 进程的 classpath 里找到这个 class。
        val hostPlugin = android.content.ComponentName(
            target.packageName,
            "com.itsaky.androidide.zerostudio.ide.debugger.host.HostPluginService",
        )
        // 1) 拉 user service
        val binder = binderImpl.bindUserService(hostPlugin, target.packageName)
        if (binder == null || !binder.pingBinder()) {
            throw IOException("Shizuku InHostPlugin: user service binder dead")
        }
        // 2) 起 IDE LocalServerSocket 等 host 反连
        //    Shizuku 反射加载 HostPluginService 时拿不到 IDE 端 timestamp,
        //    必须用约定名 (拼包名: "${INHOSTPLUGIN_SOCKET_NAME_PREFIX}-${target.packageName}"),
        //    跟 host 端 HostPluginService.computeIdeSocketName() 一致。拼包名避免
        //    多 host app 并发 attach 时 race (固定名多个 LocalServerSocket bind
        //    会失败)。多次 attach 复用同名 socket, IDE 端 release() 时必须 close
        //    server。
        val pkg = target.packageName
        val serverName = if (pkg.isBlank()) {
            INHOSTPLUGIN_SOCKET_NAME_PREFIX
        } else {
            "$INHOSTPLUGIN_SOCKET_NAME_PREFIX-$pkg"
        }
        val server = android.net.LocalServerSocket(serverName)
        inHostPluginServer = server
        try {
            // 3) accept with timeout: server.receive() 是阻塞且无 timeout API,
            //    用 withTimeoutOrNull + 异步 receive 来加超时保护, 否则 host
            //    一旦不反连 IDE 端会无限阻塞。
            val client = withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(INHOSTPLUGIN_ACCEPT_TIMEOUT_MS) {
                    try {
                        server.receive()
                    } catch (t: Throwable) {
                        log.warn("Shizuku InHostPlugin: receive() failed: {}", t.message)
                        null
                    }
                }
            } ?: throw IOException(
                "Shizuku InHostPlugin: host did not reverse-connect within " +
                    "${INHOSTPLUGIN_ACCEPT_TIMEOUT_MS}ms (LocalServerSocket='$serverName')"
            )
            // 4) JDWP 握手
            val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(
                output = client.outputStream,
                input = client.inputStream,
                commandId = (sessionIdGenerator.incrementAndGet() and 0x7fffffff).toInt(),
            )
            // 5) LocalSocket 不是 java.net.Socket 子类, 跟 AidlSocketConnection 一样
            //    需要走独立 read loop + 不存 clientSocket
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
            inHostPluginServer = null
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
     *
     * 端口: 默认 39939 (跟 host 端 IdeShizukuSocksUserService.DEFAULT_SOCKS_PORT
     * 一致), 用户可通过 [com.itsaky.androidide.debugger.connection.DebugConnectionPreferences]
     * 改 `shizukuSocksPort` 覆盖。
     */
    private suspend fun attachViaSocks(): AttachInfo {
        // 1) 拉 user service (host 端 IdeShizukuSocksUserService 启 SOCKS5 server)
        //    同 attachViaInHostPlugin: ComponentName.package 必须是 host 包名,
        //    之前用 "com.itsaky.androidide" (IDE 包) 是错的, host 进程 classpath
        //    里没有这个 class, bindUserService 会失败。
        val hostPlugin = android.content.ComponentName(
            target.packageName,
            "com.itsaky.androidide.zerostudio.ide.debugger.host.IdeShizukuSocksUserService",
        )
        // Phase 12y: 走 binder transact 协议传 socksPort (替代 Phase 12x 的
        // 走 Bundle args, Shizuku 13.1.5 没 .args(Bundle) API)。
        //   1) bindUserService 拿 binder
        //   2) binder.transact(CODE_SET_SOCKS_PORT, port) 启 host 端 SOCKS5 server
        //   3) reply.readInt() 拿 actualPort (用户传 0 时 OS 选随机端口)
        //   4) Socks5Client 连 actualPort
        val binder = binderImpl.bindUserService(hostPlugin, target.packageName)
        if (binder == null || !binder.pingBinder()) {
            throw IOException("Shizuku Socks: user service binder dead")
        }
        val requestedSocksPort = settings.shizuku.socksPort
        if (requestedSocksPort < 0 || requestedSocksPort > 65535) {
            throw IOException(
                "Shizuku Socks: socksPort invalid (got $requestedSocksPort). " +
                    "Set shizukuSocksPort in DebugConnectionPreferences to a valid TCP " +
                    "port (0 = OS picks random)."
            )
        }
        val actualSocksPort = socksControlTransact.setSocksPort(binder, requestedSocksPort)
        socksControlBinderRef = binder  // 留 detach 用 stopSocks
        // 2) SOCKS5 客户端连 host SOCKS5 server
        //    proxyAddr 必须用真 TCP 端口 (Socks5Client 走 java.net.Socket, 不支持
        //    abstract namespace)。实际端口由 host 端 binder 协议告知 (走 ISocksControl
        //    setSocksPort transact)。
        val socksHost = settings.shizuku.socksHost.ifBlank { "127.0.0.1" }
        val proxyAddr = java.net.InetSocketAddress(socksHost, actualSocksPort)
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
                val cmd = AidlJdwpProtocol.buildVmDisposeCommand(0)
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
                val cmd = AidlJdwpProtocol.buildVmDisposeCommand(0)
                synchronized(out) {
                    out.write(cmd)
                    out.flush()
                }
            }.onFailure { log.debug("detach: VM.Dispose failed: {}", it.message) }
            runCatching { sock.close() }
        }
        // LocalServerSocket 路径 (InHostPlugin)
        runCatching { inHostPluginServer?.close() }
        inHostPluginServer = null
        // Phase 12y + 13c: Socks 路径下 stop host 端 SOCKS5 server (走 ISocksControl
        //   binder transact, pingBinder 死了则静默跳过), 释放 socksControlBinderRef
        runCatching {
            val ref = socksControlBinderRef
            socksControlBinderRef = null
            socksControlTransact.stopSocks(ref)
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
        runCatching { inHostPluginServer?.close() }
        // Phase 12y + 13c: release 时也调 stopSocks 兜底 (跟 detach 一样, 但 release
        //   不走 suspend, 调 sync stopSocks 即可)。socksControlTransact.stopSocks
        //   内部已经跑 pingBinder 死了跳过。
        runCatching {
            val ref = socksControlBinderRef
            socksControlBinderRef = null
            socksControlTransact.stopSocks(ref)
        }
        localSocket = null
        localInput = null
        localOutput = null
        socket = null
        inHostPluginServer = null
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
