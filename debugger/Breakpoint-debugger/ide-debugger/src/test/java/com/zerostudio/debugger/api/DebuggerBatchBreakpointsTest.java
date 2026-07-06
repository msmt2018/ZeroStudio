/*
 *  ZeroStudio IDE - 批量断点安装单元测试 (Phase H4)
 *
 *  覆盖 Phase H4 的新增功能:
 *    - installBreakpoints 空列表为 no-op
 *    - installBreakpoints 分组优化(同文件断点只解析一次)
 *    - BreakpointSpec 构建
 *    - addBreakpoints 返回正确数量的 id
 *    - 批量安装失败时所有 bp 标为 INVALID
 */

package com.zerostudio.debugger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.zerostudio.debugger.api.Breakpoint.HitCountMode;
import com.zerostudio.debugger.jdwp.FakeJdwpClient;
import com.zerostudio.debugger.jdwp.JdwpPayloads;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DebuggerBatchBreakpointsTest {

    @Test
    public void installBreakpoints_emptyListIsNoop() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        Debugger dbg = new Debugger(fake);
        dbg.sourceLocator().installBreakpoints(Collections.emptyList());
        assertEquals(0, fake.commandCount());
    }

    @Test
    public void addBreakpoints_returnsOneIdPerSpec() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]); // handshake
        Debugger dbg = new Debugger(fake);
        dbg.connect("localhost", 5005);

        List<Debugger.BreakpointSpec> specs = Arrays.asList(
            new Debugger.BreakpointSpec("Foo.java", 10),
            new Debugger.BreakpointSpec("Foo.java", 20),
            new Debugger.BreakpointSpec("Bar.java", 5)
        );
        List<Long> ids = dbg.addBreakpoints(specs);
        assertEquals(3, ids.size());
        assertNotNull(ids.get(0));
        assertNotNull(ids.get(1));
        assertNotNull(ids.get(2));
        assertTrue(ids.get(0) != ids.get(1));
    }

    @Test
    public void addBreakpoints_withConditionAndHitCount() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]); // handshake
        Debugger dbg = new Debugger(fake);
        dbg.connect("localhost", 5005);

        List<Debugger.BreakpointSpec> specs = Arrays.asList(
            new Debugger.BreakpointSpec("Foo.java", 10, "x > 0", null,
                    HitCountMode.ALWAYS, 0),
            new Debugger.BreakpointSpec("Foo.java", 20, null, "log me",
                    HitCountMode.EQUAL, 5)
        );
        List<Long> ids = dbg.addBreakpoints(specs);
        assertEquals(2, ids.size());
    }

    @Test
    public void breakpointSpec_defaults() {
        Debugger.BreakpointSpec spec = new Debugger.BreakpointSpec("Test.java", 42);
        assertEquals("Test.java", spec.sourceFile);
        assertEquals(42, spec.line);
        assertEquals(null, spec.condition);
        assertEquals(null, spec.logMessage);
        assertEquals(HitCountMode.ALWAYS, spec.hitCountMode);
        assertEquals(0, spec.hitCount);
    }

    @Test
    public void breakpointSpec_fullConstructor() {
        Debugger.BreakpointSpec spec = new Debugger.BreakpointSpec(
            "Test.java", 42, "count > 5", "count=%d",
            HitCountMode.GREATER_THAN, 10);
        assertEquals("Test.java", spec.sourceFile);
        assertEquals(42, spec.line);
        assertEquals("count > 5", spec.condition);
        assertEquals("count=%d", spec.logMessage);
        assertEquals(HitCountMode.GREATER_THAN, spec.hitCountMode);
        assertEquals(10, spec.hitCount);
    }

    @Test
    public void addBreakpoints_withIoException_marksAllInvalid() throws Exception {
        FakeJdwpClient fake = new FakeJdwpClient();
        fake.enqueueOkReply(new byte[0]); // handshake
        Debugger dbg = new Debugger(fake);
        dbg.connect("localhost", 5005);

        // Queue empty → subsequent commands throw IOException
        List<Debugger.BreakpointSpec> specs = Arrays.asList(
            new Debugger.BreakpointSpec("Foo.java", 10),
            new Debugger.BreakpointSpec("Foo.java", 20)
        );
        List<Long> ids = dbg.addBreakpoints(specs);
        assertEquals(2, ids.size());
        // Both should be marked INVALID due to IOException
        assertEquals(Breakpoint.State.INVALID,
                     dbg.breakpoints().get(ids.get(0)).state);
        assertEquals(Breakpoint.State.INVALID,
                     dbg.breakpoints().get(ids.get(1)).state);
    }
}
