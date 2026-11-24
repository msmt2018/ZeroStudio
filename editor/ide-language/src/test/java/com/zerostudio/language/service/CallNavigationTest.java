package com.zerostudio.language.service;

import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.StackFrame;
import org.junit.Test;
import static org.junit.Assert.*;

public class CallNavigationTest {
    @Test
    public void stepIntoReturnsTop() {
        FrameSnapshot s = new FrameSnapshot();
        s.addFrame(new StackFrame("foo", "A", 1, "A.java"));
        s.addFrame(new StackFrame("bar", "B", 2, "B.java"));
        CallNavigation nav = new CallNavigation();
        nav.loadFrom(s);
        var pos = nav.step(CallNavigation.Direction.INTO);
        assertTrue(pos.isPresent());
        assertEquals("A.java", pos.get().path);
    }

    @Test
    public void stepOverAdvances() {
        FrameSnapshot s = new FrameSnapshot();
        s.addFrame(new StackFrame("foo", "A", 1, "A.java"));
        s.addFrame(new StackFrame("bar", "B", 5, "B.java"));
        CallNavigation nav = new CallNavigation();
        nav.loadFrom(s);
        var pos = nav.step(CallNavigation.Direction.OVER);
        assertTrue(pos.isPresent());
    }

    @Test
    public void stepOutPopsFrame() {
        FrameSnapshot s = new FrameSnapshot();
        s.addFrame(new StackFrame("foo", "A", 1, "A.java"));
        s.addFrame(new StackFrame("bar", "B", 2, "B.java"));
        CallNavigation nav = new CallNavigation();
        nav.loadFrom(s);
        var pos = nav.step(CallNavigation.Direction.OUT);
        assertTrue(pos.isPresent());
        assertEquals("A.java", pos.get().path);
    }

    @Test
    public void filterSkipsSyntheticAndGetters() {
        FrameSnapshot s = new FrameSnapshot();
        s.addFrame(new StackFrame("access$100", "A", 1, "A.java"));
        s.addFrame(new StackFrame("realMethod", "A", 2, "A.java"));
        CallNavigation nav = new CallNavigation();
        nav.setFilter(CallNavigation.DEFAULT_FILTER);
        nav.loadFrom(s);
        var pos = nav.step(CallNavigation.Direction.INTO);
        assertTrue(pos.isPresent());
        assertEquals(2, pos.get().line);
    }
}
