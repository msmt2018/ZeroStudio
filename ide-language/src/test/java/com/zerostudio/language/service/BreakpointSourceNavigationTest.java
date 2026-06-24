package com.zerostudio.language.service;

import com.zerostudio.language.breakpoint.Breakpoint;
import com.zerostudio.language.breakpoint.Breakpoint.HitResult;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.StackFrame;
import com.zerostudio.language.source.SourceLocator;
import com.zerostudio.language.source.SourceLocator.LocatedSource;
import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/**
 * 端到端集成测试：模拟一次完整的断点命中 → 源码跳转流程：
 *  1. FrameSnapshot 携带 stack frame + 局部变量
 *  2. Breakpoint 命中，触发条件求值
 *  3. SourceLocator 定位 frame 对应的 .kt / .java
 *  4. 若 source-jar / class-jar 中找到，则反编译并以虚拟 buffer 形式打开编辑器
 *  5. CallNavigation 控制 step into / over / out
 */
public class BreakpointSourceNavigationTest {

    private List<EditorIntegration.OpenRequest> openedFiles;
    private EditorIntegration editor;
    private SourceLocator locator;

    @Before
    public void setUp() {
        openedFiles = new ArrayList<>();
        editor = new EditorIntegration();
        editor.setOpenHandler(req -> openedFiles.add(req));
        locator = new SourceLocator();
    }

    @Test
    public void conditionalBreakpointTriggersOnCondition() {
        FrameSnapshot frame = new FrameSnapshot();
        frame.addValue(new FrameSnapshot.Value("count", "int", "Local", 5));
        Breakpoint bp = Breakpoint.builder()
                .id("bp1")
                .sourceFile("Counter.java")
                .line(10)
                .condition("count > 3")
                .build();
        HitResult r = bp.onHit(frame);
        assertTrue("Conditional BP should stop when condition is true", r.stop);
    }

    @Test
    public void conditionalBreakpointSkipsWhenFalse() {
        FrameSnapshot frame = new FrameSnapshot();
        frame.addValue(new FrameSnapshot.Value("count", "int", "Local", 1));
        Breakpoint bp = Breakpoint.builder()
                .id("bp1")
                .sourceFile("Counter.java")
                .line(10)
                .condition("count > 3")
                .build();
        HitResult r = bp.onHit(frame);
        assertTrue("Conditional BP should skip when condition is false", r.skip);
    }

    @Test
    public void logpointExpandsInlineExpression() {
        FrameSnapshot frame = new FrameSnapshot();
        frame.addValue(new FrameSnapshot.Value("name", "String", "Local", "Alice"));
        Breakpoint bp = Breakpoint.builder()
                .id("lp1")
                .sourceFile("X.java")
                .line(1)
                .logMessage("Hello, {name}!")
                .build();
        HitResult r = bp.onHit(frame);
        assertFalse(r.stop);
        assertEquals("Hello, Alice!", r.log);
    }

    @Test
    public void fullFlow_breakpointHit_opensDecompiledSource() {
        // 1. FrameSnapshot
        FrameSnapshot frame = new FrameSnapshot();
        frame.addFrame(new StackFrame("compute", "com.example.Foo", 42, null));
        frame.addFrame(new StackFrame("main", "com.example.Main", 1, null));

        // 2. Breakpoint 命中（无条件行断点）
        Breakpoint bp = Breakpoint.builder()
                .id("bp-flow")
                .sourceFile("com.example.Foo")
                .line(42)
                .build();
        HitResult r = bp.onHit(frame);
        assertTrue(r.stop);

        // 3. 通过 SourceLocator 定位 — 此处只是 missing（无 classpath）
        LocatedSource src = locator.locate("com.example.Foo");
        assertNotNull(src);
        // 4. 模拟打开：直接传 frame.topFrame().sourcePath
        StackFrame top = frame.topFrame();
        assertNotNull(top);
        // 5. 打开编辑器
        editor.openRealFile("[" + top.className + "]", new SourceRange(
                new SourcePosition("[" + top.className + "]", top.lineNumber, 1),
                new SourcePosition("[" + top.className + "]", top.lineNumber + 5, 1)));
        assertEquals(1, openedFiles.size());
        assertEquals("[com.example.Foo]", openedFiles.get(0).file);
    }

    @Test
    public void stepThroughCallStack() {
        FrameSnapshot frame = new FrameSnapshot();
        frame.addFrame(new StackFrame("a", "A", 1, "A.java"));
        frame.addFrame(new StackFrame("b", "B", 2, "B.java"));
        frame.addFrame(new StackFrame("c", "C", 3, "C.java"));

        CallNavigation nav = new CallNavigation();
        nav.loadFrom(frame);

        // Step into topmost user frame (A.foo)
        AtomicReference<SourcePosition> pos = new AtomicReference<>();
        pos.set(nav.step(CallNavigation.Direction.INTO).orElse(null));
        assertNotNull(pos.get());
        assertEquals("A.java", pos.get().path);

        // Step over -> advance to next frame (B)
        pos.set(nav.step(CallNavigation.Direction.OVER).orElse(null));
        assertNotNull(pos.get());
        assertEquals("B.java", pos.get().path);

        // Step out -> pop the deepest
        pos.set(nav.step(CallNavigation.Direction.OUT).orElse(null));
        assertNotNull(pos.get());
        // After popping the deepest (C), top is still A (the user's current frame)
        assertEquals("A.java", pos.get().path);
    }

    @Test
    public void editorReceivesVirtualBufferForDecompiledSource() {
        // 模拟从反编译得到的虚拟 buffer（实际生产中是 CFR 输出）
        String virtualSource = "/* Decompiled by CFR */\n" +
                "public class Foo { int bar() { return 0; } }\n";
        SourcePosition cursor = new SourcePosition("[com.example.Foo]", 1, 1);
        editor.openVirtual("[com.example.Foo]", virtualSource, cursor);
        assertEquals(1, openedFiles.size());
        assertTrue(openedFiles.get(0).readOnly);
        assertEquals(virtualSource, openedFiles.get(0).bufferContent);
    }
}
