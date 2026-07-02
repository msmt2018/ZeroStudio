/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  InnetVmSocksConnection 单元测试: 用真 ServerSocket 假 SOCKS5 server 跑
 *  resolve/connect/attach 全流程。
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.ConnectionError
import com.itsaky.androidide.debugger.connection.ConnectionRetryPolicy
import com.itsaky.androidide.debugger.connection.ConnectionState
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.InnetSocksConfig
import com.itsaky.androidide.debugger.connection.aidl.AidlJdwpProtocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

@RunWith(JUnit4::class)
class InnetVmSocksConnectionTest {

    private val target = DebugTarget("com.example.app", "com.example.app.MainActivity")

    private fun makeSettings(host: String, port: Int): DebugConnectionSettings {
        return DebugConnectionSettings(
            innetSocks = InnetSocksConfig(
                host = host,
                port = port,
                username = "",
                password = "",
                connectTimeoutMs = 5_000L,
            ),
            retryMaxAttempts = 1,
            retryInitialDelayMs = 1L,
        )
    }

    @Test
    fun `resolve fails with PortResolveFailed when host is blank`() = runBlocking {
        val conn = InnetVmSocksConnection(
            target = target,
            settings = makeSettings(host = "", port = 1080),
        )
        val r = conn.resolve()
        assertTrue("resolve should fail when host is blank", r.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
        assertEquals(ConnectionError.PortResolveFailed, (state as ConnectionState.Closed).error)
    }

    @Test
    fun `resolve fails with IoFailure when proxy not reachable`() = runBlocking {
        // 用一个未监听的端口探测 (1-1023 大多被 OS 占用或保留)
        val conn = InnetVmSocksConnection(
            target = target,
            settings = makeSettings(host = "127.0.0.1", port = 1)
                .copy(innetSocks = InnetSocksConfig(
                    host = "127.0.0.1",
                    port = 1,
                    connectTimeoutMs = 500L,
                )),
        )
        val r = conn.resolve()
        assertTrue("resolve should fail when port not reachable", r.isFailure)
    }

    @Test
    fun `full flow resolve connect attach transitions to Attached`() = runBlocking {
        // 起一个真 ServerSocket 当 SOCKS5 server, 收到握手后转发
        val ss = ServerSocket()
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        val port = ss.localPort

        val hostStarted = java.util.concurrent.CountDownLatch(1)
        val hostFinished = java.util.concurrent.CountDownLatch(1)
        var hostError: Throwable? = null
        val hostThread = thread(name = "fake-socks-and-jdwp", isDaemon = true) {
            try {
                hostStarted.await()
                val client = ss.accept()
                client.use { c ->
                    val cIn = DataInputStream(c.getInputStream())
                    val cOut = DataOutputStream(c.getOutputStream())
                    // 1) SOCKS5 握手: 客户端问候
                    val greeting = ByteArray(3)
                    cIn.readFully(greeting)
                    assertEquals(0x05, greeting[0].toInt() and 0xff)
                    // 服务端响应: no-auth
                    cOut.write(byteArrayOf(0x05, 0x00))
                    cOut.flush()
                    // 2) SOCKS5 CONNECT 请求: VER CMD RSV ATYP ADDR PORT
                    val reqHeader = ByteArray(4)
                    cIn.readFully(reqHeader)
                    val atyp = reqHeader[3].toInt() and 0xff
                    if (atyp == 0x01) cIn.readFully(ByteArray(4))
                    else if (atyp == 0x03) {
                        val len = cIn.readUnsignedByte()
                        cIn.readFully(ByteArray(len))
                    }
                    cIn.readShort()  // port
                    // 服务端响应: 成功
                    cOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                    cOut.flush()
                    // 3) 模拟 JDWP 端: 收到握手 -> 回握手 -> 收到 VM.Version -> 回响应
                    AidlJdwpProtocol.readAndVerifyHandshake(cIn)
                    AidlJdwpProtocol.writeHandshake(cOut)
                    cOut.flush()
                    val header = ByteArray(11)
                    cIn.readFully(header)
                    // 构造 VM.Version 响应
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
                    Thread.sleep(2_000L)
                }
            } catch (t: Throwable) {
                hostError = t
            } finally {
                hostFinished.countDown()
            }
        }

        val conn = InnetVmSocksConnection(
            target = target,
            settings = makeSettings(host = "127.0.0.1", port = port),
        )
        hostStarted.countDown()
        val r1 = conn.resolve()
        assertTrue("resolve should succeed: ${r1.exceptionOrNull()?.message}", r1.isSuccess)
        val r2 = conn.connect()
        assertTrue(r2.isSuccess)
        val r3 = conn.attach()
        hostFinished.await()
        assertTrue("attach should succeed: ${r3.exceptionOrNull()?.message}", r3.isSuccess)

        val state = conn.state.value
        assertTrue("state should be Attached, was $state", state is ConnectionState.Attached)
        hostThread.join(5_000L)
        if (hostError != null) {
            throw AssertionError("fake host thread failed", hostError)
        }

        // 验证 attachedSocket() 返回非 null
        val sock = conn.attachedSocket()
        assertNotNull(sock)

        conn.detach()
        conn.release()
        ss.close()
    }

    @Test
    fun `release resets state to Idle`() = runBlocking {
        val conn = InnetVmSocksConnection(
            target = target,
            settings = makeSettings(host = "127.0.0.1", port = 1)
                .copy(innetSocks = InnetSocksConfig(
                    host = "127.0.0.1",
                    port = 1,
                    connectTimeoutMs = 100L,
                )),
        )
        conn.release()
        assertEquals(ConnectionState.Idle, conn.state.value)
    }

    @Test
    fun `ConnectionType and DebugTarget are wired through correctly`() {
        val conn = InnetVmSocksConnection(
            target = target,
            settings = makeSettings(host = "127.0.0.1", port = 1080),
        )
        assertEquals(ConnectionType.InnetVmSocks, conn.type)
        assertEquals(target, conn.target)
    }

    @Test
    fun `attachedSocket throws when not attached`() = runBlocking {
        val conn = InnetVmSocksConnection(
            target = target,
            settings = makeSettings(host = "127.0.0.1", port = 1080),
        )
        try {
            conn.attachedSocket()
            org.junit.Assert.fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("not attached"))
        }
    }

    // ---- 内部辅助 ----

    private fun buildVmVersionReply(
        cmdId: Int,
        description: String,
        jdwpMajor: Int,
        jdwpMinor: Int,
        vmVersion: String,
        vmName: String,
    ): ByteArray {
        val payload = java.io.ByteArrayOutputStream()
        payload.write(0); payload.write(0)
        writeString(payload, description)
        writeInt(payload, jdwpMajor)
        writeInt(payload, jdwpMinor)
        writeString(payload, vmVersion)
        writeString(payload, vmName)
        val data = payload.toByteArray()
        val out = java.io.ByteArrayOutputStream()
        writeInt(out, data.size)
        writeInt(out, cmdId)
        out.write(0x80)
        out.write(0x01)
        out.write(0x01)
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
