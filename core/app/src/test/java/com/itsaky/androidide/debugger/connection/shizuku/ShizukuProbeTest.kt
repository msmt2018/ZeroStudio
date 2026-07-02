/*
 *  ZeroStudio IDE - Debugger Connection Layer
 *
 *  ShizukuProbe 单元测试: 状态探测 + 权限请求 (fake Shizuku 状态)。
 */

package com.itsaky.androidide.debugger.connection.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ShizukuProbeTest {

    @Test
    fun `ShizukuStatus isReady when running and granted`() {
        val status = ShizukuStatus(
            isRunning = true,
            isGranted = true,
            serverUid = 1000,
            serverApiVersion = 13,
        )
        assertTrue(status.isReady)
        assertTrue(status.isRunning)
        assertTrue(status.isGranted)
    }

    @Test
    fun `ShizukuStatus is not ready when not running`() {
        val status = ShizukuStatus(
            isRunning = false,
            isGranted = false,
            serverUid = -1,
            serverApiVersion = -1,
        )
        assertFalse(status.isReady)
        assertFalse(status.isRunning)
    }

    @Test
    fun `ShizukuStatus is not ready when running but not granted`() {
        val status = ShizukuStatus(
            isRunning = true,
            isGranted = false,
            serverUid = 1000,
            serverApiVersion = 13,
            notRunningReason = "Shizuku 未授权给当前 IDE app",
        )
        assertFalse(status.isReady)
        assertTrue(status.isRunning)
        assertFalse(status.isGranted)
        assertEquals("Shizuku 未授权给当前 IDE app", status.notRunningReason)
    }

    @Test
    fun `FakeShizukuProbe returns preset status`() {
        val status = ShizukuStatus(
            isRunning = true,
            isGranted = true,
            serverUid = 1000,
            serverApiVersion = 13,
        )
        val probe = FakeShizukuProbe(status = status)
        val r = probe.probe()
        assertEquals(status, r)
        assertEquals(1, probe.probeCount)
    }

    @Test
    fun `FakeShizukuProbe requestPermissionIfNeeded updates status when granted`() {
        val probe = FakeShizukuProbe(
            status = ShizukuStatus(
                isRunning = true,
                isGranted = false,
                serverUid = 1000,
                serverApiVersion = 13,
            ),
            grantResult = true,
        )
        val ok = probe.requestPermissionIfNeeded()
        assertTrue(ok)
        assertEquals(1, probe.requestCount)
        assertTrue(probe.probe().isGranted)
    }

    @Test
    fun `FakeShizukuProbe requestPermissionIfNeeded returns false when denied`() {
        val probe = FakeShizukuProbe(
            status = ShizukuStatus(
                isRunning = true,
                isGranted = false,
                serverUid = 1000,
                serverApiVersion = 13,
            ),
            grantResult = false,
        )
        val ok = probe.requestPermissionIfNeeded()
        assertFalse(ok)
        assertFalse(probe.probe().isGranted)
    }
}
