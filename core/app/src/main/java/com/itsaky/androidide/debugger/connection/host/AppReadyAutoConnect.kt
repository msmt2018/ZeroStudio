/*
 *  ZeroStudio IDE - 断点调试器连接层
 *
 *  AppReadyAutoConnect: 端到端集成的自动 attach 协调器 (子项目 9b)。
 *
 *  背景:
 *    子项目 1-8 完成了 6 选 1 连接方案, 但端到端集成存在"宿主应用启动后
 *    IDE 端断点调试器没反应"问题。
 *
 *    现有:
 *    - [AppReadySignalWatcher] 监听 logcat "READY pkg=... jdwp=PORT" 信号,
 *      但只通知, 不实际 attach
 *    - [AutoAttachManager] 只在 IDE 重启 + 同 packageName 时按保存的
 *      host:port 拉起旧连接, 不支持新连接层
 *    - 用户手动启动 host app (如点 launcher 图标), IDE 端完全不响应
 *
 *  本类协调:
 *    - 输入 1: [AppReadySignalWatcher] (logcat "READY" 信号) - 知道 host app
 *              packageName + JDWP port
 *    - 输入 2: [HostBridgeServer] (host 端反连 + HELLO) - 知道 host app 已
 *              主动反连到 IDE LocalServerSocket
 *    - 行为: 收到任一信号, 找匹配的 IDebugConnection, 触发 resolve+connect+attach
 *
 *  策略:
 *    - 默认连接方案: [ConnectionType.AidlSocket] (免 root 通用 fallback)
 *    - AIDL+Socket 路径: 走 HostBridgeServer 的反连 (host 端 HostAttachAgent
 *      反连) 而不是 TCP ServerSocket 路径
 *    - 如果 host 端只发了 "READY" 而没反连 LocalServerSocket (旧 host app),
 *      退化到 TCP connect 直连 host jdwpPort (走"传统" AutoAttachManager 路径)
 *
 *  使用:
 *    - IDE 启动时构造, 启动监听
 *    - 重复触发: 防抖 (1s 内只触发一次 attach)
 *    - 失败: log warn, 不重试 (避免 host 未真正启动时刷错)
 */

package com.itsaky.androidide.debugger.connection.host

import com.itsaky.androidide.debugger.AppReadySignalWatcher
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionRegistry
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.IDebugConnection
import com.itsaky.androidide.utils.ILogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 协调自动 attach 流程的接口。
 */
interface AutoConnectListener {
    /**
     * 决定 host app 启动后用哪个连接方案 (默认 = AidlSocket)。
     * 子类可改: 读用户偏好 / 当前调试方案 / 设备能力。
     */
    fun pickConnectionType(packageName: String, hint: AutoConnectHint): ConnectionType

    /**
     * attach 完成后回调, 通知 UI 切换。
     * @param source 触发来源 ("logcat" / "bridge"), 供 UI 显示 "来自 logcat 信号" 等
     */
    fun onAttachSuccess(
        packageName: String,
        conn: IDebugConnection,
        info: com.itsaky.androidide.debugger.connection.AttachInfo,
        source: String,
    )

    /**
     * attach 失败回调, 用于 UI 显示错误。
     * @param source 触发来源 ("logcat" / "bridge")
     */
    fun onAttachFailed(
        packageName: String,
        conn: IDebugConnection,
        error: Throwable,
        source: String,
    )
}

/**
 * 自动 attach 时的提示信息 (决定连接类型 / 超时等)。
 */
data class AutoConnectHint(
    /** logcat "READY" 信号带过来的 jdwp port, 如果有 */
    val jdwpPort: Int?,
    /** host 端反连的 LocalSocket 名字 (HostBridgeServer 提供), 如果有 */
    val localSocketName: String?,
    /** build variant (debug/release) */
    val variant: String?,
)

/**
 * 默认实现: 总是选 AidlSocket, 失败就 log。
 */
class DefaultAutoConnectListener : AutoConnectListener {
    private val log = ILogger.ROOT
    override fun pickConnectionType(packageName: String, hint: AutoConnectHint): ConnectionType {
        return ConnectionType.AidlSocket
    }
    override fun onAttachSuccess(
        packageName: String,
        conn: IDebugConnection,
        info: com.itsaky.androidide.debugger.connection.AttachInfo,
        source: String,
    ) {
        log.info("AppReadyAutoConnect: [{}] attached to {} (jdwp={})", source, packageName, info.jdwpDescription)
    }
    override fun onAttachFailed(
        packageName: String,
        conn: IDebugConnection,
        error: Throwable,
        source: String,
    ) {
        log.warn("AppReadyAutoConnect: [{}] attach to {} failed: {}", source, packageName, error.message)
    }
}

/**
 * AppReadyAutoConnect: 协调 AppReadySignalWatcher + HostBridgeServer, 在 host
 * app 启动后自动用新连接层发起 attach。
 *
 * 设计要点 (便于测试):
 *   - start() 负责启动 watcher / bridge 并把它们的回调转发到 onLogcatSignal /
 *     onBridgeConnection 公开方法
 *   - 公开方法 (onLogcatSignal / onBridgeConnection) 接收协调入口,测试时
 *     可直接调用,不必启动真 watcher / bridge
 *   - IDebugConnection 工厂: 默认用 DebugConnectionRegistry.create, 可注入
 *     connectionFactory 替换 (测试用)
 *
 * @param settings DebugConnectionSettings (用于构造 fallback AidlSocketConnection)
 * @param listener AutoConnectListener
 * @param connectionFactory 工厂 (生产: DebugConnectionRegistry.create)
 * @param debounceMs 防抖 (1s 内只触发一次 attach)
 */
