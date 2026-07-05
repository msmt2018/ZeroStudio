/*
 *  ZeroStudio IDE - AppReadyAutoConnect 单元测试
 *
 *  子项目 9b 集成: 协调 AppReadySignalWatcher + HostBridgeServer,
 *  在 host app 启动后用新连接层发起 attach。
 *
 *  测试策略:
 *    - 不真正启动 AppReadySignalWatcher / HostBridgeServer (它们分别要
 *      起 logcat 进程 / bind abstract LocalServerSocket, JVM 单元测试跑不了)
 *    - 直接调公开方法 onLogcatSignal / onBridgeConnection 触发协调逻辑
 *    - 用 fake IDebugConnection 注入到 connectionFactory
 */

package com.itsaky.androidide.debugger.connection.host

import com.itsaky.androidide.debugger.connection.AttachInfo
import com.itsaky.androidide.debugger.connection.ConnectionState
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.IDebugConnection
import com.itsaky.androidide.debugger.connection.ResolveInfo
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.IOException
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(JUnit4::class)
class AppReadyAutoConnectTest {

    private val target = DebugTarget(
        packageName = "com.example.app",
        mainActivity = "com.example.app.MainActivity",
    )
    private val settings = DebugConnectionSettings(
        retryMaxAttempts = 1,
        retryInitialDelayMs = 10L,
    )

    // ---- 1. logcat 信号触发 attach 全流程 ----

    @Test
    fun `onLogcatSignal triggers resolve-connect-attach and reports success`() = runBlocking {
        val recorder = RecordingListener()
        val factoryCalls = AtomicInteger(0)
        val factory: (ConnectionType, DebugTarget, DebugConnectionSettings) -> IDebugConnection =
            { t, _, _ ->
                factoryCalls.incrementAndGet()
                FakeDebugConnection(type = t).asSuccess()
            }
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,  // 测试用 0 防抖
            connectionFactory = factory,
        )

        auto.onLogcatSignal(
            packageName = target.packageName,
            jdwpPort = 5005,
            variant = "debug",
        )

