/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  RootConnection 单元测试: 状态机 + 错误分类 (fake probe + fake client)。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.ConnectionError
import com.itsaky.androidide.debugger.connection.ConnectionRetryPolicy
import com.itsaky.androidide.debugger.connection.ConnectionState
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.RootConfig
import com.itsaky.androidide.debugger.connection.root.FakeRootClient
import com.itsaky.androidide.debugger.connection.root.FakeRootProbe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RootConnectionTest {

    private val target = DebugTarget("com.example.app", "com.example.app.MainActivity")
    private val baseSettings = DebugConnectionSettings(
        root = RootConfig(
            suBinary = "/system/bin/su",
            probeTimeoutMs = 1000L,
            allowMagisk = true,
        ),
        retryMaxAttempts = 1,
        retryInitialDelayMs = 1L,
    )

    @Test
    fun `resolve succeeds when probe returns true`() = runBlocking {
        val probe = FakeRootProbe(shouldSucceed = true)
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = probe,
        )
        val r = conn.resolve()
        assertTrue("resolve should succeed: ${r.exceptionOrNull()?.message}", r.isSuccess)
        assertEquals(ConnectionState.Connecting, conn.state.value)
        assertEquals(1, probe.probeCount)
    }

    @Test
    fun `resolve fails with PermissionDenied when probe returns false`() = runBlocking {
        val probe = FakeRootProbe(shouldSucceed = false)
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = probe,
        )
        val r = conn.resolve()
        assertTrue("resolve should fail when no root", r.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
        assertEquals(ConnectionError.PermissionDenied, (state as ConnectionState.Closed).error)
    }

    @Test
    fun `connect calls findProcessId and stores host pid`() = runBlocking {
        val probe = FakeRootProbe(shouldSucceed = true)
        val client = FakeRootClient(pidResult = 12345)
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = probe,
            rootClient = client,
        )
        conn.resolve()
        val r = conn.connect()
        assertTrue("connect should succeed", r.isSuccess)
        assertEquals(ConnectionState.Handshaking, conn.state.value)
        assertEquals(1, client.findProcessIdCallCount)
    }

    @Test
    fun `connect fails when findProcessId returns -1`() = runBlocking {
        val probe = FakeRootProbe(shouldSucceed = true)
        val client = FakeRootClient(pidResult = -1)
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = probe,
            rootClient = client,
        )
        conn.resolve()
        val r = conn.connect()
        assertTrue("connect should fail when pid = -1", r.isFailure)
    }

    @Test
    fun `attach fails with NotImplemented when openJdwpSocket is not available`() = runBlocking {
        val probe = FakeRootProbe(shouldSucceed = true)
        val client = FakeRootClient(pidResult = 12345, socketResult = null)
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = probe,
            rootClient = client,
        )
        conn.resolve()
        conn.connect()
        val r = conn.attach()
        assertTrue("attach should fail (NotImplemented)", r.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
    }

    @Test
    fun `release resets state to Idle`() = runBlocking {
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = FakeRootProbe(true),
        )
        conn.release()
        assertEquals(ConnectionState.Idle, conn.state.value)
    }

    @Test
    fun `ConnectionType and DebugTarget are wired through correctly`() {
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = FakeRootProbe(true),
        )
        assertEquals(ConnectionType.Root, conn.type)
        assertEquals(target, conn.target)
    }

    @Test
    fun `attachedSocket throws when not attached`() = runBlocking {
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = FakeRootProbe(true),
        )
        try {
            conn.attachedSocket()
            org.junit.Assert.fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("not attached"))
        }
    }
}
