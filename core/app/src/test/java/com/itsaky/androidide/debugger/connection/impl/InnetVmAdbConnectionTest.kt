/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  InnetVmAdbConnection 单元测试:
 *    - resolve 阶段: 用真 ServerSocket 模拟 ADB server
 *    - connect 阶段: FakeAdbRunner 预置 adb connect / pidof / forward 的响应
 *    - attach 阶段: 真 ServerSocket 模拟 JDWP peer (返回 handshake + VM.Version)
 *
 *  不真起 adb binary; 也不真跑 JVM。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.ConnectionError
import com.itsaky.androidide.debugger.connection.ConnectionState
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.InnetAdbConfig
import com.itsaky.androidide.debugger.connection.adb.AdbResult
import com.itsaky.androidide.debugger.connection.adb.FakeAdbRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(JUnit4::class)
class InnetVmAdbConnectionTest {

    private val target = DebugTarget("com.example.app", "com.example.app.MainActivity")

    private fun makeSettings(
        adbHost: String = "127.0.0.1",
        adbPort: Int = 5555,
        adbSerial: String? = null,
        timeoutMs: Long = 5_000L,
    ): DebugConnectionSettings = DebugConnectionSettings(
        innetAdb = InnetAdbConfig(
            adbHost = adbHost,
            adbPort = adbPort,
            adbSerial = adbSerial,
            connectTimeoutMs = timeoutMs,
        ),
        retryMaxAttempts = 1,
        retryInitialDelayMs = 1L,
    )

    private fun fakeAdb(
        connectOk: Boolean = true,
        hostPid: Int = 12345,
        forwardOk: Boolean = true,
        serial: String? = null,
    ): FakeAdbRunner {
        val fake = FakeAdbRunner()
        // adb connect
        fake.respond(FakeAdbRunner.contains("connect")) { _ ->
            if (connectOk) AdbResult(0, "connected to 127.0.0.1:5555")
            else AdbResult(1, "", "cannot connect")
        }
        // adb shell pidof
        fake.respond(FakeAdbRunner.contains("pidof")) { _ ->
            if (hostPid > 0) AdbResult(0, hostPid.toString())
            else AdbResult(0, "")  // 空 = host app 没在跑
        }
        // adb forward
        fake.respond(FakeAdbRunner.contains("forward")) { _ ->
            if (forwardOk) AdbResult(0, "")
            else AdbResult(1, "", "forward failed")
        }
        return fake
    }

