/*
 *  ZeroStudio IDE - Debug Connection Layer
 *
 *  UsbLanConnection 单元测试:
 *    - resolve 阶段: 用真 ServerSocket 模拟本地 adb server (127.0.0.1:5037)
 *    - connect 阶段: FakeAdbRunner 预置 adb devices / pidof / forward 的响应
 *    - 默认 adbPort = 5037 (跟 InnetVmAdb 默认 5555 不同)
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.ConnectionError
import com.itsaky.androidide.debugger.connection.ConnectionState
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.UsbLanConfig
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
class UsbLanConnectionTest {

    private val target = DebugTarget("com.example.app", "com.example.app.MainActivity")

    private fun makeSettings(
        adbHost: String = "127.0.0.1",
        adbPort: Int = 5037,
        adbSerial: String? = null,
        timeoutMs: Long = 5_000L,
    ): DebugConnectionSettings = DebugConnectionSettings(
        usbLan = UsbLanConfig(
            adbHost = adbHost,
            adbPort = adbPort,
            adbSerial = adbSerial,
            connectTimeoutMs = timeoutMs,
        ),
        retryMaxAttempts = 1,
        retryInitialDelayMs = 1L,
    )

    /**
     * 起一个真 ServerSocket 模拟本地 adb server, 在独立线程里 accept + close resolve 阶段的 probe。
     */
    private fun startAdbServer(): ServerSocket {
        val ss = ServerSocket()
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        ss.soTimeout = 1_000
        thread(name = "fake-adb-server", isDaemon = true) {
            try { ss.accept().use { } } catch (_: Throwable) { }
        }
        return ss
    }

    private fun fakeAdb(
        devicesOutput: String = "List of devices attached\nemulator-5554\tdevice\n\n",
        hostPid: Int = 12345,
        forwardOk: Boolean = true,
    ): FakeAdbRunner {
        val fake = FakeAdbRunner()
        fake.respond(FakeAdbRunner.contains("devices")) { _ ->
            AdbResult(0, devicesOutput)
        }
        fake.respond(FakeAdbRunner.contains("pidof")) { _ ->
            if (hostPid > 0) AdbResult(0, hostPid.toString())
            else AdbResult(0, "")
        }
        fake.respond(FakeAdbRunner.contains("forward")) { _ ->
            if (forwardOk) AdbResult(0, "")
            else AdbResult(1, "", "forward failed")
        }
        return fake
    }

    @Test
    fun `default settings have adbPort 5037 (local adb server)`() {
        val settings = DebugConnectionSettings()
        assertEquals(5037, settings.usbLan.adbPort)
        assertEquals("127.0.0.1", settings.usbLan.adbHost)
    }

    @Test
    fun `resolve fails with PortResolveFailed when host is blank`() = runBlocking {
        val conn = UsbLanConnection(
            target = target,
            settings = makeSettings(adbHost = "", adbPort = 5037),
        )
        val r = conn.resolve()
        assertTrue("resolve should fail when host is blank", r.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
        assertEquals(ConnectionError.PortResolveFailed, (state as ConnectionState.Closed).error)
    }

    @Test
    fun `resolve succeeds when local adb server is reachable`() = runBlocking {
        val ss = startAdbServer()
        try {
            val conn = UsbLanConnection(
                target = target,
                settings = makeSettings(adbPort = ss.localPort),
            )
            val r = conn.resolve()
            assertTrue("resolve should succeed: ${r.exceptionOrNull()?.message}", r.isSuccess)
            val info = r.getOrNull()!!
            assertEquals("adb-forward-usb", info.transportKind)
            assertEquals(ConnectionState.Connecting, conn.state.value)
        } finally {
            ss.close()
        }
    }

    @Test
    fun `connect fails when adb devices returns no devices`() = runBlocking {
        val ss = startAdbServer()
        try {
            val fake = fakeAdb(devicesOutput = "List of devices attached\n\n")  // 空设备列表
            val conn = UsbLanConnection(
                target = target,
                settings = makeSettings(adbPort = ss.localPort),
                adbRunner = fake,
            )
            conn.resolve()
            val r = conn.connect()
            assertTrue("connect should fail when no devices attached", r.isFailure)
        } finally {
            ss.close()
        }
    }

    @Test
    fun `connect fails when target serial not in devices list`() = runBlocking {
        val ss = startAdbServer()
        try {
            val fake = fakeAdb(devicesOutput = "List of devices attached\nemulator-5554\tdevice\n\n")
            val conn = UsbLanConnection(
                target = target,
                settings = makeSettings(adbPort = ss.localPort, adbSerial = "non-existent-serial"),
                adbRunner = fake,
            )
            conn.resolve()
            val r = conn.connect()
            assertTrue("connect should fail when serial not in list", r.isFailure)
            val msg = r.exceptionOrNull()?.message ?: ""
            assertTrue("error message should mention serial: $msg",
                msg.contains("non-existent-serial"))
        } finally {
            ss.close()
        }
    }

    @Test
    fun `connect fails when target device is in offline state`() = runBlocking {
        val ss = startAdbServer()
        try {
            val fake = fakeAdb(devicesOutput = "List of devices attached\nemulator-5554\toffline\n\n")
            val conn = UsbLanConnection(
                target = target,
                settings = makeSettings(adbPort = ss.localPort, adbSerial = "emulator-5554"),
                adbRunner = fake,
            )
            conn.resolve()
            val r = conn.connect()
            assertTrue("connect should fail when device is offline", r.isFailure)
        } finally {
            ss.close()
        }
    }

    @Test
    fun `connect succeeds when device is in device state without serial filter`() = runBlocking {
        val ss = startAdbServer()
        try {
            val fake = fakeAdb(
                devicesOutput = "List of devices attached\nemulator-5554\tdevice\nphysical-device-2\tdevice\n\n",
                hostPid = 55555,
                forwardOk = true,
            )
            val conn = UsbLanConnection(
                target = target,
                settings = makeSettings(adbPort = ss.localPort),  // 不配 serial
                adbRunner = fake,
            )
            conn.resolve()
            val r = conn.connect()
            assertTrue("connect should succeed: ${r.exceptionOrNull()?.message}", r.isSuccess)
            assertEquals(ConnectionState.Handshaking, conn.state.value)
            // adb devices + adb forward 应该都调用过
            assertTrue("adb devices should have been called",
                fake.callHistory.any { it.any { a -> a == "devices" } })
            assertTrue("adb forward should have been called",
                fake.callHistory.any { it.any { a -> a == "forward" } })
        } finally {
            ss.close()
            // release 时尝试清 forward, fake 的 contains("forward") 会返回 AdbResult(0, "")
        }
    }

    @Test
    fun `connect succeeds when specific serial is in device state`() = runBlocking {
        val ss = startAdbServer()
        try {
            val fake = fakeAdb(
                devicesOutput = "List of devices attached\nemulator-5554\tdevice\nemulator-5556\tunauthorized\n\n",
                hostPid = 33333,
                forwardOk = true,
            )
            val conn = UsbLanConnection(
                target = target,
                settings = makeSettings(adbPort = ss.localPort, adbSerial = "emulator-5554"),
                adbRunner = fake,
            )
            conn.resolve()
            val r = conn.connect()
            assertTrue("connect should succeed: ${r.exceptionOrNull()?.message}", r.isSuccess)
            // 所有 adb 命令都应加 -s emulator-5554 前缀
            for (call in fake.callHistory) {
                assertEquals("-s", call[0])
                assertEquals("emulator-5554", call[1])
            }
        } finally {
            ss.close()
        }
    }

    @Test
    fun `connect fails when adb forward fails`() = runBlocking {
        val ss = startAdbServer()
        try {
            val fake = fakeAdb(
                devicesOutput = "List of devices attached\nemulator-5554\tdevice\n\n",
                hostPid = 12345,
                forwardOk = false,  // forward 失败
            )
            val conn = UsbLanConnection(
                target = target,
                settings = makeSettings(adbPort = ss.localPort),
                adbRunner = fake,
            )
            conn.resolve()
            val r = conn.connect()
            assertTrue("connect should fail when adb forward fails", r.isFailure)
        } finally {
            ss.close()
        }
    }

    @Test
    fun `release resets state to Idle`() = runBlocking {
        val conn = UsbLanConnection(
            target = target,
            settings = makeSettings(),
        )
        conn.release()
        assertEquals(ConnectionState.Idle, conn.state.value)
    }

    @Test
    fun `ConnectionType and DebugTarget are wired through correctly`() {
        val conn = UsbLanConnection(
            target = target,
            settings = makeSettings(),
        )
        assertEquals(ConnectionType.UsbLan, conn.type)
        assertEquals(target, conn.target)
    }

    @Test
    fun `attachedSocket throws when not attached`() = runBlocking {
        val conn = UsbLanConnection(
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
}
