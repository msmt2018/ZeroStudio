/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  AidlJdwpProtocol 单元测试:
 *  验证握手字节序列、VM.Version 命令包构造、响应包解析。
 *  纯 JUnit4,不依赖 Android Context / Robolectric。
 */

package com.itsaky.androidide.debugger.connection.aidl

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

@RunWith(JUnit4::class)
class AidlJdwpProtocolTest {

    @Test
    fun `HANDSHAKE constant is the canonical 14 byte ASCII string`() {
        assertEquals("JDWP-Handshake", AidlJdwpProtocol.HANDSHAKE)
        assertEquals(14, AidlJdwpProtocol.HANDSHAKE_BYTES.size)
        assertArrayEquals(
            "JDWP-Handshake".toByteArray(StandardCharsets.US_ASCII),
            AidlJdwpProtocol.HANDSHAKE_BYTES,
        )
    }

    @Test
    fun `buildVmVersionCommand produces 11 byte header with length 11`() {
        val cmd = AidlJdwpProtocol.buildVmVersionCommand(commandId = 1)
        assertEquals(11, cmd.size)
        // length = 11 (header-only, no data)
        val len = ((cmd[0].toInt() and 0xff) shl 24) or
                ((cmd[1].toInt() and 0xff) shl 16) or
                ((cmd[2].toInt() and 0xff) shl 8) or
                (cmd[3].toInt() and 0xff)
        assertEquals(11, len)
        // id = 1
        val id = ((cmd[4].toInt() and 0xff) shl 24) or
                ((cmd[5].toInt() and 0xff) shl 16) or
                ((cmd[6].toInt() and 0xff) shl 8) or
                (cmd[7].toInt() and 0xff)
        assertEquals(1, id)
        // flags = 0x00 (command)
        assertEquals(0x00, cmd[8].toInt() and 0xff)
        // commandSet = 1 (VirtualMachine)
        assertEquals(0x01, cmd[9].toInt() and 0xff)
        // command = 1 (Version)
        assertEquals(0x01, cmd[10].toInt() and 0xff)
    }

    @Test
    fun `parseVmVersionReply extracts description, jdwp and vm fields`() {
        val packet = buildVmVersionReplyPacket(
            cmdId = 0x42,
            description = "Java JDWP",
            jdwpMajor = 11,
            jdwpMinor = 2,
            vmVersion = "1.8.0_292",
            vmName = "OpenJDK 64-Bit Server VM",
        )
        val info = AidlJdwpProtocol.parseVmVersionReply(packet)
        assertEquals("Java JDWP", info.description)
        assertEquals(11, info.jdwpMajor)
        assertEquals(2, info.jdwpMinor)
        assertEquals("1.8.0_292", info.vmVersion)
        assertEquals("OpenJDK 64-Bit Server VM", info.vmName)
        assertEquals("11.2", info.jdwpVersion)
    }

