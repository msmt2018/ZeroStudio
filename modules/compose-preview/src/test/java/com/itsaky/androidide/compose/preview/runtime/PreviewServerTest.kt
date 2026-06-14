/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.Socket

/**
 * v2.5 P0 P3-FE-05: PreviewServer 单元测试.
 *
 * 端到端: 启动 server, 客户端连上发请求, 校验响应.
 */
class PreviewServerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `parseCommand extracts fields from well-formed json`() {
        val server = PreviewServer(port = 0)  // port 0 ignored (parseCommand doesn't bind)
        val json = """{"function":"com.example.Foo","profileId":"phone-medium","theme":"dark"}"""
        val cmd = server.parseCommand(json)
        assertNotNull(cmd)
        assertEquals("com.example.Foo", cmd!!.functionFqn)
        assertEquals("phone-medium", cmd.profileId)
        assertEquals("dark", cmd.theme)
    }

    @Test
    fun `parseCommand falls back to defaults for missing optional fields`() {
        val server = PreviewServer()
        val cmd = server.parseCommand("""{"function":"x"}""")
        assertNotNull(cmd)
        assertEquals("phone-medium", cmd!!.profileId)
        assertEquals("light", cmd.theme)
    }

    @Test
    fun `parseCommand returns null for missing function field`() {
        val server = PreviewServer()
        assertEquals(null, server.parseCommand("""{"profileId":"foo"}"""))
    }

    @Test
    fun `handler receives parsed command and returns png bytes`() {
        var received: PreviewServer.PreviewCommand? = null
        val handler = PreviewServer.PreviewHandler { cmd ->
            received = cmd
            PreviewServer.PreviewResponse.Ok(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))  // PNG magic
        }
        val port = findFreePort()
        val server = PreviewServer(port = port, handler = handler)
        assertTrue(server.start())
        try {
            val client = java.net.Socket("127.0.0.1", port)
            client.use { c ->
                val out = c.getOutputStream()
                val input = c.getInputStream()
                out.write(PreviewServer.CMD_RENDER)
                val json = """{"function":"com.example.MyComposable","profileId":"phone-large","theme":"light"}"""
                val bytes = json.toByteArray(Charsets.UTF_8)
                out.write((bytes.size shr 24) and 0xFF)
                out.write((bytes.size shr 16) and 0xFF)
                out.write((bytes.size shr 8) and 0xFF)
                out.write(bytes.size and 0xFF)
                out.write(bytes)
                out.flush()

                // 读响应
                val status = readInt(input)
                val len = readInt(input)
                val body = ByteArray(len)
                var total = 0
                while (total < len) total += input.read(body, total, len - total)
                assertEquals(PreviewServer.STATUS_OK, status)
                assertEquals(4, body.size)
            }
            assertNotNull(received)
            assertEquals("com.example.MyComposable", received!!.functionFqn)
            assertEquals("phone-large", received.profileId)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `start is idempotent`() {
        val port = findFreePort()
        val server = PreviewServer(port = port)
        assertTrue(server.start())
        try {
            // 二次 start 应该返回 false
            assertEquals(false, server.start())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `handler exception returns ERR response`() {
        val handler = PreviewServer.PreviewHandler { _ ->
            throw RuntimeException("boom")
        }
        val port = findFreePort()
        val server = PreviewServer(port = port, handler = handler)
        assertTrue(server.start())
        try {
            val client = java.net.Socket("127.0.0.1", port)
            client.use { c ->
                val out = c.getOutputStream()
                out.write(PreviewServer.CMD_RENDER)
                val json = """{"function":"x"}""".toByteArray(Charsets.UTF_8)
                out.writeInt(json.size)
                out.write(json)
                out.flush()

                val status = readInt(c.getInputStream())
                val len = readInt(c.getInputStream())
                val body = ByteArray(len)
                var total = 0
                while (total < len) total += c.getInputStream().read(body, total, len - total)
                assertEquals(PreviewServer.STATUS_ERR, status)
                assertTrue(String(body, Charsets.UTF_8).contains("boom"))
            }
        } finally {
            server.stop()
        }
    }

    private fun readInt(input: java.io.InputStream): Int {
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        val b4 = input.read()
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    private fun findFreePort(): Int = java.net.ServerSocket(0).use { it.localPort }
}
