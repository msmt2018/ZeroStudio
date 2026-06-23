/*
 *  ZeroStudio IDE - IdeBreakpoint 单元测试 (Phase E2)
 *
 *  覆盖:
 *    - 默认构造器字段值
 *    - 兼容性构造器(7-arg / 8-arg)正确把 hitCount 解释为阈值
 *    - setHitCount 模式切换 + 阈值
 *    - setCondition / setLogMessage 与 hitCountMode 联动时的状态机
 *    - hitCountReceived 计数器互不干扰
 */

package com.itsaky.androidide.debugger.model

import com.zerostudio.debugger.api.Breakpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class IdeBreakpointHitCountTest {

    @Test
    fun `default constructor uses ALWAYS mode`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        assertEquals(Breakpoint.HitCountMode.ALWAYS, bp.hitCountMode)
        assertEquals(0, bp.hitCount)
        assertEquals(0, bp.hitCountReceived)
        assertEquals(IdeBreakpoint.State.NORMAL, bp.state)
    }

    @Test
    fun `legacy 7-arg constructor preserves hit count as threshold`() {
        val bp = IdeBreakpoint(
                "abc-123",
                "/tmp/Foo.kt",
                10,
                "i > 0",
                IdeBreakpoint.State.CONDITION,
                -1L,
                5 /* hitCount 阈值 */)
        assertEquals(Breakpoint.HitCountMode.ALWAYS, bp.hitCountMode)
        assertEquals(5, bp.hitCount)
        assertEquals(0, bp.hitCountReceived)
    }

    @Test
    fun `legacy 8-arg constructor preserves both condition and log`() {
        val bp = IdeBreakpoint(
                "abc-123",
                "/tmp/Foo.kt",
                10,
                "i > 0",
                "x=" + "${'$'}{x}",
                IdeBreakpoint.State.LOG,
                3L,
                7 /* hitCount 阈值 */)
        assertEquals(7, bp.hitCount)
        assertEquals("x=" + "${'$'}{x}", bp.logMessage)
    }

    @Test
    fun `setHitCount equal stores mode and count`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.setHitCount(Breakpoint.HitCountMode.EQUAL, 5)
        assertEquals(Breakpoint.HitCountMode.EQUAL, bp.hitCountMode)
        assertEquals(5, bp.hitCount)
        // 设置命中次数本身不算条件断点,状态保持 NORMAL
        assertEquals(IdeBreakpoint.State.NORMAL, bp.state)
    }

    @Test
    fun `setHitCount always with count clears to ALWAYS`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.setHitCount(Breakpoint.HitCountMode.MULTIPLE, 10)
        bp.setHitCount(Breakpoint.HitCountMode.ALWAYS, 0)
        assertEquals(Breakpoint.HitCountMode.ALWAYS, bp.hitCountMode)
        assertEquals(0, bp.hitCount)
    }

    @Test
    fun `setHitCount negative count clamped to zero`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.setHitCount(Breakpoint.HitCountMode.EQUAL, -5)
        assertEquals(0, bp.hitCount)
    }

    @Test
    fun `setHitCount null mode falls back to ALWAYS`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.setHitCount(Breakpoint.HitCountMode.EQUAL, 3)
        bp.setHitCount(null as Breakpoint.HitCountMode?, 99)
        assertEquals(Breakpoint.HitCountMode.ALWAYS, bp.hitCountMode)
    }

    @Test
    fun `setHitCount does not override disabled`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.toggleDisabled()
        bp.setHitCount(Breakpoint.HitCountMode.EQUAL, 5)
        assertEquals(IdeBreakpoint.State.DISABLED, bp.state)
    }

    @Test
    fun `setHitCount keeps LOG state when log message present`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.setLogMessage("x=")
        bp.setHitCount(Breakpoint.HitCountMode.MULTIPLE, 5)
        assertEquals(IdeBreakpoint.State.LOG, bp.state)
    }

    @Test
    fun `setCondition after hit count returns to CONDITION`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.setHitCount(Breakpoint.HitCountMode.MULTIPLE, 5)
        bp.setCondition("i > 0")
        assertEquals(IdeBreakpoint.State.CONDITION, bp.state)
    }

    @Test
    fun `hitCountReceived and hitCount are independent`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.setHitCount(Breakpoint.HitCountMode.MULTIPLE, 3)
        bp.hitCountReceived = 17
        // 互不干扰
        assertEquals(3, bp.hitCount)
        assertEquals(17, bp.hitCountReceived)
        assertNotEquals(bp.hitCount, bp.hitCountReceived)
    }

    @Test
    fun `toString includes hit count summary only when non-default`() {
        val defaultBp = IdeBreakpoint("/tmp/Foo.kt", 10)
        assertFalse(defaultBp.toString().contains("hitCount="))

        val countedBp = IdeBreakpoint("/tmp/Foo.kt", 10)
        countedBp.setHitCount(Breakpoint.HitCountMode.MULTIPLE, 10)
        assertTrue(countedBp.toString().contains("hitCount=MULTIPLE:10"))
    }
}
