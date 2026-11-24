package com.zerostudio.language.runtime;

import com.zerostudio.language.runtime.FrameSnapshot.StackFrame;
import com.zerostudio.language.runtime.FrameSnapshot.Value;
import org.junit.Test;
import static org.junit.Assert.*;

public class FrameSnapshotTest {
    @Test
    public void capturesValues() {
        FrameSnapshot s = new FrameSnapshot();
        s.addValue(new Value("x", "int", "Local", 42L));
        s.addValue(new Value("y", "java.lang.String", "Field", "hi"));
        assertEquals(42L, s.getValue("x").value);
        assertEquals("hi", s.getValue("y").value);
    }

    @Test
    public void frozenAfterSet() {
        FrameSnapshot s = new FrameSnapshot();
        assertFalse(s.isFrozen());
        s.setFrozen(true);
        assertTrue(s.isFrozen());
    }

    @Test
    public void topFrame() {
        FrameSnapshot s = new FrameSnapshot();
        s.addFrame(new StackFrame("foo", "C1", 1, "C1.java"));
        s.addFrame(new StackFrame("bar", "C2", 2, "C2.java"));
        assertEquals("C1.foo:1", s.topFrame().display());
        assertEquals(2, s.frames().size());
    }

    @Test
    public void valueDisplayString() {
        FrameSnapshot s = new FrameSnapshot();
        s.addValue(new Value("name", "java.lang.String", "Field", "alice"));
        assertEquals("\"alice\"", s.getValue("name").displayValue());
        s.addValue(new Value("n", "int", "Local", 5L));
        assertEquals("5", s.getValue("n").displayValue());
    }

    @Test
    public void nullValueDisplay() {
        FrameSnapshot s = new FrameSnapshot();
        s.addValue(new Value("x", "Object", "Field", null));
        assertTrue(s.getValue("x").isNull);
        assertEquals("null", s.getValue("x").displayValue());
    }
}
