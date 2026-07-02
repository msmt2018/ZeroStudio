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
        )
        data class FailureRecord(
            val packageName: String,
            val error: Throwable,
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
        ) {
            successes.add(SuccessRecord(packageName, conn.type, info))
        }

        override fun onAttachFailed(
            packageName: String,
            conn: IDebugConnection,
            error: Throwable,
        ) {
            failures.add(FailureRecord(packageName, error))
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