    @Test
    fun `parseVmVersionReply rejects non-reply flags`() {
        val packet = ByteArray(13)
        // length(4) + id(4) + flags(0x00, command) + commandSet(1) + command(1) + errorCode(2)
        // flags = 0x00 (wrong, must be 0x80 for reply)
        try {
            AidlJdwpProtocol.parseVmVersionReply(packet)
            fail("expected IOException for non-reply flags")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("flags"))
        }
    }

    @Test
    fun `parseVmVersionReply rejects non-zero errorCode`() {
        val packet = buildVmVersionReplyPacket(
            cmdId = 1,
            description = "X",
            jdwpMajor = 1,
            jdwpMinor = 0,
            vmVersion = "X",
            vmName = "X",
            forceErrorCode = 0x1234,
        )
        try {
            AidlJdwpProtocol.parseVmVersionReply(packet)
            fail("expected IOException for non-zero errorCode")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("errorCode"))
        }
    }

    @Test
    fun `writeHandshake and readAndVerifyHandshake roundtrip over piped streams`() {
        val pipeOut = PipedOutputStream()
        val pipeIn = PipedInputStream(pipeOut)
        val out = DataOutputStream(pipeOut)
        val input = DataInputStream(pipeIn)
        AidlJdwpProtocol.writeHandshake(out)
        out.flush()
        AidlJdwpProtocol.readAndVerifyHandshake(input)
    }

    @Test
    fun `readAndVerifyHandshake throws on corrupted bytes`() {
        val corrupted = ByteArray(14) { 'A'.code.toByte() }
        val input = DataInputStream(ByteArrayInputStream(corrupted))
        try {
            AidlJdwpProtocol.readAndVerifyHandshake(input)
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Bad handshake"))
        }
    }

    @Test
    fun `performHandshakeAndVersionProbe runs end-to-end against a fake host`() {
        // 开一个 ServerSocket 假装是 host,接受 connect 后跑标准握手 + VM.Version。
        val ss = ServerSocket()
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        val port = ss.localPort

        var hostThreadFailed: Throwable? = null
        val hostThread = thread(name = "fake-host", isDaemon = true) {
            try {
                val client = ss.accept()
                client.use { c ->
                    val cIn = DataInputStream(c.getInputStream())
                    val cOut = DataOutputStream(c.getOutputStream())
                    // 1) 读 IDE 端发来的 handshake
                    AidlJdwpProtocol.readAndVerifyHandshake(cIn)
                    // 2) 回 handshake
                    AidlJdwpProtocol.writeHandshake(cOut)
                    cOut.flush()
                    // 3) 读 VM.Version 命令 (11 字节 header)
                    val header = ByteArray(11)
                    cIn.readFully(header)
                    val cmd = header[10].toInt() and 0xff
                    assertEquals("expected VM.Version command", 0x01, cmd)
                    // 4) 回一个 VM.Version 响应
                    val reply = buildVmVersionReplyPacket(
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
                }
            } catch (t: Throwable) {
                hostThreadFailed = t
            }
        }

        // IDE 端: 连接 -> handshake + VM.Version
        val client = Socket("127.0.0.1", port)
        client.use { c ->
            val info = AidlJdwpProtocol.performHandshakeAndVersionProbe(c, commandId = 7)
            assertEquals("Java JDWP", info.description)
            assertEquals(11, info.jdwpMajor)
            assertEquals(2, info.jdwpMinor)
            assertEquals("1.8.0_292", info.vmVersion)
            assertEquals("OpenJDK 64-Bit Server VM", info.vmName)
        }

        hostThread.join(5_000L)
        ss.close()
        if (hostThreadFailed != null) {
            throw AssertionError("fake host thread failed", hostThreadFailed)
        }
    }

    // ---- 内部辅助: 构造一个 VM.Version 响应包(11 字节 header + 2 字节 errorCode + payload) ----

    private fun buildVmVersionReplyPacket(
        cmdId: Int,
        description: String,
        jdwpMajor: Int,
        jdwpMinor: Int,
        vmVersion: String,
        vmName: String,
        forceErrorCode: Int = 0,
    ): ByteArray {
        val payload = ByteArrayOutputStream()
        // errorCode = 0 (or override)
        payload.write((forceErrorCode ushr 8) and 0xff)
        payload.write(forceErrorCode and 0xff)
        // description string
        writeJdwpString(payload, description)
        // jdwpMajor / jdwpMinor
        writeInt(payload, jdwpMajor)
        writeInt(payload, jdwpMinor)
        // vmVersion / vmName
        writeJdwpString(payload, vmVersion)
        writeJdwpString(payload, vmName)

        val data = payload.toByteArray()
        val out = ByteArrayOutputStream()
        // length = 2 (errorCode) + 4+descLen + 4 + 4 + 4+vmVerLen + 4+vmNameLen = data.size
        writeInt(out, data.size)
        // id
        writeInt(out, cmdId)
        // flags = 0x80 (reply)
        out.write(0x80)
        // commandSet = 1, command = 1
        out.write(0x01)
        out.write(0x01)
        out.write(data)
        return out.toByteArray()
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xff)
        out.write((v ushr 16) and 0xff)
        out.write((v ushr 8) and 0xff)
        out.write(v and 0xff)
    }

    private fun writeJdwpString(out: ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        writeInt(out, bytes.size)
        out.write(bytes)
    }
}
