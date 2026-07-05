/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  RootConnection 单元测试 (子项目 4 stub 替换后):
 *  验证 resolve/connect/attach 走 RootJdwpStream (InputStream/OutputStream) 路径。
 *
 *  测试策略:
 *    - resolve: FakeRootProbe 控 root 探测结果
 *    - connect: FakeRootClient 控 findProcessId 返回值
 *    - attach: FakeRootClient 返 RootJdwpStream (基于 PipedInputStream/PipedOutputStream),
 *      一个独立线程当"host runtime", 在 piped 对端跑 JDWP 握手 + VM.Version 协议
 *    - 验证 state 切到 Attached, sendJdwp 能用, attachedSocket() 抛 UnsupportedOperationException
 */

package com.itsaky.androidide.debugger.connection.impl

import com.itsaky.androidide.debugger.connection.AttachInfo
import com.itsaky.androidide.debugger.connection.ConnectionError
import com.itsaky.androidide.debugger.connection.ConnectionState
import com.itsaky.androidide.debugger.connection.ConnectionType
import com.itsaky.androidide.debugger.connection.DebugConnectionSettings
import com.itsaky.androidide.debugger.connection.DebugTarget
import com.itsaky.androidide.debugger.connection.RootConfig
import com.itsaky.androidide.debugger.connection.aidl.AidlJdwpProtocol
import com.itsaky.androidide.debugger.connection.root.FakeRootClient
import com.itsaky.androidide.debugger.connection.root.FakeRootProbe
import com.itsaky.androidide.debugger.connection.root.RootClient
import com.itsaky.androidide.debugger.connection.root.RootJdwpStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets

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

    // ---- resolve: root 探测 ----

    @Test
    fun `resolve succeeds when probe returns true and transitions to Connecting`() = runBlocking {
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

    // ---- connect: 找 host pid ----

    @Test
    fun `connect calls findProcessId and transitions to Handshaking`() = runBlocking {
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
        assertEquals(target.packageName, client.lastPackageName)
    }

    @Test
    fun `connect fails with IoFailure when findProcessId returns -1`() = runBlocking {
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
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
    }

    // ---- attach: 走 RootJdwpStream (子项目 4 stub 替换后) ----

    @Test
    fun `attach succeeds via RootJdwpStream and transitions to Attached`() = runBlocking {
        val probe = FakeRootProbe(shouldSucceed = true)
        // 建一对 piped stream: IDE 端 (output) -> 桥接到 host 端 (input),
        //                       host 端 (output) -> 桥接到 IDE 端 (input)
        val ideToHost = PipedOutputStream()
        val hostToIde = PipedInputStream()
        val hostInput = PipedInputStream(ideToHost)
        val hostOutput = PipedOutputStream()
        hostOutput.connect(hostToIde)

        val stream = RootJdwpStream(
            input = hostToIde,
            output = ideToHost,
            onClose = { /* no-op in test */ },
        )
        val client = FakeRootClient(pidResult = 12345, streamResult = stream)
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = probe,
            rootClient = client,
        )

        // 启 "host runtime" 线程, 跑 JDWP 握手 + VM.Version 协议
        val hostStarted = java.util.concurrent.CountDownLatch(1)
        val hostFinished = java.util.concurrent.CountDownLatch(1)
        var hostError: Throwable? = null
        val hostThread = Thread({
            try {
                hostStarted.countDown()
                val cIn = DataInputStream(hostInput)
                val cOut = DataOutputStream(hostOutput)
                // 1) 读 IDE handshake
                AidlJdwpProtocol.readAndVerifyHandshake(cIn)
                // 2) 回 handshake
                AidlJdwpProtocol.writeHandshake(cOut)
                cOut.flush()
                // 3) 读 VM.Version 命令 (11 字节 header)
                val header = ByteArray(11)
                cIn.readFully(header)
                val cmd = header[10].toInt() and 0xff
                assertEquals(0x01, cmd)
                // 4) 回 VM.Version 响应
                val reply = buildVmVersionReply(
                    cmdId = ((header[4].toInt() and 0xff) shl 24) or
                        ((header[5].toInt() and 0xff) shl 16) or
                        ((header[6].toInt() and 0xff) shl 8) or
                        (header[7].toInt() and 0xff),
                    description = "Java JDWP",
                    jdwpMajor = 11,
                    jdwpMinor = 2,
                    vmVersion = "1.8.0_292",
                    vmName = "DalvikVM",
                )
                cOut.write(reply)
                cOut.flush()
                // 保持 stream 不关, 让后续 sendJdwp 不立刻 EOF
                Thread.sleep(2_000L)
            } catch (t: Throwable) {
                hostError = t
            } finally {
                hostFinished.countDown()
            }
        }, "fake-root-host").apply { isDaemon = true; start() }

        try {
            hostStarted.await()
            conn.resolve()
            conn.connect()
            val ar = conn.attach()
            assertTrue("attach should succeed: ${ar.exceptionOrNull()?.message}", ar.isSuccess)
            val info: AttachInfo = ar.getOrNull()!!
            assertEquals(12345, info.pid)
            assertTrue(
                "description should contain vmName or jdwp version: ${info.jdwpDescription}",
                info.jdwpDescription.contains("DalvikVM") || info.jdwpDescription.contains("11.2"),
            )
            val state = conn.state.value
            assertTrue("state should be Attached, was $state", state is ConnectionState.Attached)
            assertEquals(12345, (state as ConnectionState.Attached).pid)
        } finally {
            hostThread.join(5_000L)
            conn.release()
        }

        hostFinished.await()
        if (hostError != null) {
            throw AssertionError("fake root host thread failed", hostError)
        }
    }

    @Test
    fun `attach fails with IoFailure when openJdwpStream returns null`() = runBlocking {
        val probe = FakeRootProbe(shouldSucceed = true)
        val client = object : RootClient {
            override fun findProcessId(packageName: String, suBin: String, timeoutMs: Long) = 12345
            override fun openJdwpStream(hostPid: Int, suBin: String, timeoutMs: Long): RootJdwpStream {
                throw IOException("socat not installed")
            }
        }
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = probe,
            rootClient = client,
        )
        conn.resolve()
        conn.connect()
        val r = conn.attach()
        assertTrue("attach should fail when openJdwpStream throws", r.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
    }

    @Test
    fun `attach fails with JdwpHandshakeFailed when stream sends bad bytes`() = runBlocking {
        val probe = FakeRootProbe(shouldSucceed = true)
        val pipeIn = PipedInputStream()
        val pipeOut = PipedOutputStream(pipeIn)
        val stream = RootJdwpStream(
            input = pipeIn,
            output = pipeOut,
            onClose = {},
        )
        val client = FakeRootClient(pidResult = 12345, streamResult = stream)
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = probe,
            rootClient = client,
        )

        // host 端发 14 字节错握手
        val badHandshake = ByteArray(14) { 'X'.code.toByte() }
        pipeOut.write(badHandshake)
        pipeOut.flush()

        conn.resolve()
        conn.connect()
        val r = conn.attach()
        assertTrue("attach should fail with bad handshake", r.isFailure)
        val state = conn.state.value
        assertTrue("state should be Closed", state is ConnectionState.Closed)
        assertEquals(
            ConnectionError.JdwpHandshakeFailed,
            (state as ConnectionState.Closed).error,
        )
    }

    @Test
    fun `attach before connect fails with IllegalStateException`() = runBlocking {
        val probe = FakeRootProbe(shouldSucceed = true)
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = probe,
        )
        // 没调 resolve/connect 直接 attach
        val r = conn.attach()
        assertTrue("attach should fail before connect", r.isFailure)
    }

    // ---- 字节流: sendJdwp + receiveJdwp ----

    @Test
    fun `sendJdwp writes to output stream after attach`() = runBlocking {
        val probe = FakeRootProbe(shouldSucceed = true)
        val pipeOut = PipedOutputStream()
        val pipeIn = PipedInputStream(pipeOut)
        val stream = RootJdwpStream(
            input = pipeIn,
            output = pipeOut,
            onClose = {},
        )
        val client = FakeRootClient(pidResult = 12345, streamResult = stream)
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = probe,
            rootClient = client,
        )
        // 喂一个空 VM.Version 响应让 attach 成功
        val hostThread = Thread({
            try {
                val cIn = DataInputStream(pipeIn)
                val cOut = DataOutputStream(pipeOut)
                AidlJdwpProtocol.readAndVerifyHandshake(cIn)
                AidlJdwpProtocol.writeHandshake(cOut)
                cOut.flush()
                val header = ByteArray(11)
                cIn.readFully(header)
                cOut.write(buildVmVersionReply(
                    cmdId = ((header[4].toInt() and 0xff) shl 24) or
                        ((header[5].toInt() and 0xff) shl 16) or
                        ((header[6].toInt() and 0xff) shl 8) or
                        (header[7].toInt() and 0xff),
                    description = "d", jdwpMajor = 11, jdwpMinor = 0,
                    vmVersion = "v", vmName = "n",
                ))
                cOut.flush()
                // 等 sendJdwp 写数据
                val payload = ByteArray(5)
                cIn.readFully(payload)
                assertEquals(0x01.toByte(), payload[0])
                assertEquals(0x02.toByte(), payload[1])
            } catch (_: Throwable) { }
        }, "fake-root-host-send").apply { isDaemon = true; start() }

        try {
            conn.resolve()
            conn.connect()
            conn.attach()
            conn.sendJdwp(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05))
            hostThread.join(3_000L)
        } finally {
            conn.release()
        }
    }

    @Test
    fun `attachedSocket throws UnsupportedOperationException (Root 走 InputStream 路径)`() = runBlocking {
        val conn = RootConnection(
            target = target,
            settings = baseSettings,
            rootProbe = FakeRootProbe(true),
        )
        try {
            conn.attachedSocket()
            fail("expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("InputStream"))
        }
    }

    // ---- 释放 ----

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
