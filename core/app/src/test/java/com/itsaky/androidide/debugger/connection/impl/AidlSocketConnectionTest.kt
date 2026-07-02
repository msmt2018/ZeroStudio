/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  AidlSocketConnection 集成测试:
 *  用真实 ServerSocket + Fake 注入跑 resolve/connect/attach 全流程。
 *
 *  测试用一个独立线程当"host" (reverse-connect 到 IDE 端 ServerSocket),
 *  走标准握手 + VM.Version 协议。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.AidlSocketConfig
import com.itsaky.androidide.debugger.connection.ConnectionCapability
import com.itsaky.androidide.debugger.connection.ConnectionError
import com.itsaky.androidide.debugger.connection.ConnectionRetryPolicy
import com.itsaky.androidide.debugger.connection.ConnectionState
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.aidl.AidlJdwpProtocol
import com.itsaky.androidide.debugger.connection.aidl.AppProcessInfo
import com.itsaky.androidide.debugger.connection.aidl.FakeAidlHostLauncher
import com.itsaky.androidide.debugger.connection.aidl.FakeAidlProcessProbe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

@RunWith(JUnit4::class)
class AidlSocketConnectionTest {

    private val target = DebugTarget("com.example.app", "com.example.app.MainActivity")
    private val settings = DebugConnectionSettings(
        aidlSocket = com.itsaky.androidide.debugger.connection.AidlSocketConfig(
            requireHostForeground = false,  // 测试里用 fake probe,不需要真进程
        ),
        retryMaxAttempts = 1,  // 测试里只跑一次,加快速度
        retryInitialDelayMs = 10L,
    )

    @Test
    fun `resolve returns transportKind tcp endpoint and transitions to Connecting`() = runBlocking {
        val probe = FakeAidlProcessProbe(result = AppProcessInfo(123, "com.example.app", 1000))
        val launcher = FakeAidlHostLauncher(shouldSucceed = true)
        val conn = AidlSocketConnection(
            target = target,
            settings = settings,
            hostLauncher = launcher,
            processProbe = probe,
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L),
        )

