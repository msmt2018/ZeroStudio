package com.zerostudio.language.breakpoint;

import com.zerostudio.language.runtime.FrameSnapshot;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class BreakpointManagerTest {

    private FrameSnapshot mkFrame(String cls, String method) {
        FrameSnapshot f = new FrameSnapshot();
        f.addFrame(new FrameSnapshot.StackFrame(method, cls, 10, null));
        return f;
    }

    @Test
    public void functionBreakpointMatches() {
        BreakpointManager mgr = new BreakpointManager();
        mgr.addFunction(new BreakpointManager.FunctionBreakpoint("com.x.A", "foo", null, 0));
        List<Breakpoint.HitResult> hits = mgr.checkFunctionEntry(mkFrame("com.x.A", "foo"));
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).stop);
    }

    @Test
    public void functionBreakpointNoMatchDifferentMethod() {
        BreakpointManager mgr = new BreakpointManager();
        mgr.addFunction(new BreakpointManager.FunctionBreakpoint("com.x.A", "foo", null, 0));
        assertTrue(mgr.checkFunctionEntry(mkFrame("com.x.A", "bar")).isEmpty());
    }

    @Test
    public void functionBreakpointNoMatchDifferentClass() {
        BreakpointManager mgr = new BreakpointManager();
        mgr.addFunction(new BreakpointManager.FunctionBreakpoint("com.x.A", "foo", null, 0));
        assertTrue(mgr.checkFunctionEntry(mkFrame("com.x.B", "foo")).isEmpty());
    }

    @Test
    public void functionBreakpointHitLimitRemoves() {
        BreakpointManager mgr = new BreakpointManager();
        BreakpointManager.FunctionBreakpoint fb = new BreakpointManager.FunctionBreakpoint("A", "foo", null, 2);
        mgr.addFunction(fb);
        // 第 1 次：保留
        mgr.checkFunctionEntry(mkFrame("A", "foo"));
        assertEquals(1, mgr.functions().size());
        // 第 2 次：移除
        mgr.checkFunctionEntry(mkFrame("A", "foo"));
        assertEquals(0, mgr.functions().size());
    }

    @Test
    public void fieldWatchpointReadWrite() {
        BreakpointManager mgr = new BreakpointManager();
        mgr.addWatchpoint(new BreakpointManager.FieldWatchpoint("A", "count", BreakpointManager.AccessMode.WRITE));
        FrameSnapshot f = mkFrame("A", "m");
        assertTrue(mgr.checkFieldAccess(f, "count", false).isEmpty());
        List<Breakpoint.HitResult> hits = mgr.checkFieldAccess(f, "count", true);
        assertEquals(1, hits.size());
    }

    @Test
    public void fieldWatchpointReadOnly() {
        BreakpointManager mgr = new BreakpointManager();
        mgr.addWatchpoint(new BreakpointManager.FieldWatchpoint("A", "count", BreakpointManager.AccessMode.READ));
        FrameSnapshot f = mkFrame("A", "m");
        assertEquals(1, mgr.checkFieldAccess(f, "count", false).size());
        assertTrue(mgr.checkFieldAccess(f, "count", true).isEmpty());
    }

    @Test
    public void fieldWatchpointDifferentField() {
        BreakpointManager mgr = new BreakpointManager();
        mgr.addWatchpoint(new BreakpointManager.FieldWatchpoint("A", "count", BreakpointManager.AccessMode.READ_WRITE));
        FrameSnapshot f = mkFrame("A", "m");
        assertTrue(mgr.checkFieldAccess(f, "name", true).isEmpty());
    }

    @Test
    public void disabledFunctionBreakpointSkipped() {
        BreakpointManager mgr = new BreakpointManager();
        BreakpointManager.FunctionBreakpoint fb = new BreakpointManager.FunctionBreakpoint("A", "foo", null, 0);
        fb.enabled = false;
        mgr.addFunction(fb);
        assertTrue(mgr.checkFunctionEntry(mkFrame("A", "foo")).isEmpty());
    }

    @Test
    public void findMethodLineReturnsCorrectLine() {
        String src = "class A {\n" +
                "    public void foo() {}\n" +
                "    public int bar(int x) { return x; }\n" +
                "}\n";
        assertEquals(2, BreakpointManager.findMethodLine(src, "foo"));
        assertEquals(3, BreakpointManager.findMethodLine(src, "bar"));
    }

    @Test
    public void findMethodLineNotFoundReturnsNegative() {
        assertEquals(-1, BreakpointManager.findMethodLine("class A {}", "missing"));
        assertEquals(-1, BreakpointManager.findMethodLine(null, "x"));
    }

    @Test
    public void createTemporaryBreakpoint() {
        Breakpoint bp = BreakpointManager.createTemporary("A.java", 10, 1);
        assertEquals(Breakpoint.Kind.LINE, bp.kind);
        assertEquals(10, bp.line);
        assertEquals(1, bp.hitThreshold);
    }

    @Test
    public void removeById() {
        BreakpointManager mgr = new BreakpointManager();
        BreakpointManager.FunctionBreakpoint fb = new BreakpointManager.FunctionBreakpoint("A", "foo", null, 0);
        mgr.addFunction(fb);
        mgr.removeFunction(fb.id);
        assertEquals(0, mgr.functions().size());
    }

    @Test
    public void nullFrameReturnsEmpty() {
        BreakpointManager mgr = new BreakpointManager();
        mgr.addFunction(new BreakpointManager.FunctionBreakpoint("A", "foo", null, 0));
        assertTrue(mgr.checkFunctionEntry(null).isEmpty());
        assertTrue(mgr.checkFieldAccess(null, "x", true).isEmpty());
    }
}
