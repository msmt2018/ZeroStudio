/*
 *  ZeroStudio IDE - Debugger Connection Layer 单测
 *
 *  子项目 1 阶段: 只测抽象层本身 (ConnectionType / ConnectionRetryPolicy /
 *  DebugConnectionRegistry),不测 5 个 stub 实现(那些是子项目 2~5 的事)。
 *
 *  不依赖 Android Context,用纯 JUnit4 跑。
 */

package com.itsaky.androidide.debugger.connection

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ConnectionTypeTest {

    @Test
    fun `fromId returns default AidlSocket for null`() {
        assertSame(ConnectionType.AidlSocket, ConnectionType.fromId(null))
    }

    @Test
    fun `fromId returns default for unknown id`() {
        assertSame(ConnectionType.AidlSocket, ConnectionType.fromId("not-a-real-id"))
    }

    @Test
    fun `fromId roundtrips all types`() {
        for (type in ConnectionType.ALL) {
            assertSame(type, ConnectionType.fromId(type.id))
        }
    }

    @Test
    fun `requiresRoot is true only for Root`() {
        assertFalse(ConnectionType.AidlSocket.requiresRoot)
        assertFalse(ConnectionType.Shizuku.requiresRoot)
        assertTrue(ConnectionType.Root.requiresRoot)
        assertFalse(ConnectionType.InnetVm.requiresRoot)
        assertFalse(ConnectionType.UsbLan.requiresRoot)
    }

    @Test
    fun `requiresShizuku is true only for Shizuku`() {
        assertFalse(ConnectionType.AidlSocket.requiresShizuku)
        assertTrue(ConnectionType.Shizuku.requiresShizuku)
        assertFalse(ConnectionType.Root.requiresShizuku)
        assertFalse(ConnectionType.InnetVm.requiresShizuku)
        assertFalse(ConnectionType.UsbLan.requiresShizuku)
    }

    @Test
    fun `isValidId accepts all real ids`() {
        for (type in ConnectionType.ALL) {
            assertTrue(ConnectionType.isValidId(type.id))
        }
    }

    @Test
    fun `isValidId rejects unknown id`() {
        assertFalse(ConnectionType.isValidId("nope"))
        assertFalse(ConnectionType.isValidId(null))
    }
}

@RunWith(JUnit4::class)
class ConnectionRetryPolicyTest {

    @Test
    fun `returns first success`() = runBlocking {
        val policy = ConnectionRetryPolicy(maxAttempts = 3, initialDelayMs = 10L)
        val r = policy.retry { attempt ->
            if (attempt == 2) Result.success("ok")
            else Result.failure(RuntimeException("try $attempt"))
        }
        assertTrue(r.isSuccess)
        assertEquals("ok", r.getOrNull())
    }

    @Test
    fun `exhausts attempts and returns last failure`() = runBlocking {
        val policy = ConnectionRetryPolicy(maxAttempts = 3, initialDelayMs = 1L)
        val r = policy.retry<Int> { Result.failure(RuntimeException("always fails")) }
        assertTrue(r.isFailure)
    }

    @Test
    fun `default config is 3 attempts and 500ms initial`() {
        val policy = ConnectionRetryPolicy()
        assertEquals(3, policy.maxAttempts)
        assertEquals(500L, policy.initialDelayMs)
        assertEquals(2.0, policy.multiplier, 0.0)
    }

    @Test
    fun `single attempt runs once and returns failure`() = runBlocking {
        val policy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L)
        var calls = 0
        val r = policy.retry<Int> {
            calls++
            Result.failure(RuntimeException("nope"))
        }
        assertTrue(r.isFailure)
        assertEquals(1, calls)
    }
}

@RunWith(JUnit4::class)
class DebugConnectionRegistryTest {

    private val target = DebugTarget("com.example.app", "com.example.app.MainActivity")

    private val settings = DebugConnectionSettings()

    @Test
    fun `create returns AidlSocketConnection for AidlSocket type`() {
        val conn = DebugConnectionRegistry.create(ConnectionType.AidlSocket, target, settings)
        assertEquals(ConnectionType.AidlSocket, conn.type)
        assertEquals(target, conn.target)
    }

    @Test
    fun `create returns ShizukuConnection for Shizuku type`() {
        val conn = DebugConnectionRegistry.create(ConnectionType.Shizuku, target, settings)
        assertEquals(ConnectionType.Shizuku, conn.type)
    }

    @Test
    fun `create returns RootConnection for Root type`() {
        val conn = DebugConnectionRegistry.create(ConnectionType.Root, target, settings)
        assertEquals(ConnectionType.Root, conn.type)
    }

    @Test
    fun `create returns InnetVmConnection for InnetVm type`() {
        val conn = DebugConnectionRegistry.create(ConnectionType.InnetVm, target, settings)
        assertEquals(ConnectionType.InnetVm, conn.type)
    }

    @Test
    fun `create returns UsbLanConnection for UsbLan type`() {
        val conn = DebugConnectionRegistry.create(ConnectionType.UsbLan, target, settings)
        assertEquals(ConnectionType.UsbLan, conn.type)
    }

    @Test
    fun `createForActive honors settings activeType`() {
        val s = DebugConnectionSettings(activeType = ConnectionType.Shizuku)
        val conn = DebugConnectionRegistry.createForActive(target, s)
        assertEquals(ConnectionType.Shizuku, conn.type)
    }

    @Test
    fun `new connection starts in Idle state`() {
        val conn = DebugConnectionRegistry.create(ConnectionType.AidlSocket, target, settings)
        assertEquals(ConnectionState.Idle, conn.state.value)
    }
}

@RunWith(JUnit4::class)
class ConnectionErrorTest {

    @Test
    fun `retryable errors are flagged correctly`() {
        assertFalse(ConnectionError.PermissionDenied.retryable)
        assertFalse(ConnectionError.DebugFlagMissing.retryable)
        assertFalse(ConnectionError.BuildConfigNotDebug.retryable)
        assertTrue(ConnectionError.HostAppNotRunning.retryable)
        assertTrue(ConnectionError.Timeout.retryable)
        assertTrue(ConnectionError.JdwpHandshakeFailed.retryable)
        assertTrue(ConnectionError.PortResolveFailed.retryable)
        assertTrue(ConnectionError.NetworkUnreachable.retryable)
        assertTrue(ConnectionError.IoFailure(RuntimeException("io")).retryable)
        assertTrue(ConnectionError.Unknown(RuntimeException("unknown")).retryable)
    }

    @Test
    fun `describe returns non-empty for all variants`() {
        val variants: List<ConnectionError> = listOf(
            ConnectionError.PermissionDenied,
            ConnectionError.HostAppNotRunning,
            ConnectionError.DebugFlagMissing,
            ConnectionError.BuildConfigNotDebug,
            ConnectionError.Timeout,
            ConnectionError.JdwpHandshakeFailed,
            ConnectionError.PortResolveFailed,
            ConnectionError.NetworkUnreachable,
            ConnectionError.IoFailure(RuntimeException("io boom")),
            ConnectionError.Unknown(RuntimeException("weird")),
        )
        for (e in variants) {
            val desc = e.describe()
            assertNotNull(desc)
            assertTrue("describe for $e should not be blank", desc.isNotBlank())
        }
    }
}

@RunWith(JUnit4::class)
class DebugTargetTest {

    @Test
    fun `requires non-blank packageName`() {
        try {
            DebugTarget("", "MainActivity")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("packageName"))
        }
    }

    @Test
    fun `requires non-blank mainActivity`() {
        try {
            DebugTarget("com.example", "  ")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("mainActivity"))
        }
    }
}
