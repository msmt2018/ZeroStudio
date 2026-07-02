/*
 *  ZeroStudio IDE - 断点调试器连接层
 *
 *  HostBridgeServer 单元测试:
 *    - bind abstract socket (用唯一名字避免冲突)
 *    - 用 LocalSocket 连接 + 发 HELLO
 *    - listener 收到正确的 HostConnection (pkg / pid)
 *    - HELLO 解析失败时丢弃连接
 */

package com.itsaky.androidide.debugger.connection.host

import android.net.LocalSocket
import android.net.LocalSocketAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class HostBridgeServerTest {

    private var server: HostBridgeServer? = null

    @After
    fun tearDown() {
        server?.stop()
        server = null
    }

    private fun uniqueName(suffix: String): String =
        "test-ide-bridge-${System.nanoTime()}-$suffix"

    @Test
    fun `server is not running before start`() {
        val s = HostBridgeServer(uniqueName("a"))
        server = s
        assertEquals(false, s.isRunning())
    }

    @Test
    fun `start binds and accept loop runs`() {
        val s = HostBridgeServer(uniqueName("start"))
        server = s
        s.start()
        assertTrue("server should be running", s.isRunning())
    }

    @Test
    fun `listener receives correct Hello for valid HELLO line`() {
        val s = HostBridgeServer(uniqueName("hello"))
        server = s
        val received = CountDownLatch(1)
        var captured: HostConnection? = null
        s.setListener { conn ->
            captured = conn
            received.countDown()
        }
        s.start()

        // client 端: LocalSocket.connect + send HELLO + leave open
        val clientSock = LocalSocket()
        clientSock.connect(LocalSocketAddress(s.localSocketName, LocalSocketAddress.Namespace.ABSTRACT))
        val hello = "HELLO pkg=com.example.app pid=12345 build=debug"
        clientSock.outputStream.write((hello + "\n").toByteArray(Charsets.UTF_8))
        clientSock.outputStream.flush()

        assertTrue("listener should be called within 3s", received.await(3, TimeUnit.SECONDS))
        val conn = captured!!
        assertEquals("com.example.app", conn.hello.packageName)
        assertEquals(12345, conn.hello.pid)
        assertEquals(hello, conn.hello.raw)
        runCatching { clientSock.close() }
    }

    @Test
    fun `HELLO with extra fields still parses pkg and pid`() {
        val s = HostBridgeServer(uniqueName("extra"))
        server = s
        val received = CountDownLatch(1)
        var captured: HostConnection? = null
        s.setListener { conn ->
            captured = conn
            received.countDown()
        }
        s.start()

        val clientSock = LocalSocket()
        clientSock.connect(LocalSocketAddress(s.localSocketName, LocalSocketAddress.Namespace.ABSTRACT))
        clientSock.outputStream.write(
            "HELLO pkg=org.test.app pid=999 arch=arm64-v8a sdk=34\n".toByteArray(Charsets.UTF_8)
        )
        clientSock.outputStream.flush()

        assertTrue(received.await(3, TimeUnit.SECONDS))
        val conn = captured!!
        assertEquals("org.test.app", conn.hello.packageName)
        assertEquals(999, conn.hello.pid)
        runCatching { clientSock.close() }
    }

    @Test
    fun `invalid HELLO is dropped (no listener call)`() {
        val s = HostBridgeServer(uniqueName("invalid"))
        server = s
        val received = CountDownLatch(1)
        s.setListener { received.countDown() }
        s.start()

        val clientSock = LocalSocket()
        clientSock.connect(LocalSocketAddress(s.localSocketName, LocalSocketAddress.Namespace.ABSTRACT))
        // 缺 HELLO 前缀
        clientSock.outputStream.write("GHOST pkg=x pid=1\n".toByteArray(Charsets.UTF_8))
        clientSock.outputStream.flush()

        // 等 2s 确认 listener 没被调
        assertEquals(false, received.await(2, TimeUnit.SECONDS))
        runCatching { clientSock.close() }
    }

    @Test
    fun `HELLO with missing pkg is dropped`() {
        val s = HostBridgeServer(uniqueName("nospkg"))
        server = s
        val received = CountDownLatch(1)
        s.setListener { received.countDown() }
        s.start()

        val clientSock = LocalSocket()
        clientSock.connect(LocalSocketAddress(s.localSocketName, LocalSocketAddress.Namespace.ABSTRACT))
        clientSock.outputStream.write("HELLO pid=42\n".toByteArray(Charsets.UTF_8))
        clientSock.outputStream.flush()

        assertEquals(false, received.await(2, TimeUnit.SECONDS))
        runCatching { clientSock.close() }
    }

    @Test
    fun `HELLO with non-numeric pid is dropped`() {
        val s = HostBridgeServer(uniqueName("badpid"))
        server = s
        val received = CountDownLatch(1)
        s.setListener { received.countDown() }
        s.start()

        val clientSock = LocalSocket()
        clientSock.connect(LocalSocketAddress(s.localSocketName, LocalSocketAddress.Namespace.ABSTRACT))
        clientSock.outputStream.write("HELLO pkg=com.foo pid=abc\n".toByteArray(Charsets.UTF_8))
        clientSock.outputStream.flush()

        assertEquals(false, received.await(2, TimeUnit.SECONDS))
        runCatching { clientSock.close() }
    }

    @Test
    fun `stop closes server and ends accept loop`() {
        val s = HostBridgeServer(uniqueName("stop"))
        s.start()
        assertTrue(s.isRunning())
        s.stop()
        assertEquals(false, s.isRunning())
    }

    @Test
    fun `multiple host connections are all received`() {
        val s = HostBridgeServer(uniqueName("multi"))
        server = s
        val received = CountDownLatch(3)
        s.setListener { received.countDown() }
        s.start()

        repeat(3) { i ->
            val c = LocalSocket()
            c.connect(LocalSocketAddress(s.localSocketName, LocalSocketAddress.Namespace.ABSTRACT))
            c.outputStream.write("HELLO pkg=com.test$i pid=${100 + i}\n".toByteArray(Charsets.UTF_8))
            c.outputStream.flush()
            // 不关, 让 listener 能看到
        }
        assertTrue("all 3 HELLOs should be received", received.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `awaitNextConnection returns null when no connection within timeout`() {
        val s = HostBridgeServer(uniqueName("await"))
        server = s
        s.start()
        val conn = s.awaitNextConnection(200L)
        assertNull("no connection yet, should return null", conn)
    }
}
