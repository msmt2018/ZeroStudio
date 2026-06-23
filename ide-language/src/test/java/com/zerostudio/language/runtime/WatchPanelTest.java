package com.zerostudio.language.runtime;

import com.zerostudio.language.eval.WatchPanel;
import com.zerostudio.language.eval.WatchPanel.WatchEntry;
import com.zerostudio.language.runtime.FrameSnapshot.Value;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class WatchPanelTest {
    @Test
    public void evaluatesSimpleExpression() {
        WatchPanel w = new WatchPanel();
        w.addWatch("a + b");
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("a", "int", "Local", 3L));
        f.addValue(new Value("b", "int", "Local", 4L));
        w.evaluate(f);
        List<WatchEntry> all = w.watches();
        assertEquals(1, all.size());
        assertEquals("7", all.get(0).displayValue);
    }

    @Test
    public void showsNullAsNull() {
        WatchPanel w = new WatchPanel();
        w.addWatch("x");
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("x", "Object", "Field", null));
        w.evaluate(f);
        WatchEntry e = w.watches().get(0);
        assertTrue(e.isNull);
        assertEquals("null", e.displayValue);
    }

    @Test
    public void parseErrorCaptured() {
        WatchPanel w = new WatchPanel();
        w.addWatch("@#$");
        w.evaluate(new FrameSnapshot());
        WatchEntry e = w.watches().get(0);
        assertTrue(e.isError);
    }

    @Test
    public void listIndexWorks() {
        WatchPanel w = new WatchPanel();
        w.addWatch("arr[1]");
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("arr", "java.util.List", "Field", Arrays.asList("x", "y", "z")));
        w.evaluate(f);
        assertEquals("\"y\"", w.watches().get(0).displayValue);
    }
}
