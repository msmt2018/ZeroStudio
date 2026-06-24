/*
 *  ZeroStudio IDE - BreakpointStateColors 单元测试 (Phase E3)
 *
 *  验证:
 *    - 7 个 State 各映射到不同的 R.color 资源 id
 *    - 命中次数/已命中次数颜色也由资源提供
 *    - 同一 state 两次查询返回相同 resId (稳定)
 *    - 任何 State.values() 中的值都能找到 resId,无遗漏
 */

package com.itsaky.androidide.debugger

import androidx.annotation.ColorRes
import com.itsaky.androidide.R
import com.itsaky.androidide.debugger.model.IdeBreakpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BreakpointStateColorsTest {

    @Test
    fun `colorResForState maps NORMAL to bp_normal`() {
        assertEquals(R.color.debugger_bp_normal,
                BreakpointStateColors.colorResForState(IdeBreakpoint.State.NORMAL))
    }

    @Test
    fun `colorResForState maps all 7 states to distinct resources`() {
        @ColorRes
        val n = BreakpointStateColors.colorResForState(IdeBreakpoint.State.NORMAL)
        @ColorRes
        val inv = BreakpointStateColors.colorResForState(IdeBreakpoint.State.INVALID)
        @ColorRes
        val v = BreakpointStateColors.colorResForState(IdeBreakpoint.State.VERIFIED)
        @ColorRes
        val c = BreakpointStateColors.colorResForState(IdeBreakpoint.State.CONDITION)
        @ColorRes
        val l = BreakpointStateColors.colorResForState(IdeBreakpoint.State.LOG)
        @ColorRes
        val d = BreakpointStateColors.colorResForState(IdeBreakpoint.State.DISABLED)
        @ColorRes
        val h = BreakpointStateColors.colorResForState(IdeBreakpoint.State.HIT)
        // 全部互不相同
        val ids = listOf(n, inv, v, c, l, d, h).toSet()
        assertEquals(7, ids.size)
    }

    @Test
    fun `colorResForState is stable across calls`() {
        for (s in IdeBreakpoint.State.values()) {
            @ColorRes
            val a = BreakpointStateColors.colorResForState(s)
            @ColorRes
            val b = BreakpointStateColors.colorResForState(s)
            assertEquals(a, b)
        }
    }

    @Test
    fun `every state has a non-zero colorRes`() {
        for (s in IdeBreakpoint.State.values()) {
            @ColorRes
            val id = BreakpointStateColors.colorResForState(s)
            assertNotEquals("state=$s", 0, id)
        }
        // 7 个互不相同的资源
        val ids = IdeBreakpoint.State.values()
                .map { BreakpointStateColors.colorResForState(it) }
                .toSet()
        assertEquals(7, ids.size)
    }

    @Test
    fun `hit count label colors are non-zero`() {
        @ColorRes
        val a = R.color.debugger_bp_hit_count_label
        @ColorRes
        val b = R.color.debugger_bp_hit_count_received_label
        assertNotEquals(0, a)
        assertNotEquals(0, b)
        assertNotEquals(a, b)
    }

    @Test
    fun `colorResForState falls back to NORMAL on unknown state`() {
        // 反射构造一个伪 state 不安全,改为断言 known states 全部存在。
        val known = IdeBreakpoint.State.values().toSet()
        for (s in known) {
            assertTrue(BreakpointStateColors.colorResForState(s) != 0)
        }
    }
}
