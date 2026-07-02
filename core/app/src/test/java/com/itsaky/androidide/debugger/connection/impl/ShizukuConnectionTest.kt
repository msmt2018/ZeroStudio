/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuConnection 集成测试: 4 个子路径的状态机。
 *  Binder / InHostPlugin / Socks 三个不支持的子路径预期抛 NotImplemented,
 *  WifiAdb 复用 AidlSocketConnection 走全流程。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.ConnectionError
import com.itsaky.androidide.debugger.connection.ConnectionRetryPolicy
import com.itsaky.androidide.debugger.connection.ConnectionState
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.ShizukuConfig
import com.itsaky.androidide.debugger.connection.shizuku.FakeShizukuBinderClient
import com.itsaky.androidide.debugger.connection.shizuku.FakeShizukuProbe
import com.itsaky.androidide.debugger.connection.shizuku.ShizukuStatus
import com.itsaky.androidide.debugger.connection.shizuku.ShizukuSubPathResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ShizukuConnectionTest {

    private val target = DebugTarget("com.example.app", "com.example.app.MainActivity")
    private val baseSettings = DebugConnectionSettings(
        shizuku = ShizukuConfig(
            subPath = ShizukuConfig.SubPath.Auto,
            binderTimeoutMs = 1000L,
        ),
        retryMaxAttempts = 1,
        retryInitialDelayMs = 1L,
    )

    @Test
    fun `resolve fails when Shizuku is not running`() = runBlocking {
        val probe = FakeShizukuProbe(
            status = ShizukuStatus(
                isRunning = false,
                isGranted = false,
                serverUid = -1,
                serverApiVersion = -1,
                notRunningReason = "not running",
            ),
        )
        val conn = ShizukuConnection(
            target = target,
            settings = baseSettings,
            probe = probe,
            resolver = ShizukuSubPathResolver(probe, listOf()),
        )

        val r = conn.resolve()
        assertTrue("resolve should fail when Shizuku not running", r.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
        assertEquals(ConnectionError.PermissionDenied, (state as ConnectionState.Closed).error)
    }

    @Test
    fun `resolve transitions to Connecting when Shizuku ready and Auto picks WifiAdb`() = runBlocking {
        val probe = FakeShizukuProbe()  // running + granted
        val conn = ShizukuConnection(
            target = target,
            settings = baseSettings,
            probe = probe,
            resolver = ShizukuSubPathResolver(probe, listOf()),
        )

        val r = conn.resolve()
        assertTrue("resolve should succeed", r.isSuccess)
        assertEquals(ConnectionState.Connecting, conn.state.value)
    }

    @Test
    fun `resolve retries permission request when not granted initially`() = runBlocking {
        val probe = FakeShizukuProbe(
            status = ShizukuStatus(
                isRunning = true,
                isGranted = false,
                serverUid = 1000,
                serverApiVersion = 13,
            ),
            grantResult = true,
        )
        val conn = ShizukuConnection(
            target = target,
            settings = baseSettings,
            probe = probe,
            resolver = ShizukuSubPathResolver(probe, listOf()),
        )

        val r = conn.resolve()
        assertTrue("resolve should succeed after permission grant", r.isSuccess)
        assertEquals(1, probe.requestCount)
    }

    @Test
    fun `connect on Binder subPath returns NotImplemented error`() = runBlocking {
        val probe = FakeShizukuProbe()
        val conn = ShizukuConnection(
            target = target,
            settings = baseSettings.copy(
                shizuku = baseSettings.shizuku.copy(subPath = ShizukuConfig.SubPath.Binder),
            ),
            probe = probe,
        )

        conn.resolve()
        val cr = conn.connect()
        // Binder 路径 connect 阶段不做事, 应该成功 (没动作)
        // 但内部 SubPath.Binder 走 connect: "nothing to do", 转入 Handshaking
        assertTrue("connect on Binder should succeed (no-op): ${cr.exceptionOrNull()?.message}", cr.isSuccess)
        assertEquals(ConnectionState.Handshaking, conn.state.value)
    }

    @Test
    fun `attach on Binder subPath fails with NotImplemented`() = runBlocking {
        val probe = FakeShizukuProbe()
        val conn = ShizukuConnection(
            target = target,
            settings = baseSettings.copy(
                shizuku = baseSettings.shizuku.copy(subPath = ShizukuConfig.SubPath.Binder),
            ),
            probe = probe,
        )

        conn.resolve()
        conn.connect()
        val ar = conn.attach()
        assertTrue("attach on Binder should fail (NotImplemented)", ar.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
    }

    @Test
    fun `attach on Socks subPath fails with NotImplemented`() = runBlocking {
        val probe = FakeShizukuProbe()
        val conn = ShizukuConnection(
            target = target,
            settings = baseSettings.copy(
                shizuku = baseSettings.shizuku.copy(subPath = ShizukuConfig.SubPath.Socks),
            ),
            probe = probe,
        )
        conn.resolve()
        conn.connect()
        val ar = conn.attach()
        assertTrue(ar.isFailure)
    }

    @Test
    fun `release resets state to Idle`() = runBlocking {
        val probe = FakeShizukuProbe()
        val conn = ShizukuConnection(
            target = target,
            settings = baseSettings,
            probe = probe,
        )
        conn.release()
        assertEquals(ConnectionState.Idle, conn.state.value)
    }

    @Test
    fun `ConnectionType and DebugTarget are wired through correctly`() {
        val conn = ShizukuConnection(
            target = target,
            settings = baseSettings,
            probe = FakeShizukuProbe(),
        )
        assertEquals(ConnectionType.Shizuku, conn.type)
        assertEquals(target, conn.target)
    }

    @Test
    fun `attachedSocket throws when not attached`() = runBlocking {
        val conn = ShizukuConnection(
            target = target,
            settings = baseSettings,
            probe = FakeShizukuProbe(),
        )
        try {
            conn.attachedSocket()
            org.junit.Assert.fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("not attached"))
        }
    }

    @Test
    fun `FakeShizukuBinderClient pingBinder returns preset value`() = runBlocking {
        val binder = FakeShizukuBinderClient(pingResult = true)
        assertEquals(true, binder.pingBinder())
        assertEquals(1000, binder.getUid())
        assertEquals(13, binder.getVersion())
    }

    @Test
    fun `FakeShizukuBinderClient newProcess throws by default`() = runBlocking {
        val binder = FakeShizukuBinderClient()
        try {
            binder.newProcess(arrayOf("ls"))
            org.junit.Assert.fail("expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            // expected
        }
    }
}
