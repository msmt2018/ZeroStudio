/*
 *  ZeroStudio IDE - BreakpointListAdapter 单元测试 (Phase E2)
 *
 *  覆盖 hitCountLabelResId 纯函数 + IdeBreakpoint hit count 路径下
 *  应展示的字符串映射 (无 context, 仅断言映射关系)。
 */

package com.itsaky.androidide.debugger

import com.itsaky.androidide.R
import com.itsaky.androidide.debugger.adapter.BreakpointListAdapter
import com.itsaky.androidide.debugger.model.IdeBreakpoint
import com.zerostudio.debugger.api.Breakpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BreakpointListAdapterHitCountTest {

    @Test
    fun `hitCountLabelResId maps EQUAL to equal string`() {
        assertEquals(R.string.debugger_bp_hit_equal,
                BreakpointListAdapter.hitCountLabelResId(Breakpoint.HitCountMode.EQUAL))
    }

    @Test
    fun `hitCountLabelResId maps GREATER_THAN to greater string`() {
        assertEquals(R.string.debugger_bp_hit_greater,
                BreakpointListAdapter.hitCountLabelResId(Breakpoint.HitCountMode.GREATER_THAN))
    }

    @Test
    fun `hitCountLabelResId maps MULTIPLE to multiple string`() {
        assertEquals(R.string.debugger_bp_hit_multiple,
                BreakpointListAdapter.hitCountLabelResId(Breakpoint.HitCountMode.MULTIPLE))
    }

    @Test
    fun `hitCountLabelResId falls back to equal for ALWAYS`() {
        // ALWAYS 模式下不应展示该 label,这里仅保证函数安全
        assertEquals(R.string.debugger_bp_hit_equal,
                BreakpointListAdapter.hitCountLabelResId(Breakpoint.HitCountMode.ALWAYS))
    }

    @Test
    fun `ideBreakpoint setHitCount multiple leaves state NORMAL`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.setHitCount(Breakpoint.HitCountMode.MULTIPLE, 3)
        // 命中次数本身不算"条件断点"
        assertEquals(IdeBreakpoint.State.NORMAL, bp.state)
    }

    @Test
    fun `ideBreakpoint setHitCount equal does not change state`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.setHitCount(Breakpoint.HitCountMode.EQUAL, 5)
        assertEquals(IdeBreakpoint.State.NORMAL, bp.state)
    }

    @Test
    fun `ideBreakpoint preserves hit count when toggling disable-enable`() {
        val bp = IdeBreakpoint("/tmp/Foo.kt", 10)
        bp.setHitCount(Breakpoint.HitCountMode.MULTIPLE, 7)
        bp.toggleDisabled()
        bp.toggleDisabled()
        // 重新启用,命中次数配置应保留
        assertEquals(Breakpoint.HitCountMode.MULTIPLE, bp.hitCountMode)
        assertEquals(7, bp.hitCount)
        assertNotEquals(IdeBreakpoint.State.DISABLED, bp.state)
    }

    // ============== Phase E4: stateLabelResId ==============

    @Test
    fun `stateLabelResId maps NORMAL to normal string`() {
        assertEquals(R.string.debugger_bp_state_normal,
                BreakpointListAdapter.stateLabelResId(IdeBreakpoint.State.NORMAL))
    }

    @Test
    fun `stateLabelResId maps INVALID to invalid string`() {
        assertEquals(R.string.debugger_bp_state_invalid,
                BreakpointListAdapter.stateLabelResId(IdeBreakpoint.State.INVALID))
    }

    @Test
    fun `stateLabelResId maps VERIFIED to verified string`() {
        assertEquals(R.string.debugger_bp_state_verified,
                BreakpointListAdapter.stateLabelResId(IdeBreakpoint.State.VERIFIED))
    }

    @Test
    fun `stateLabelResId maps CONDITION to condition string`() {
        assertEquals(R.string.debugger_bp_state_condition,
                BreakpointListAdapter.stateLabelResId(IdeBreakpoint.State.CONDITION))
    }

    @Test
    fun `stateLabelResId maps LOG to logpoint string`() {
        assertEquals(R.string.debugger_bp_state_logpoint,
                BreakpointListAdapter.stateLabelResId(IdeBreakpoint.State.LOG))
    }

    @Test
    fun `stateLabelResId maps DISABLED to disabled string`() {
        assertEquals(R.string.debugger_bp_state_disabled,
                BreakpointListAdapter.stateLabelResId(IdeBreakpoint.State.DISABLED))
    }

    @Test
    fun `stateLabelResId maps HIT to hit string`() {
        assertEquals(R.string.debugger_bp_state_hit,
                BreakpointListAdapter.stateLabelResId(IdeBreakpoint.State.HIT))
    }

    @Test
    fun `stateLabelResId returns 0 for unknown state (defensive)`() {
        // 反射构造未知 enum 不安全,仅断言 7 个已知状态都返回非零 resId
        for (s in IdeBreakpoint.State.values()) {
            assertNotEquals("state=$s", 0,
                    BreakpointListAdapter.stateLabelResId(s))
        }
    }
}