class AppReadyAutoConnect(
    private val settings: DebugConnectionSettings,
    private val listener: AutoConnectListener = DefaultAutoConnectListener(),
    private val debounceMs: Long = 1_000L,
    private val connectionFactory: (
        type: ConnectionType,
        target: DebugTarget,
        settings: DebugConnectionSettings,
    ) -> IDebugConnection = ::defaultConnectionFactory,
) {
    private val log = ILogger.ROOT
    private val running = AtomicBoolean(false)
    /**
     * Per-packageName 最后调度时间 (ms)。用 map 不用全局变量, 避免
     * packageA 触发后 100ms 内 packageB 也被 debounce 误跳过。
     */
    private val lastScheduleAtByPkg = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingJobs = CopyOnWriteArrayList<Job>()
    /** packageName -> 当前活跃的 connection (用于 attach 复用, 避免每次创建) */
    private val activeByPkg = ConcurrentHashMap<String, IDebugConnection>()

    @Volatile private var watcher: AppReadySignalWatcher? = null
    @Volatile private var bridgeServer: HostBridgeServer? = null

    /**
     * 启动: 同时挂 AppReadySignalWatcher (logcat) + HostBridgeServer (反连)。
     * 重复调用是 no-op。
     */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        log.info("AppReadyAutoConnect: starting")

        // 1) logcat "READY" 信号
        val w = AppReadySignalWatcher()
        w.setListener(object : AppReadySignalWatcher.Listener {
            override fun onAppReady(packageName: String, jdwpPort: Int, variant: String?) {
                onLogcatSignal(packageName, jdwpPort, variant)
            }
        })
        w.start()
        watcher = w

        // 2) HostBridgeServer 反连
        //    用固定名字 [HostBridgeServer.WELL_KNOWN_NAME], 跟宿主 app 的
        //    manifest placeholder (由 IdeDebuggerInitScriptPlugin 注入) 对齐。
        //    之前用 defaultName() = "ide-debug-bridge-<uid>", 但宿主 app 进程
        //    uid 跟 IDE 不同, 双方名字对不齐, 反连永远连不上。
        val name = HostBridgeServer.WELL_KNOWN_NAME
        val b = HostBridgeServer(name)
        b.setListener { conn -> onBridgeConnection(conn) }
        try {
            b.start()
        } catch (t: Throwable) {
            log.warn("AppReadyAutoConnect: failed to start HostBridgeServer: {}", t.message)
        }
        bridgeServer = b
    }

    /**
     * 停止: 取消所有 pending jobs, 关 watcher + bridge server。
     */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        log.info("AppReadyAutoConnect: stopping")
        for (j in pendingJobs) j.cancel()
        pendingJobs.clear()
        runCatching { watcher?.stop() }
        runCatching { bridgeServer?.stop() }
        watcher = null
        bridgeServer = null
    }

    /**
     * logcat "READY" 信号的协调入口 (公开, 便于测试直接调用)。
     */
    fun onLogcatSignal(packageName: String, jdwpPort: Int, variant: String?) {
        schedule(
            packageName = packageName,
            hint = AutoConnectHint(
                jdwpPort = jdwpPort,
                localSocketName = bridgeServer?.localSocketName,
                variant = variant,
            ),
            source = "logcat",
        )
    }

    /**
     * HostBridgeServer 反连的协调入口 (公开, 便于测试直接调用)。
     */
    fun onBridgeConnection(conn: HostConnection) {
        schedule(
            packageName = conn.hello.packageName,
            hint = AutoConnectHint(
                jdwpPort = null,  // logcat 信号会带, 这里不重复
                localSocketName = bridgeServer?.localSocketName,
                variant = null,
            ),
            source = "bridge",
        )
    }

    // ---- 私有 ----

    private fun schedule(packageName: String, hint: AutoConnectHint, source: String) {
        val now = System.currentTimeMillis()
        val last = lastScheduleAtByPkg[packageName] ?: 0L
        if (now - last < debounceMs) {
            log.debug(
                "AppReadyAutoConnect: debounced for pkg={} ({}ms since last), skipping",
                packageName, now - last,
            )
            return
        }
        lastScheduleAtByPkg[packageName] = now
        val job = scope.launch {
            // 给 host app 一点时间完成反连
            delay(300L)
            doAttach(packageName, hint, source)
        }
        pendingJobs.add(job)
        job.invokeOnCompletion { pendingJobs.remove(job) }
    }

    private suspend fun doAttach(packageName: String, hint: AutoConnectHint, source: String) {
        log.info("AppReadyAutoConnect: source={} pkg={} hint={}", source, packageName, hint)
        val type = listener.pickConnectionType(packageName, hint)
        val target = DebugTarget(packageName = packageName, mainActivity = null)
        val conn = activeByPkg[packageName]
            ?: connectionFactory(type, target, settings).also { activeByPkg[packageName] = it }
        // resolve -> connect -> attach
        val r = conn.resolve()
        if (r.isFailure) {
            listener.onAttachFailed(packageName, conn, r.exceptionOrNull()!!, source)
            activeByPkg.remove(packageName, conn)
            return
        }
        val c = conn.connect()
        if (c.isFailure) {
            listener.onAttachFailed(packageName, conn, c.exceptionOrNull()!!, source)
            activeByPkg.remove(packageName, conn)
            return
        }
        val a = conn.attach()
        if (a.isFailure) {
            listener.onAttachFailed(packageName, conn, a.exceptionOrNull()!!, source)
            activeByPkg.remove(packageName, conn)
            return
        }
        listener.onAttachSuccess(packageName, conn, a.getOrNull()!!, source)
    }
}

private fun defaultConnectionFactory(
    type: ConnectionType,
    target: DebugTarget,
    settings: DebugConnectionSettings,
): IDebugConnection = DebugConnectionRegistry.create(type, target, settings)
