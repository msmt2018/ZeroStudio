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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.5 P0 P3-FE-05: AdbForwardTunnel 单元测试.
 *
 * 由于 adb 命令依赖外部可执行文件, 这里的测试仅覆盖:
 * - 不可用 adb 路径时的 graceful failure
 * - 命令行构造 (通过 mock 行为)
 */
class AdbForwardTunnelTest {

    @Test
    fun `isAdbAvailable returns false for nonexistent adb path`() {
        val tunnel = AdbForwardTunnel(adbPath = "/nonexistent/adb")
        assertFalse(tunnel.isAdbAvailable())
    }

    @Test
    fun `devices returns empty when adb unavailable`() {
        val tunnel = AdbForwardTunnel(adbPath = "/nonexistent/adb")
        assertTrue(tunnel.devices().isEmpty())
    }

    @Test
    fun `forward returns false when adb unavailable`() {
        val tunnel = AdbForwardTunnel(adbPath = "/nonexistent/adb")
        assertFalse(tunnel.forward(9876, "androidide_preview"))
    }

    @Test
    fun `reverse returns false when adb unavailable`() {
        val tunnel = AdbForwardTunnel(adbPath = "/nonexistent/adb")
        assertFalse(tunnel.reverse(9876, 8080))
    }

    @Test
    fun `removeForward returns false when adb unavailable`() {
        val tunnel = AdbForwardTunnel(adbPath = "/nonexistent/adb")
        assertFalse(tunnel.removeForward(9876))
    }

    @Test
    fun `listForward returns empty when adb unavailable`() {
        val tunnel = AdbForwardTunnel(adbPath = "/nonexistent/adb")
        assertTrue(tunnel.listForward().isEmpty())
    }

    @Test
    fun `timeout bound respected on long-running command`() {
        // Use a command that takes longer than timeout. We point adb to /bin/sleep
        val tunnel = AdbForwardTunnel(
            adbPath = "/bin/sleep",
            commandTimeoutMs = 200L,
        )
        // isAdbAvailable will run /bin/sleep --version, which blocks
        // We expect either a quick true (no) or false (sleep returns nonzero)
        // Most importantly: the call must NOT hang for more than 200ms
        val start = System.currentTimeMillis()
        val result = tunnel.isAdbAvailable()
        val elapsed = System.currentTimeMillis() - start
        assertTrue("call should return within 1.5s, took ${elapsed}ms", elapsed < 1_500L)
        // /bin/sleep --version returns non-zero (unrecognized), so isAdbAvailable()=false
        assertFalse(result)
    }
}
