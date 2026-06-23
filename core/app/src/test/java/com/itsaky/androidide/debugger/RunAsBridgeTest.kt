/*
 *  ZeroStudio IDE - RunAsBridge 单元测试
 *
 *  PR-D4: 验证空 packageName 和空 command 走 fallback 路径.
 *  真实 run-as 行为在 instrumented / 真机测试中覆盖.
 */

package com.itsaky.androidide.debugger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RunAsBridgeTest {

    private val bridge = RunAsBridge()

    @Test
    fun `empty package name yields -1 from probeUid`() {
        assertEquals(-1, bridge.probeUid(""))
    }

    @Test
    fun `empty package name yields empty exec output`() {
        assertEquals("", bridge.exec("", "id", 1000L))
    }

    @Test
    fun `empty command yields empty exec output`() {
        assertEquals("", bridge.exec("com.example", "", 1000L))
    }

    @Test
    fun `fileExists returns false for empty package`() {
        assertFalse(bridge.fileExists("", "/data/local/tmp/x"))
    }

    @Test
    fun `default timeout is positive`() {
        assertTrue("default timeout should be > 0", RunAsBridge.DEFAULT_TIMEOUT_MS > 0)
    }
}
