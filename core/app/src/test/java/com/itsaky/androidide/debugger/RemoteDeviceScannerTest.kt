/*
 *  ZeroStudio IDE - RemoteDeviceScanner 单元测试
 *
 *  PR-D6: 校验常量,probeAdbPort 在 unreachable host 上返回 0.
 */

package com.itsaky.androidide.debugger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RemoteDeviceScannerTest {

    @Test
    fun `constants are stable`() {
        assertEquals("_adb._tcp", RemoteDeviceScanner.ADB_SERVICE_TYPE)
        assertEquals(5555, RemoteDeviceScanner.DEFAULT_ADB_PORT)
        assertTrue(RemoteDeviceScanner.DEFAULT_SCAN_TIMEOUT_MS > 0)
    }

    @Test
    fun `lastResult starts empty`() {
        val scanner = RemoteDeviceScanner()
        assertEquals(0, scanner.lastResult().size)
        scanner.shutdown()
    }

    @Test
    fun `probeAdbPort on unroutable address returns 0`() {
        val scanner = RemoteDeviceScanner()
        try {
            // 192.0.2.0/24 is reserved (TEST-NET-1 RFC 5737); should
            // never have adb open.
            val port = scanner.probeAdbPort("192.0.2.1", 250L)
            assertEquals(0, port)
        } finally {
            scanner.shutdown()
        }
    }

    @Test
    fun `DeviceInfo toString is human readable`() {
        val d = RemoteDeviceScanner.DeviceInfo("pixel", "10.0.0.1", 5555)
        val s = d.toString()
        assertTrue(s.contains("pixel"))
        assertTrue(s.contains("10.0.0.1"))
        assertTrue(s.contains("5555"))
    }
}
