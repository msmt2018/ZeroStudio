/*
 *  ZeroStudio IDE - 断点调试器连接层
 *
 *  HostBridgeServer: IDE 端 LocalServerSocket 接收 host app 反向连接 (子项目 9a)。
 *
 *  背景:
 *    子项目 1-8 完成了 6 选 1 连接方案的 IDE 端 + host 端 runtime, 但端到端
 *    集成存在"宿主应用启动后 IDE 端断点调试器没反应"问题。
 *    现有 AidlSocketConnection 是"IDE bind ServerSocket -> launch host app
 *    (Intent extra 传 port) -> host 反连"模式, 必须 IDE 先启动 host。
 *    如果用户**手动**启动 host app (如点 launcher 图标), IDE 端不响应。
 *
 *  本类解决这个:
 *    - IDE 启动后 bind 一个 abstract namespace 的 LocalServerSocket, 名字
 *      由 build-time 注入的 Manifest placeholder 决定 (per-project 唯一)
 *    - host app 启动后, 走 HostAttachAgent 反向连这个 LocalServerSocket
 *    - host 端发一行 HELLO 协议头 (HELLO pkg=<pkg> pid=<pid>), IDE 解析后
 *      触发 AppReadyAutoConnect
 *    - accept 的 socket 包装成 LocalSocket 供 AIDL+Socket connection attach 用
 *
 *  设计要点:
 *    - 单实例: IDE 进程内只允许一个 LocalServerSocket (避免冲突)
 *    - 多连接: host 可能多次启动 (调试时), accept 循环
 *    - 超时: accept 设 soTimeout, 关闭时 wait 不阻塞
 *    - HELLO 协议: 简单 key=value 形式, 解析失败丢弃连接
 */

package com.itsaky.androidide.debugger.connection.host

import android.net.LocalServerSocket
import android.net.LocalSocket
import com.itsaky.androidide.utils.ILogger
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 收到的 host 端 HELLO 信息。
 *
 * Phase 13j: 加 [processName] 字段, 兼容 host app multi-process 场景
 * (e.g. android:process=":debug"). ContentProvider 默认在主进程, 但部分
 * host app Manifest 显式指定 :debug / :remote, HELLO 拿到的 pid 是
 * ContentProvider 所在进程 pid, 不是 host app 主进程 pid. IDE 端拿这个
 * pid attach 会 attach 到 :debug 进程 (没有 host app 主进程的 @jdwp
 * socket), 走不通.
 */
data class HostHello(
    val packageName: String,
    val pid: Int,
    val processName: String? = null,
    val raw: String,
)

/**
 * 收到的 host 反向连接 (LocalSocket 包装, 仍可读写)。
 */
class HostConnection(
    val hello: HostHello,
    val socket: LocalSocket,
) {
    val receivedAt: Long = System.currentTimeMillis()
}

/**
 * IDE 端 LocalServerSocket 服务。
 *
 * 用法 (由 IDE 启动时构造, 单例):
 * ```
 *   val server = HostBridgeServer("ide-debug-bridge-com.example.app")
 *   server.setListener { conn -> AppReadyAutoConnect.onHostConnected(conn) }
 *   server.start()  // bind + accept loop
 *   // 之后不用了:
 *   server.stop()
 * ```
 */
