/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuSocksClient 单元测试: SOCKS5 客户端协议握手。
 *  用真 ServerSocket + 假 SOCKS5 server 验证客户端发出对的字节。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

@RunWith(JUnit4::class)
class ShizukuSocksClientTest {

    @Test
    fun `SOCKS5 connect sends no-auth greeting and parses server method 00`() {
        val ss = ServerSocket()
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        val port = ss.localPort

        var serverError: Throwable? = null
        var receivedGreeting: ByteArray? = null
        var receivedRequest: ByteArray? = null
        val hostThread = thread(name = "fake-socks-server", isDaemon = true) {
            try {
                val client = ss.accept()
                client.use { c ->
                    val cIn = DataInputStream(c.getInputStream())
                    val cOut = DataOutputStream(c.getOutputStream())
                    val greeting = ByteArray(3)
                    cIn.readFully(greeting)
                    receivedGreeting = greeting
                    cOut.write(byteArrayOf(0x05, 0x00)) // no auth
                    cOut.flush()
                    val request = ByteArray(10) // VER CMD RSV ATYP ADDR(4) PORT(2)
                    cIn.readFully(request)
                    receivedRequest = request
                    cOut.write(byteArrayOf(0x05.toByte(), 0x00, 0x00, 0x01, 0x7f, 0x00, 0x00, 0x01, 0x00, 0x50))
                    cOut.flush()
                    Thread.sleep(200)
                }
            } catch (t: Throwable) {
                serverError = t
            }
        }

        val client = ShizukuSocksClient()
        val sock = try {
            client.connect(
                proxyAddr = InetSocketAddress("127.0.0.1", port),
                targetHost = "127.0.0.1",
                targetPort = 80,
            )
        } catch (t: Throwable) {
            ss.close()
            hostThread.join(2000L)
            throw t
        }
        hostThread.join(5000L)
        ss.close()

        // Verify greeting
        assertArrayEquals(byteArrayOf(0x05, 0x01, 0x00), receivedGreeting)
        // Verify request: VER=05 CMD=01 RSV=00 ATYP=01 127.0.0.1 0.0.80
        assertNotNull(receivedRequest)
        assertEquals(0x05, (receivedRequest!![0].toInt() and 0xff))
        assertEquals(0x01, (receivedRequest[1].toInt() and 0xff))
        assertEquals(0x00, (receivedRequest[2].toInt() and 0xff))
        assertEquals(0x01, (receivedRequest[3].toInt() and 0xff))
        assertEquals(127, (receivedRequest[4].toInt() and 0xff))
        assertEquals(0, (receivedRequest[5].toInt() and 0xff))
        assertEquals(0, (receivedRequest[6].toInt() and 0xff))
        assertEquals(80, (receivedRequest[7].toInt() and 0xff))

        assertTrue(sock.isConnected)
        sock.close()
        if (serverError != null) throw AssertionError("fake server failed", serverError)
    }

    @Test
    fun `SOCKS5 connect throws on non-zero REP`() {
        val ss = ServerSocket()
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        val port = ss.localPort

        val hostThread = thread(name = "fake-socks-reject", isDaemon = true) {
            try {
                val client = ss.accept()
                client.use { c ->
                    val cIn = DataInputStream(c.getInputStream())
                    val cOut = DataOutputStream(c.getOutputStream())
                    val greeting = ByteArray(3)
                    cIn.readFully(greeting)
                    cOut.write(byteArrayOf(0x05, 0x00))
                    cOut.flush()
                    val request = ByteArray(10)
                    cIn.readFully(request)
                    // Reply with REP=05 (Connection refused)
                    cOut.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                    cOut.flush()
                }
            } catch (_: Throwable) { }
        }

        val client = ShizukuSocksClient()
        try {
            client.connect(
                proxyAddr = InetSocketAddress("127.0.0.1", port),
                targetHost = "127.0.0.1",
                targetPort = 80,
            )
            fail("expected IOException for REP=05")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("REP=5"))
        }
        hostThread.join(2000L)
        ss.close()
    }

    @Test
    fun `SOCKS5 connect throws on bad server version`() {
        val ss = ServerSocket()
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        val port = ss.localPort

        val hostThread = thread(name = "fake-socks-badver", isDaemon = true) {
            try {
                val client = ss.accept()
                client.use { c ->
                    val cIn = DataInputStream(c.getInputStream())
                    val cOut = DataOutputStream(c.getOutputStream())
                    cIn.readFully(ByteArray(3))
                    cOut.write(byteArrayOf(0x04, 0x00)) // VER=04, wrong
                    cOut.flush()
                }
            } catch (_: Throwable) { }
        }

        val client = ShizukuSocksClient()
        try {
            client.connect(
                proxyAddr = InetSocketAddress("127.0.0.1", port),
                targetHost = "127.0.0.1",
                targetPort = 80,
            )
            fail("expected IOException for bad version")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("version=4"))
        }
        hostThread.join(2000L)
        ss.close()
    }
}