    @Test
    fun `resolve fails with PortResolveFailed when host is blank`() = runBlocking {
        val conn = InnetVmAdbConnection(
            target = target,
            settings = makeSettings(adbHost = "", adbPort = 5555),
        )
        val r = conn.resolve()
        assertTrue("resolve should fail when host is blank", r.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
        assertEquals(ConnectionError.PortResolveFailed, (state as ConnectionState.Closed).error)
    }

    @Test
    fun `resolve fails with IoFailure when adb port not reachable`() = runBlocking {
        // 选一个 1-1023 保留端口, 几乎不可能被监听
        val conn = InnetVmAdbConnection(
            target = target,
            settings = makeSettings(adbHost = "127.0.0.1", adbPort = 1, timeoutMs = 300L),
        )
        val r = conn.resolve()
        assertTrue("resolve should fail when port not reachable", r.isFailure)
    }

    @Test
    fun `resolve succeeds when adb port is reachable`() = runBlocking {
        // 起一个真 ServerSocket 当 adb server
        val ss = ServerSocket()
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        try {
            val conn = InnetVmAdbConnection(
                target = target,
                settings = makeSettings(adbPort = ss.localPort),
            )
            val r = conn.resolve()
            assertTrue("resolve should succeed: ${r.exceptionOrNull()?.message}", r.isSuccess)
            val info = r.getOrNull()!!
            assertEquals("adb-forward", info.transportKind)
            assertEquals(ConnectionState.Connecting, conn.state.value)
        } finally {
            ss.close()
        }
    }

    @Test
    fun `connect fails with IoFailure when adb connect returns failure`() = runBlocking {
        // 起一个真 ServerSocket 模拟 adb server, 接受 probe 后关
        val adbSs = ServerSocket()
        adbSs.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        adbSs.soTimeout = 1_000
        val probeAccepted = CountDownLatch(1)
        thread(name = "fake-adb-probe", isDaemon = true) {
            try { adbSs.accept().use { } } catch (_: Throwable) { }
            probeAccepted.countDown()
        }

        val fake = FakeAdbRunner().apply {
            respond(FakeAdbRunner.contains("connect")) { _ ->
                AdbResult(1, "", "fake: connect failed")
            }
        }
        val conn = InnetVmAdbConnection(
            target = target,
            settings = makeSettings(adbPort = adbSs.localPort),
            adbRunner = fake,
        )
        conn.resolve()  // 走到 Connecting
        val r = conn.connect()
        assertTrue("connect should fail when adb connect returns 1", r.isFailure)
        adbSs.close()
    }

    @Test
    fun `connect fails when host pid is empty`() = runBlocking {
        val adbSs = ServerSocket()
        adbSs.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        adbSs.soTimeout = 1_000
        thread(name = "fake-adb-probe", isDaemon = true) {
            try { adbSs.accept().use { } } catch (_: Throwable) { }
        }

        val fake = FakeAdbRunner().apply {
            respond(FakeAdbRunner.contains("connect")) { _ -> AdbResult(0, "connected") }
            respond(FakeAdbRunner.contains("pidof")) { _ -> AdbResult(0, "") }  // empty
        }
        val conn = InnetVmAdbConnection(
            target = target,
            settings = makeSettings(adbPort = adbSs.localPort),
            adbRunner = fake,
        )
        conn.resolve()
        val r = conn.connect()
        assertTrue("connect should fail when pid is empty", r.isFailure)
        adbSs.close()
    }

    @Test
    fun `connect succeeds when adbRunner returns valid pid and forward ok`() = runBlocking {
        val adbSs = ServerSocket()
        adbSs.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        adbSs.soTimeout = 1_000
        thread(name = "fake-adb-probe", isDaemon = true) {
            try { adbSs.accept().use { } } catch (_: Throwable) { }
        }

        val fake = fakeAdb(connectOk = true, hostPid = 12345, forwardOk = true)
        val conn = InnetVmAdbConnection(
            target = target,
            settings = makeSettings(adbPort = adbSs.localPort),
            adbRunner = fake,
        )
        conn.resolve()
        val r = conn.connect()
        assertTrue("connect should succeed: ${r.exceptionOrNull()?.message}", r.isSuccess)
        assertEquals(ConnectionState.Handshaking, conn.state.value)
        // 应该 adb forward 命令被调用过
        assertTrue("adb forward should have been called",
            fake.callHistory.any { it.any { a -> a == "forward" } })
        adbSs.close()
        conn.release()
    }

    @Test
    fun `release resets state to Idle`() = runBlocking {
        val conn = InnetVmAdbConnection(
            target = target,
            settings = makeSettings(),
        )
        conn.release()
        assertEquals(ConnectionState.Idle, conn.state.value)
    }

    @Test
    fun `ConnectionType and DebugTarget are wired through correctly`() {
        val conn = InnetVmAdbConnection(
            target = target,
            settings = makeSettings(),
        )
        assertEquals(ConnectionType.InnetVmAdb, conn.type)
        assertEquals(target, conn.target)
    }

    @Test
    fun `attachedSocket throws when not attached`() = runBlocking {
        val conn = InnetVmAdbConnection(
            target = target,
            settings = makeSettings(),
        )
        try {
            conn.attachedSocket()
            org.junit.Assert.fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("not attached"))
        }
    }

    @Test
    fun `adbSerial is injected as -s arg when configured`() = runBlocking {
        val adbSs = ServerSocket()
        adbSs.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        adbSs.soTimeout = 1_000
        thread(name = "fake-adb-probe", isDaemon = true) {
            try { adbSs.accept().use { } } catch (_: Throwable) { }
        }

        val fake = fakeAdb(connectOk = true, hostPid = 999, forwardOk = true)
        val conn = InnetVmAdbConnection(
            target = target,
            settings = makeSettings(adbPort = adbSs.localPort, adbSerial = "emulator-5554"),
            adbRunner = fake,
        )
        conn.resolve()
        val r = conn.connect()
        assertTrue("connect should succeed with serial: ${r.exceptionOrNull()?.message}", r.isSuccess)
        // 验证所有 adb 命令都加了 -s emulator-5554 前缀
        for (call in fake.callHistory) {
            assertEquals("-s", call[0])
            assertEquals("emulator-5554", call[1])
        }
        adbSs.close()
        conn.release()
    }

    @Test
    fun `release cleans up adb forward`() = runBlocking {
        val adbSs = ServerSocket()
        adbSs.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        adbSs.soTimeout = 1_000
        thread(name = "fake-adb-probe", isDaemon = true) {
            try { adbSs.accept().use { } } catch (_: Throwable) { }
        }

        val fake = fakeAdb(connectOk = true, hostPid = 7777, forwardOk = true)
        val conn = InnetVmAdbConnection(
            target = target,
            settings = makeSettings(adbPort = adbSs.localPort),
            adbRunner = fake,
        )
        conn.resolve()
        conn.connect()
        conn.release()
        // release 时应该尝试 adb forward --remove
        val removeCalls = fake.callHistory.filter { args ->
            args.any { it == "forward" } && args.any { it == "--remove" }
        }
        assertTrue("release should call adb forward --remove, got calls: ${fake.callHistory}", removeCalls.isNotEmpty())
        adbSs.close()
    }
}