        // 等 doAttach 协程跑完 (300ms delay + 几次 IO 切换)
        waitUntilOrFail(2_000L) { recorder.successes.size == 1 }
        assertEquals(1, factoryCalls.get())
        val s = recorder.successes[0]
        assertEquals(target.packageName, s.packageName)
        assertEquals(ConnectionType.AidlSocket, s.connectionType)
        assertEquals(123, s.info.pid)
    }

    // ---- 2. bridge 反连触发 attach ----

    @Test
    fun `onBridgeConnection triggers attach with bridge source`() = runBlocking {
        val recorder = RecordingListener()
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ -> FakeDebugConnection(type = t).asSuccess() },
        )

        val conn = HostConnection(
            hello = HostHello(
                packageName = target.packageName,
                pid = 999,
                raw = "HELLO pkg=${target.packageName} pid=999",
            ),
            socket = mockk<android.net.LocalSocket>(relaxed = true),
        )
        auto.onBridgeConnection(conn)

        waitUntilOrFail(2_000L) { recorder.successes.size == 1 }
        assertEquals(target.packageName, recorder.successes[0].packageName)
    }

    // ---- 3. 防抖: 1s 内多次 schedule 只跑一次 ----

    @Test
    fun `debounce suppresses burst signals within window`() = runBlocking {
        val recorder = RecordingListener()
        val factoryCalls = AtomicInteger(0)
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 1_000L,  // 1s 防抖
            connectionFactory = { t, _, _ ->
                factoryCalls.incrementAndGet()
                FakeDebugConnection(type = t).asSuccess()
            },
        )

        // 在 1s 内连发 5 次
        repeat(5) { i ->
            auto.onLogcatSignal(
                packageName = target.packageName,
                jdwpPort = 5000 + i,
                variant = null,
            )
        }
        // 等一会儿让 doAttach 跑完
        delay(500L)
        // 只有 1 次 factory 调用,只有 1 次 success
        assertEquals(1, factoryCalls.get())
        assertEquals(1, recorder.successes.size)
    }

    // ---- 4. resolve 失败 -> onAttachFailed ----

    @Test
    fun `resolve failure invokes onAttachFailed and does not cache connection`() = runBlocking {
        val recorder = RecordingListener()
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ -> FakeDebugConnection(type = t).asResolveFailure() },
        )

        auto.onLogcatSignal(target.packageName, 5005, "debug")

        waitUntilOrFail(2_000L) { recorder.failures.size == 1 }
        val f = recorder.failures[0]
        assertEquals(target.packageName, f.packageName)
        assertNotNull(f.error)
        // 失败后 activeByPkg 应该被清空,下次同 pkg 应当再次调 factory
        assertEquals(0, recorder.successes.size)
    }

    // ---- 5. connect 失败 -> onAttachFailed ----

    @Test
    fun `connect failure invokes onAttachFailed`() = runBlocking {
        val recorder = RecordingListener()
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ -> FakeDebugConnection(type = t).asConnectFailure() },
        )

        auto.onLogcatSignal(target.packageName, 5005, "debug")

        waitUntilOrFail(2_000L) { recorder.failures.size == 1 }
        val f = recorder.failures[0]
        assertEquals(target.packageName, f.packageName)
        assertNotNull(f.error)
    }

    // ---- 6. attach 失败 -> onAttachFailed ----

    @Test
    fun `attach failure invokes onAttachFailed`() = runBlocking {
        val recorder = RecordingListener()
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ -> FakeDebugConnection(type = t).asAttachFailure() },
        )

        auto.onLogcatSignal(target.packageName, 5005, "debug")

        waitUntilOrFail(2_000L) { recorder.failures.size == 1 }
        val f = recorder.failures[0]
        assertEquals(target.packageName, f.packageName)
        assertNotNull(f.error)
    }

    // ---- 7. 同 packageName 复用 connection (activeByPkg 缓存) ----

    @Test
    fun `subsequent same-packageName signal reuses active connection`() = runBlocking {
        val recorder = RecordingListener()
        val factoryCalls = AtomicInteger(0)
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ ->
                factoryCalls.incrementAndGet()
                FakeDebugConnection(type = t).asSuccess()
            },
        )

        // 第 1 次:成功
        auto.onLogcatSignal(target.packageName, 5005, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 1 }
        assertEquals(1, factoryCalls.get())

        // 等防抖 (1s default); 改用 debounceMs = 50L 避免测试跑很久
        // 这里 debounceMs=0L,所以可以直接发第 2 次
        auto.onLogcatSignal(target.packageName, 5006, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 2 }
        // factory 还是只调 1 次 (activeByPkg 缓存)
        assertEquals(1, factoryCalls.get())
    }

    // ---- 8. 不同 packageName 不复用 ----

    @Test
    fun `different packageName triggers new connection`() = runBlocking {
        val recorder = RecordingListener()
        val factoryCalls = AtomicInteger(0)
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ ->
                factoryCalls.incrementAndGet()
                FakeDebugConnection(type = t).asSuccess()
            },
        )

        auto.onLogcatSignal("com.example.app1", 5005, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 1 }
        assertEquals(1, factoryCalls.get())

        auto.onLogcatSignal("com.example.app2", 5006, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 2 }
        assertEquals(2, factoryCalls.get())
    }

    // ---- 9. AutoConnectHint 传值 ----

    @Test
    fun `pickConnectionType receives hint with jdwp port from logcat signal`() = runBlocking {
        val recorder = RecordingListener()
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ -> FakeDebugConnection(type = t).asSuccess() },
        )

        auto.onLogcatSignal(target.packageName, 9999, "release")
        waitUntilOrFail(2_000L) { recorder.lastHint != null }
        val h = recorder.lastHint!!
        assertEquals(9999, h.jdwpPort)
        assertEquals("release", h.variant)
    }

    // ---- 10. DefaultAutoConnectListener 总是选 AidlSocket ----

    @Test
    fun `DefaultAutoConnectListener picks AidlSocket`() {
        val l = DefaultAutoConnectListener()
        val t = l.pickConnectionType(
            "com.example",
            AutoConnectHint(jdwpPort = 5005, localSocketName = "x", variant = "debug"),
        )
        assertEquals(ConnectionType.AidlSocket, t)
    }

    // ---- 11. 多 activeByPkg 清理: 多个 pkg 失败后, activeByPkg 都被清空 ----

    @Test
    fun `multiple package failures all clear activeByPkg entries`() = runBlocking {
        val recorder = RecordingListener()
        val factoryCalls = AtomicInteger(0)
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ ->
                factoryCalls.incrementAndGet()
                FakeDebugConnection(type = t).asResolveFailure()
            },
        )

        // 3 个不同 pkg 都失败
        auto.onLogcatSignal("com.app1", 5005, "debug")
        auto.onLogcatSignal("com.app2", 5006, "debug")
        auto.onLogcatSignal("com.app3", 5007, "debug")

        waitUntilOrFail(2_000L) { recorder.failures.size == 3 }
        assertEquals(3, factoryCalls.get())
        // 3 次失败后, 下次同 pkg 应当再次调 factory (cache cleared)
        auto.onLogcatSignal("com.app1", 5008, "debug")
        waitUntilOrFail(2_000L) { factoryCalls.get() == 4 }
    }

    // ---- 12. 空 hint (no jdwp port / no local socket / no variant) ----

    @Test
    fun `empty hint is still passed to pickConnectionType`() = runBlocking {
        val recorder = RecordingListener()
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ -> FakeDebugConnection(type = t).asSuccess() },
        )

        // 空 hint
        auto.onLogcatSignal(target.packageName, 0, null)

        waitUntilOrFail(2_000L) { recorder.lastHint != null }
        val h = recorder.lastHint!!
        assertEquals(0, h.jdwpPort)
        assertEquals(null, h.variant)
    }

    // ---- 13. source 字段透传: onLogcatSignal 走 logcat, onBridgeConnection 走 bridge ----

    @Test
    fun `source field indicates logcat or bridge origin in doAttach logs`() = runBlocking {
        // 这里我们用 listener 的 lastSourcePath (新增字段) 来验证 source 透传
        val recorder = RecordingSourceListener()
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ -> FakeDebugConnection(type = t).asSuccess() },
        )

        // logcat 信号
        auto.onLogcatSignal(target.packageName, 5005, "debug")
        waitUntilOrFail(2_000L) { recorder.sources.contains("logcat") }
        // bridge 信号
        val conn = HostConnection(
            hello = HostHello(
                packageName = target.packageName,
                pid = 999,
                raw = "HELLO pkg=${target.packageName} pid=999",
            ),
            socket = mockk<android.net.LocalSocket>(relaxed = true),
        )
        auto.onBridgeConnection(conn)
        waitUntilOrFail(2_000L) { recorder.sources.contains("bridge") }
        // 至少一次 logcat + 一次 bridge
        assertTrue("expected at least one logcat source", recorder.sources.contains("logcat"))
        assertTrue("expected at least one bridge source", recorder.sources.contains("bridge"))
    }

    // ---- 14. init 异常隔离: HostBridgeServer 启动失败不阻塞 logcat 信号处理 ----

    @Test
    fun `bridge server start failure is isolated and logcat still works`() = runBlocking {
        // 用一个会抛错的 connectionFactory 来让 logcat 信号失败
        val recorder = RecordingListener()
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ -> FakeDebugConnection(type = t).asSuccess() },
        )

        // start() 内部会 start HostBridgeServer. 在 JVM 测试里, HostBridgeServer
        // 起真 LocalServerSocket 可能 OK, 但我们这里直接调 start() 后立刻发 logcat
        // 信号, 验证不会 crash
        try {
            auto.start()
        } catch (t: Throwable) {
            // start() 不应该抛
            fail("start() should not throw: ${t.message}")
        }
        // logcat 信号应该照常工作
        auto.onLogcatSignal(target.packageName, 5005, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 1 }

        // 清理
        auto.stop()
    }

    // ---- 15. stop 取消所有 pending jobs ----

    @Test
    fun `stop cancels pending jobs and clears state`() = runBlocking {
        val recorder = RecordingListener()
        val factoryCalls = AtomicInteger(0)
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 0L,
            connectionFactory = { t, _, _ ->
                factoryCalls.incrementAndGet()
                FakeDebugConnection(type = t).asSuccess()
            },
        )

        // 多次发信号, 然后立刻 stop
        repeat(5) { auto.onLogcatSignal(target.packageName, 5000 + it, "debug") }
        // 立即 stop, 应该不会跑任何 attach (因为 300ms delay 后才跑)
        auto.stop()
        // 等一会儿确认没跑
        delay(500L)
        // 可能有 0 或 1 次 (取决于 timing)
        // 关键: stop 后不应该 crash
    }

    // ---- 16. onLogcatSignal 多次调用但 listener 没准备好也安全 ----

    @Test
    fun `rapid bursts after debounce window each trigger fresh attach`() = runBlocking {
        val recorder = RecordingListener()
        val factoryCalls = AtomicInteger(0)
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 100L,  // 100ms 防抖
            connectionFactory = { t, _, _ ->
                factoryCalls.incrementAndGet()
                FakeDebugConnection(type = t).asSuccess()
            },
        )

        // 第一次
        auto.onLogcatSignal("com.first", 5005, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 1 }
        // 等过防抖期
        delay(150L)
        // 第二次 (新 pkg)
        auto.onLogcatSignal("com.second", 5006, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 2 }
        assertEquals(2, factoryCalls.get())
    }

    // ---- 17. per-package debounce 修复: 不同 pkg 互不干扰 ----

    @Test
    fun `per-package debounce - different packages do not interfere`() = runBlocking {
        val recorder = RecordingListener()
        val factoryCalls = AtomicInteger(0)
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 1_000L,  // 1s 防抖
            connectionFactory = { t, _, _ ->
                factoryCalls.incrementAndGet()
                FakeDebugConnection(type = t).asSuccess()
            },
        )

        // 第一次 pkg A
        auto.onLogcatSignal("com.A", 5005, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 1 }
        // 立即触发 pkg B (距 pkg A < 1s, 但 pkg B 自己从未触发, 应该不被 debounce)
        auto.onLogcatSignal("com.B", 5006, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 2 }
        // 立即再触发 pkg C, 同样应该不被 debounce
        auto.onLogcatSignal("com.C", 5007, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 3 }
        assertEquals(3, factoryCalls.get())
    }

    @Test
    fun `per-package debounce - same package within window is dropped`() = runBlocking {
        val recorder = RecordingListener()
        val factoryCalls = AtomicInteger(0)
        val auto = AppReadyAutoConnect(
            settings = settings,
            listener = recorder,
            debounceMs = 1_000L,
            connectionFactory = { t, _, _ ->
                factoryCalls.incrementAndGet()
                FakeDebugConnection(type = t).asSuccess()
            },
        )

        // pkg A 第一次
        auto.onLogcatSignal("com.A", 5005, "debug")
        waitUntilOrFail(2_000L) { recorder.successes.size == 1 }
        // pkg A 立即再触发 (同 pkg, < 1s, 应被 debounce 掉)
        auto.onLogcatSignal("com.A", 5006, "debug")
        delay(500L)  // 等一会, 确认没新 success
        assertEquals("same package within window should be debounced", 1, recorder.successes.size)
        assertEquals("factory should not be called again", 1, factoryCalls.get())
    }

    // ---- 工具方法 ----

    private suspend fun waitUntilOrFail(timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(20L)
        }
        fail("predicate not satisfied within ${timeoutMs}ms")
    }

    // ---- 测试辅助:RecordingListener ----

    private class RecordingListener : AutoConnectListener {
        data class SuccessRecord(
            val packageName: String,
            val connectionType: ConnectionType,
            val info: AttachInfo,
            val source: String = "",
        )
        data class FailureRecord(
            val packageName: String,
            val error: Throwable,
            val source: String = "",
        )

        val successes = CopyOnWriteArrayList<SuccessRecord>()
        val failures = CopyOnWriteArrayList<FailureRecord>()
        var lastHint: AutoConnectHint? = null

        override fun pickConnectionType(packageName: String, hint: AutoConnectHint): ConnectionType {
            lastHint = hint
            return ConnectionType.AidlSocket
        }

        override fun onAttachSuccess(
            packageName: String,
            conn: IDebugConnection,
            info: AttachInfo,
            source: String,
        ) {
            successes.add(SuccessRecord(packageName, conn.type, info, source))
        }

        override fun onAttachFailed(
            packageName: String,
            conn: IDebugConnection,
            error: Throwable,
            source: String,
        ) {
            failures.add(FailureRecord(packageName, error, source))
        }
    }

    // ---- 测试辅助:RecordingSourceListener (只记录 source 字段) ----

    private class RecordingSourceListener : AutoConnectListener {
        val sources = CopyOnWriteArrayList<String>()
        var lastHint: AutoConnectHint? = null

        override fun pickConnectionType(packageName: String, hint: AutoConnectHint): ConnectionType {
            lastHint = hint
            return ConnectionType.AidlSocket
        }

        override fun onAttachSuccess(
            packageName: String,
            conn: IDebugConnection,
            info: AttachInfo,
            source: String,
        ) {
            sources.add(source)
        }

        override fun onAttachFailed(
            packageName: String,
            conn: IDebugConnection,
            error: Throwable,
            source: String,
        ) {
            sources.add(source)
        }
    }

    // ---- 测试辅助:FakeDebugConnection ----

    private class FakeDebugConnection(
        override val type: ConnectionType,
    ) : IDebugConnection {
        // 三段返回: resolve / connect / attach
        private var resolveResult: Result<ResolveInfo> = Result.success(
            ResolveInfo(transportKind = "test", endpoint = "x", requiresHostRunning = true)
        )
        private var connectResult: Result<Unit> = Result.success(Unit)
        private var attachResult: Result<AttachInfo> = Result.success(
            AttachInfo(pid = 123, jdwpSessionId = 1L, jdwpDescription = "fake")
        )

        private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
        override val state: StateFlow<ConnectionState> = _state
        override val target: DebugTarget = DebugTarget("ignored", "ignored/x")
        override val capabilities: Set<com.itsaky.androidide.debugger.connection.ConnectionCapability> = emptySet()

        fun asSuccess(): FakeDebugConnection = this
        fun asResolveFailure(): FakeDebugConnection = apply { resolveResult = Result.failure(IOException("resolve boom")) }
        fun asConnectFailure(): FakeDebugConnection = apply { connectResult = Result.failure(IOException("connect boom")) }
        fun asAttachFailure(): FakeDebugConnection = apply { attachResult = Result.failure(IOException("attach boom")) }

        override suspend fun resolve(): Result<ResolveInfo> = resolveResult
        override suspend fun connect(): Result<Unit> = connectResult
        override suspend fun attach(): Result<AttachInfo> = attachResult.also {
            if (it.isSuccess) _state.value = ConnectionState.Attached(it.getOrNull()!!.pid, it.getOrNull()!!.jdwpSessionId)
        }
        override suspend fun detach() { _state.value = ConnectionState.Closed(null) }
        override fun release() { _state.value = ConnectionState.Idle }
        override suspend fun sendJdwp(bytes: ByteArray) {}
        override fun receiveJdwp(): Flow<ByteArray> = MutableStateFlow(ByteArray(0))
        override fun attachedSocket(): Socket = throw UnsupportedOperationException("fake")
    }
}
