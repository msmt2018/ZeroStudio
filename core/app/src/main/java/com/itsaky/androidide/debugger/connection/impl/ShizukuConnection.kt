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
import com.itsaky.androidide.debugger.connection.shizuku.UserServiceHandle
import com.itsaky.androidide.debugger.connection.shizuku.probeHostPluginUsable
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
     * server.accept() 是阻塞且无 timeout API, 必须用 coroutine withTimeoutOrNull
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
        //
        // Phase 16: 注入 hostPluginProbe (Phase 14 实装) 给 InHostPluginCapability +
        //   SocksCapability, 不再走默认 placeholder `{ _ -> true }`。probe 走
        //   `probeHostPluginUsable` 真 bind host 端 IdeShizukuSocksUserService
        //   user service 1.5s timeout, host 没装 aar 立即返 false (InHostPlugin /
        //   Socks 路径不可用, Auto 模式跳过)。
        val apiVersion = runCatching { probeImpl.probe().serverApiVersion }.getOrDefault(-1)
        val hostPkg = target.packageName
        resolver ?: ShizukuSubPathResolver(
            probeImpl,
            defaultShizukuSubPathCapabilities(
                serverApiVersion = apiVersion,
                hostPluginProbe = { _: DebugTarget ->
                    if (hostPkg.isBlank()) {
                        false
                    } else {
                        val probeComponent = android.content.ComponentName(
                            hostPkg,
                            "com.itsaky.androidide.zerostudio.ide.debugger.host.IdeShizukuSocksUserService",
                        )
                        // 1.5s timeout 探测 host 端 IdeShizukuSocksUserService 能否
                        //   bind - 走得到代表 host 装了 aar, InHostPlugin (走 HostPluginService)
                        //   跟 Socks 路径都依赖此 aar, 共享 probe 结果。
                        // 注: hostPluginProbe 签名是 (DebugTarget) -> Boolean (非 suspend),
                        //   probeHostPluginUsable 是 suspend, 走 runBlocking 同步桥接 (capability
                        //   probeUsable 本身就是同步调用, 阻塞 resolver 调用线程)。
                        kotlinx.coroutines.runBlocking {
                            probeHostPluginUsable(
                                componentName = probeComponent,
                                processName = hostPkg,
                                timeoutMs = 1_500L,
                            )
                        }
                    }
                },
            ),
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
    // Phase 16: InHostPlugin / Socks / Binder (fallback) 路径下走 Shizuku.bindUserService
    //   拉 host 端 user service 拿 handle, 存这里给 detach / release unbind。Phase 15
    //   之前 caller 拿不到 ServiceConnection, host 端 user service 永远 leak (Socks
    //   路径下会留 SOCKS5 server 占端口, InHostPlugin 路径下会留 LocalServerSocket 占
    //   abstract namespace)。
    @Volatile private var userServiceHandle: UserServiceHandle? = null
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
        // Phase 16: bindUserService 现在返 UserServiceHandle (含 binder + ServiceConnection),
        //   ServiceConnection 存到 userServiceHandle 字段, detach / release 时 unbind。
        val handle = binderImpl.bindUserService(hostPlugin, target.packageName)
        if (handle.binder == null || !handle.binder.pingBinder()) {
            throw IOException("Shizuku InHostPlugin: user service binder dead")
        }
        userServiceHandle = handle
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
            // 3) accept with timeout: server.accept() 是阻塞且无 timeout API,
            //    用 withTimeoutOrNull + 异步 accept 来加超时保护, 否则 host
            //    一旦不反连 IDE 端会无限阻塞。
            val client = withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(INHOSTPLUGIN_ACCEPT_TIMEOUT_MS) {
                    try {
                        server.accept()
                    } catch (t: Throwable) {
                        log.warn("Shizuku InHostPlugin: accept() failed: {}", t.message)
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
            // Phase 16: 失败路径 unbind handle, host 端 user service 别 leak
            runCatching {
                val h = userServiceHandle
                userServiceHandle = null
                if (h != null) binderImpl.unbindUserService(h)
            }
            throw t
        }
    }

    /**
     * 子项目 4 - Binder 路径实装。
     *
     * **Phase 13d 限制 (留 TODO 文档化)**:
     *   Shizuku 13+ 把 [rikka.shizuku.Shizuku.transferFileDescriptor] 设 package-private,
     *   第三方 IDE 端不能直接调 ([ShizukuBinderClient.transferFileDescriptor] 抛
     *   `UnsupportedOperationException`)。所以 Binder 路径走 fallback: 复用
     *   [attachViaInHostPlugin] 同款实装 (走 `Shizuku.bindUserService` + host 端
     *   `HostPluginService` reverse-connect 回 IDE `LocalServerSocket`)。
     *
     *   唯一区别: transport 名字保留 Binder 供 UI 显示 (跟 InHostPlugin 区分开),
     *   底层逻辑复用 InHostPlugin。
     *
     * **Shizuku 14+ 真路径 TODO** (Phase 13d 后续):
     *   1) host 端 user service (e.g. `BinderTransportService`) 跑 root 进程 attach
     *      host app 的 JDWP agent, open `/proc/<host_pid>/fd/<jdwp_socket>` 拿 fd
     *   2) host 端 user service 把 fd 写回 Parcel
     *   3) IDE 端 `ShizukuBinderClient.transferFileDescriptor` 走 Shizuku 14+ 公共
     *      API (如果官方开放) 拿回 ParcelFileDescriptor
     *   4) `ShizukuFdTransporter.toSocket(pfd)` 包成 [com.itsaky.androidide.debugger.connection.shizuku.PfdSocket]
     *   5) 走 JDWP 握手 + VM.Version
     *
     *   优先级: 14+ 走真 transferFileDescriptor, 13+ 继续走 InHostPlugin fallback。
     *
     * **SocksServiceUserService adapter** (Phase 13d 关联):
     *   Socks 路径的 user service adapter (`IdeShizukuSocksUserService`) 已在
     *   Phase 12y + 13c 合并实装 (走 `ISocksControl` binder transact 协议传 port
     *   + detach 释放)。BInder 路径 14+ 真实现后, 同样需要
     *   `BinderTransportService` user service (跟 Socks 路径 adapter 风格一致)。
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
        val handle = binderImpl.bindUserService(hostPlugin, target.packageName)
        // Phase 16: bindUserService 返 UserServiceHandle, ServiceConnection 存到
        //   userServiceHandle 字段, detach / release 时 unbind。handle.binder
        //   单独拿出来用 (走 ISocksControl transact)。
        if (handle.binder == null || !handle.binder.pingBinder()) {
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
        val actualSocksPort = socksControlTransact.setSocksPort(handle.binder, requestedSocksPort)
        socksControlBinderRef = handle.binder  // 留 detach 用 stopSocks
        userServiceHandle = handle              // 留 detach / release 用 unbindUserService
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
            // Phase 16: Socks 路径下 sock.connect 失败, 已 bind host 端 user service
            //   (host IdeShizukuSocksUserService 已启 SOCKS5 server 占端口), 要:
            //   1) stopSocks 走 ISocksControl transact 停 SOCKS5 server
            //   2) unbindUserService 释放 host 端 user service
            // 顺序: 先 stopSocks 再 unbind (unbind 后 binder 失效 stopSocks 失败)。
            runCatching {
                val ref = socksControlBinderRef
                socksControlBinderRef = null
                if (ref != null) socksControlTransact.stopSocks(ref)
            }
            runCatching {
                val h = userServiceHandle
                userServiceHandle = null
                if (h != null) binderImpl.unbindUserService(h)
            }
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
        // Phase 16: unbind host 端 user service (InHostPlugin / Socks / Binder 路径),
        //   之前 detach / release 漏 unbind, host 端 service 永远 leak (Socks 路径下
        //   会留 SOCKS5 server 占端口, InHostPlugin 路径下会留 LocalServerSocket 占
        //   abstract namespace)。bail out 在 stopSocks 之后, 顺序无所谓 (Socks 路径
        //   两者都 unbind, InHostPlugin 路径只 unbind user service, Socks 路径
        //   stopSocks + unbind 都要)。
        runCatching {
            val handle = userServiceHandle
            userServiceHandle = null
            if (handle != null) binderImpl.unbindUserService(handle)
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
        // Phase 16: release 时也 unbind host 端 user service (兜底, 跟 detach 同款)。
        runCatching {
            val handle = userServiceHandle
            userServiceHandle = null
            if (handle != null) binderImpl.unbindUserService(handle)
        }
        localSocket = null
        localInput = null
        localOutput = null
        socket = null
        inHostPluginServer = null
        super.release()
    }

    // ---- 私有 ----

    /**
     * Phase 5 死代码清理: 之前留 startReadLoop(sock) 跟 startReadLoopFromStream(input)
     * 给 attach 完成后调, 跟 JdwpClient 内部 read 抢同一 input stream 导致字节
     * 重复 emit, Phase 12m 之后全部 connection 不再调. 删, 留注释说明
     * JdwpClient 接管 socket 内部 read, BaseDebugConnection 子类不要再走
     * startReadLoop.
     *
     * ShizukuConnection 的 incoming SharedFlow 仍保留 (receiveJdwp() 暴露给
     * 上层 send/receive 路径用), 但没有上游 emit, 等于 dead flow, Phase 10
     * e2e 测试时再决定要不要删.
     */

    /**
     * Phase 6: 跟 AdbForwardConnection.mapXxxError 同款细分模式.
     * ShizukuConnection 走 Shizuku user service, 4 类错误来源:
     *   - Shizuku 没启 / 没授权 -> PermissionDenied
     *   - user service 不可用 -> IoFailure
     *   - transferFileDescriptor 不可用 (13+ 限制) -> 走 fallback
     *   - SOCKS5 server 启失败 -> IoFailure
     */
    private fun mapConnectError(t: Throwable): ConnectionError = when (t) {
        is java.net.ConnectException -> ConnectionError.NetworkUnreachable
        is java.net.NoRouteToHostException -> ConnectionError.NetworkUnreachable
        is java.net.SocketTimeoutException -> ConnectionError.Timeout
        is java.io.IOException -> ConnectionError.IoFailure(t)
        else -> ConnectionError.Unknown(t)
    }

    private fun mapAttachError(t: Throwable): ConnectionError {
        val msg = t.message.orEmpty()
        return when {
            // Shizuku 权限拒绝
            msg.contains("permission", ignoreCase = true) ||
                msg.contains("shizuku", ignoreCase = true) && msg.contains("denied", ignoreCase = true) ->
                ConnectionError.PermissionDenied
            // Shizuku server 死了 / 不可用
            msg.contains("shizuku service not running", ignoreCase = true) ||
                msg.contains("shizuku binder dead", ignoreCase = true) ->
                ConnectionError.IoFailure(t)
            // Shizuku 14+ transferFileDescriptor 限制 (Phase 13d 走 fallback)
            msg.contains("transferFileDescriptor", ignoreCase = true) ->
                ConnectionError.IoFailure(t)
            // SOCKS5 server 启失败 (Phase 12y ISocksControl 协议)
            msg.contains("SocksControl", ignoreCase = true) ->
                ConnectionError.IoFailure(t)
            // JDWP 14 字节握手失败 / EOF
            msg.contains("Bad handshake", ignoreCase = true) ||
                msg.contains("EOF during handshake", ignoreCase = true) ->
                ConnectionError.JdwpHandshakeFailed
            // 通用 timeout
            msg.contains("timeout", ignoreCase = true) -> ConnectionError.Timeout
            t is java.io.IOException -> ConnectionError.IoFailure(t)
            else -> ConnectionError.Unknown(t)
        }
    }
}