        val r = conn.resolve()
        assertTrue("resolve should succeed", r.isSuccess)
        assertEquals("tcp", r.getOrNull()!!.transportKind)
        assertEquals(ConnectionState.Connecting, conn.state.value)
    }

    @Test
    fun `resolve fails with HostAppNotRunning when probe returns null`() = runBlocking {
        val probe = FakeAidlProcessProbe(result = null)
        val conn = AidlSocketConnection(
            target = target,
            settings = settings.copy(
                aidlSocket = settings.aidlSocket.copy(requireHostForeground = true),
            ),
            hostLauncher = FakeAidlHostLauncher(true),
            processProbe = probe,
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L),
        )

        val r = conn.resolve()
        assertTrue("resolve should fail when host not running", r.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
        assertEquals(
            ConnectionError.HostAppNotRunning,
            (state as ConnectionState.Closed).error,
        )
    }

    @Test
    fun `connect binds ServerSocket and invokes launcher with port`() = runBlocking {
        val probe = FakeAidlProcessProbe(result = null)
        val launcher = FakeAidlHostLauncher(shouldSucceed = true)
        val conn = AidlSocketConnection(
            target = target,
            settings = settings,
            hostLauncher = launcher,
            processProbe = probe,
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L),
        )

        // 跳过 resolve(),直接 connect(),模拟"requireHostForeground = false"路径
        val r = conn.connect()
        assertTrue("connect should succeed: ${r.exceptionOrNull()?.message}", r.isSuccess)
        assertEquals(target.packageName, launcher.lastPackageName)
        assertEquals(target.mainActivity, launcher.lastMainActivity)
        assertTrue("launcher should have received a non-zero port", launcher.lastPort > 0)
        assertEquals(ConnectionState.Handshaking, conn.state.value)

        // 清理
        conn.release()
    }

    @Test
    fun `full flow resolve connect attach transitions to Attached`() = runBlocking {
        val probe = FakeAidlProcessProbe(result = null)  // 不需要 probe 命中
        val launcher = FakeAidlHostLauncher(shouldSucceed = true)
        val conn = AidlSocketConnection(
            target = target,
            settings = settings,
            hostLauncher = launcher,
            processProbe = probe,
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L),
        )

        val resolveR = conn.resolve()
        assertTrue(resolveR.isSuccess)

        val connectR = conn.connect()
        assertTrue("connect should succeed", connectR.isSuccess)
        val port = launcher.lastPort
        assertTrue(port > 0)

        // 启动一个"假 host"线程: 等 attach 准备好后连接回 127.0.0.1:port,
        // 跑标准握手 + VM.Version 协议。
        val attachStarted = java.util.concurrent.CountDownLatch(1)
        val attachFinished = java.util.concurrent.CountDownLatch(1)
        var hostError: Throwable? = null
        val hostThread = thread(name = "fake-host", isDaemon = true) {
            try {
                attachStarted.await()
                val client = Socket("127.0.0.1", port)
                client.use { c ->
                    val cIn = DataInputStream(c.getInputStream())
                    val cOut = DataOutputStream(c.getOutputStream())
                    AidlJdwpProtocol.readAndVerifyHandshake(cIn)
                    AidlJdwpProtocol.writeHandshake(cOut)
                    cOut.flush()
                    // 读 VM.Version 命令
                    val header = ByteArray(11)
                    cIn.readFully(header)
                    // 构造响应
                    val reply = buildVmVersionReply(
                        cmdId = ((header[4].toInt() and 0xff) shl 24) or
                                ((header[5].toInt() and 0xff) shl 16) or
                                ((header[6].toInt() and 0xff) shl 8) or
                                (header[7].toInt() and 0xff),
                        description = "Java JDWP",
                        jdwpMajor = 11,
                        jdwpMinor = 2,
                        vmVersion = "1.8.0_292",
                        vmName = "OpenJDK 64-Bit Server VM",
                    )
                    cOut.write(reply)
                    cOut.flush()
                    // 保持 socket 不关,这样后续 sendJdwp/receiveJdwp 不会立刻 EOF
                    Thread.sleep(2_000L)
                }
            } catch (t: Throwable) {
                hostError = t
            } finally {
                attachFinished.countDown()
            }
        }

        attachStarted.countDown()
        val attachR = conn.attach()
        attachFinished.await()

        assertTrue("attach should succeed: ${attachR.exceptionOrNull()?.message}", attachR.isSuccess)
        val state = conn.state.value
        assertTrue("state should be Attached, was $state", state is ConnectionState.Attached)
        val attachedState = state as ConnectionState.Attached
        assertTrue("jdwpSessionId should be non-zero", attachedState.jdwpSessionId != 0L)
        assertTrue(
            "description should contain vmName or jdwp version",
            attachedState.jdwpDescription.contains("OpenJDK") ||
                attachedState.jdwpDescription.contains("11.2"),
        )

        hostThread.join(5_000L)
        if (hostError != null) {
            throw AssertionError("fake host thread failed", hostError)
        }

        // 验证 attachedSocket() 返回非 null
        val socket = conn.attachedSocket()
        assertNotNull(socket)

        // 清理
        conn.detach()
        conn.release()
    }

    @Test
    fun `attach fails with JdwpHandshakeFailed when host sends bad bytes`() = runBlocking {
        val probe = FakeAidlProcessProbe(result = null)
        val launcher = FakeAidlHostLauncher(shouldSucceed = true)
        val conn = AidlSocketConnection(
            target = target,
            settings = settings,
            hostLauncher = launcher,
            processProbe = probe,
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L),
        )

        conn.resolve()
        conn.connect()
        val port = launcher.lastPort

        val hostThread = thread(name = "bad-host", isDaemon = true) {
            try {
                val client = Socket("127.0.0.1", port)
                client.use { c ->
                    val cIn = DataInputStream(c.getInputStream())
                    val cOut = DataOutputStream(c.getOutputStream())
                    AidlJdwpProtocol.readAndVerifyHandshake(cIn)
                    // 回错误 handshake
                    cOut.write(ByteArray(14) { 'X'.code.toByte() })
                    cOut.flush()
                }
            } catch (_: Throwable) { }
        }

        val attachR = conn.attach()
        hostThread.join(5_000L)

        assertTrue("attach should fail", attachR.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
        assertEquals(
            ConnectionError.JdwpHandshakeFailed,
            (state as ConnectionState.Closed).error,
        )

        conn.release()
    }

    @Test
    fun `release closes sockets and resets state to Idle`() = runBlocking {
        val probe = FakeAidlProcessProbe(result = null)
        val launcher = FakeAidlHostLauncher(shouldSucceed = true)
        val conn = AidlSocketConnection(
            target = target,
            settings = settings,
            hostLauncher = launcher,
            processProbe = probe,
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L),
        )
        conn.resolve()
        conn.connect()
        conn.release()
        assertEquals(ConnectionState.Idle, conn.state.value)
    }

    @Test
    fun `release from Idle is a no-op`() = runBlocking {
        val probe = FakeAidlProcessProbe(result = null)
        val launcher = FakeAidlHostLauncher(shouldSucceed = true)
        val conn = AidlSocketConnection(
            target = target,
            settings = settings,
            hostLauncher = launcher,
            processProbe = probe,
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L),
        )
        conn.release()  // 不抛
        assertEquals(ConnectionState.Idle, conn.state.value)
    }

    @Test
    fun `attachedSocket throws when not attached`() = runBlocking {
        val probe = FakeAidlProcessProbe(result = null)
        val launcher = FakeAidlHostLauncher(shouldSucceed = true)
        val conn = AidlSocketConnection(
            target = target,
            settings = settings,
            hostLauncher = launcher,
            processProbe = probe,
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L),
        )
        try {
            conn.attachedSocket()
            org.junit.Assert.fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("not attached"))
        }
    }

    @Test
    fun `ConnectionType and DebugTarget are wired through correctly`() {
        val conn = AidlSocketConnection(
            target = target,
            settings = settings,
            hostLauncher = FakeAidlHostLauncher(true),
            processProbe = FakeAidlProcessProbe(null),
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L),
        )
        assertEquals(ConnectionType.AidlSocket, conn.type)
        assertEquals(target, conn.target)
    }

    @Test
    fun `capabilities include NeedsHostForeground`() = runBlocking {
        val conn = AidlSocketConnection(
            target = target,
            settings = settings,
            hostLauncher = FakeAidlHostLauncher(true),
            processProbe = FakeAidlProcessProbe(null),
            retryPolicy = ConnectionRetryPolicy(maxAttempts = 1, initialDelayMs = 1L),
        )
        assertTrue(
            "capabilities should include NeedsHostForeground",
            conn.capabilities.contains(
                com.itsaky.androidide.debugger.connection.ConnectionCapability.NeedsHostForeground
            ),
        )
    }

    // ---- 内部辅助: 构造 VM.Version 响应包 ----

    private fun buildVmVersionReply(
        cmdId: Int,
        description: String,
        jdwpMajor: Int,
        jdwpMinor: Int,
        vmVersion: String,
        vmName: String,
    ): ByteArray {
        val payload = java.io.ByteArrayOutputStream()
        // errorCode = 0
        payload.write(0)
        payload.write(0)
        writeString(payload, description)
        writeInt(payload, jdwpMajor)
        writeInt(payload, jdwpMinor)
        writeString(payload, vmVersion)
        writeString(payload, vmName)
        val data = payload.toByteArray()
        val out = java.io.ByteArrayOutputStream()
        writeInt(out, data.size)
        writeInt(out, cmdId)
        out.write(0x80)  // reply flag
        out.write(0x01)  // commandSet
        out.write(0x01)  // command
        out.write(data)
        return out.toByteArray()
    }

    private fun writeInt(out: java.io.ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xff)
        out.write((v ushr 16) and 0xff)
        out.write((v ushr 8) and 0xff)
        out.write(v and 0xff)
    }

    private fun writeString(out: java.io.ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(StandardCharsets.UTF_8)
        writeInt(out, b.size)
        out.write(b)
    }
}