class HostBridgeServer(
    val localSocketName: String,
) {
    private val log = ILogger.ROOT

    @Volatile private var serverSocket: LocalServerSocket? = null
    @Volatile private var acceptThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val idGenerator = AtomicLong(System.currentTimeMillis())
    // Phase 12w: 改用 LinkedBlockingDeque 替代 CopyOnWriteArrayList
    //   - CopyOnWriteArrayList.remove O(n): 反复 attach/detach 性能差, 单次 100ms+,
    //     而且 awaitNextConnection 每次 poll 都要 firstOrNull 扫整个 list
    //   - socket.isConnected 不可靠: Android LocalSocket.isConnected 反映 JVM 视角
    //     connected 状态, 但 accept 后立即 true, 即使对端 close 后 JVM 不一定
    //     知道, 走 isConnected 判定可能拿到已经 close 的 conn
    //   - busy-wait sleep 20ms: 没数据时 CPU 持续唤醒
    // 修法: queue.put/poll 替代, 走阻塞 poll 不用 busy-wait, O(1) 操作
    private val activeConnections = LinkedBlockingDeque<HostConnection>()
    // 保留 CopyOnWriteArrayList 作为 "all known connections" 视图 (给 stop()
    //   排空 + 调试用), Phase 12w 之后不主路径
    private val allConnections = CopyOnWriteArrayList<HostConnection>()

    @Volatile private var listener: ((HostConnection) -> Unit)? = null

    fun setListener(l: ((HostConnection) -> Unit)?) {
        this.listener = l
    }

    /**
     * 启动 LocalServerSocket, 进入 accept 循环。
     * 重复调用是 no-op, 不会重启。
     *
     * @throws IOException bind 失败
     */
    @Throws(IOException::class)
    fun start() {
        if (!running.compareAndSet(false, true)) {
            log.debug("HostBridgeServer: already running on '{}'", localSocketName)
            return
        }
        val lss = try {
            LocalServerSocket(localSocketName)
        } catch (t: Throwable) {
            running.set(false)
            throw IOException("failed to bind LocalServerSocket '$localSocketName': ${t.message}", t)
        }
        serverSocket = lss
        log.info("HostBridgeServer: bound abstract socket '{}'", localSocketName)
        val t = Thread({ acceptLoop(lss) }, "HostBridgeServer-accept")
        t.isDaemon = true
        acceptThread = t
        t.start()
    }

    /**
     * 停止服务: 关闭 LocalServerSocket + 关闭所有 active connection + join accept thread。
     */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        log.info("HostBridgeServer: stopping")
        runCatching { serverSocket?.close() }
        serverSocket = null
        // 关闭所有 active
        for (c in activeConnections) {
            runCatching { c.socket.close() }
        }
        activeConnections.clear()
        // accept thread 会因 serverSocket.close() 抛 IOException 退出
        acceptThread?.let { t ->
            try { t.join(2_000L) } catch (_: InterruptedException) { }
        }
        acceptThread = null
    }

    /**
     * 阻塞等待下一个 host 端连接 (用于 AidlSocketConnection.attachViaLocalBridge)。
     *
     * @param timeoutMs 等待时间
     * @return 第一个 [HostConnection]; 超时返回 null
     */
    fun awaitNextConnection(timeoutMs: Long): HostConnection? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (running.get() && System.currentTimeMillis() < deadline) {
            val next = activeConnections.firstOrNull { it.socket.isConnected }
            if (next != null) {
                activeConnections.remove(next)
                return next
            }
            try { Thread.sleep(20L) } catch (_: InterruptedException) { return null }
        }
        return null
    }

    /**
     * 是否正在 listen。
     */
    fun isRunning(): Boolean = running.get()

    // ---- 私有 ----

    private fun acceptLoop(lss: LocalServerSocket) {
        while (running.get() && !lss.isClosed) {
            try {
                val client = lss.accept()
                handleClient(client)
            } catch (t: Throwable) {
                if (running.get()) {
                    log.warn("HostBridgeServer: accept failed: {}", t.message)
                }
            }
        }
        log.info("HostBridgeServer: accept loop ended")
    }

    private fun handleClient(client: LocalSocket) {
        try {
            // Phase 12r: 改用 BufferedReader.readLine() 走阻塞 read + soTimeout,
            // 之前 readLineWithTimeout 轮询 input.available() 在 LocalSocket 上不可靠
            // (Android LocalSocket InputStream 的 available() 经常返 0 即使 kernel
            //  buffer 已有数据, 走非阻塞 polling 会一直 sleep 直到 5s timeout 返空,
            // HELLO 被丢弃, 端到端 host 端发 HELLO IDE 端不响应)。
            client.soTimeout = HELLO_READ_TIMEOUT_MS.toInt()
            val input = client.inputStream
            val raw = try {
                BufferedReader(InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).readLine()
            } catch (e: java.net.SocketTimeoutException) {
                log.warn("HostBridgeServer: HELLO read timeout ({}ms), dropping connection", HELLO_READ_TIMEOUT_MS)
                null
            }
            val hello = parseHello(raw)
            if (hello == null) {
                log.warn("HostBridgeServer: dropping connection, invalid HELLO: '{}'", raw)
                runCatching { client.close() }
                return
            }
            val conn = HostConnection(hello = hello, socket = client)
            activeConnections.put(conn)
            allConnections.add(conn)
            log.info("HostBridgeServer: received HELLO pkg={} pid={} id={}", hello.packageName, hello.pid, idGenerator.incrementAndGet())
            // Phase 13j: 兼容 multi-process host app, process= 字段让 IDE 端
            //   知道 HELLO 来自主进程还是 :debug / :remote. 跟 packageName 不
            //   一致 = ContentProvider 跑在非主进程, 这时 HELLO 的 pid 可能是
            //   :debug 进程, IDE 拿这个 pid attach JDWP 会失败 (attach 到的进程
            //   没有 @jdwp socket). Phase 13j 留 TODO 给上层 (AppReadyAutoConnect)
            //   处理: 走 reject + 提示用户在 Manifest 把 ContentProvider 挪回主进程.
            if (hello.processName != null && hello.processName != hello.packageName) {
                log.warn(
                    "HostBridgeServer: HELLO from non-main process '{}' (pkg={} pid={}). " +
                        "ContentProvider is in :debug / :remote process; JDWP attach will fail. " +
                        "Fix host app Manifest: move ContentProvider declaration to default process.",
                    hello.processName, hello.packageName, hello.pid,
                )
            }
            // 通知 listener (AppReadyAutoConnect)
            try {
                listener?.invoke(conn)
            } catch (t: Throwable) {
                log.warn("HostBridgeServer: listener threw: {}", t.message)
            }
        } catch (t: Throwable) {
            log.warn("HostBridgeServer: handleClient failed: {}", t.message)
            runCatching { client.close() }
        }
    }

    private fun parseHello(raw: String): HostHello? {
        if (raw.isBlank()) return null
        // 协议: "HELLO pkg=<pkg> pid=<pid> [process=<processName>] [sdk=<sdk>]"
        val trimmed = raw.trim()
        if (!trimmed.startsWith("HELLO ")) return null
        val parts = trimmed.substringAfter("HELLO ").split(Regex("\\s+"))
        var pkg: String? = null
        var pid: Int = 0
        var process: String? = null
        for (p in parts) {
            val kv = p.split("=", limit = 2)
            if (kv.size != 2) continue
            when (kv[0]) {
                "pkg" -> pkg = kv[1]
                "pid" -> pid = kv[1].toIntOrNull() ?: 0
                "process" -> process = kv[1]
            }
        }
        if (pkg.isNullOrBlank() || pid <= 0) return null
        return HostHello(packageName = pkg, pid = pid, processName = process, raw = raw)
    }

    companion object {
        const val HELLO_READ_TIMEOUT_MS: Long = 5_000L

        /**
         * 默认的 LocalServerSocket 名字生成 (per-uid 唯一):
         *   ide-debug-bridge-<uid>
         */
        fun defaultName(uid: Int = android.os.Process.myUid()): String =
            "ide-debug-bridge-$uid"
    }
}
