package com.zerostudio.language.runtime;

import com.zerostudio.language.runtime.CallStackViewModel.Row;
import com.zerostudio.language.runtime.FrameSnapshot.StackFrame;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class CallStackViewModelTest {
    @Test
    public void rowsHighlightTop() {
        CallStackViewModel vm = new CallStackViewModel();
        FrameSnapshot s = new FrameSnapshot();
        s.addFrame(new StackFrame("foo", "A", 1, "A.java"));
        s.addFrame(new StackFrame("bar", "B", 2, "B.java"));
        s.addFrame(new StackFrame("baz", "C", 3, "C.java"));
        vm.loadFrom(s);
        List<Row> rows = vm.rows();
        assertEquals(3, rows.size());
        assertTrue(rows.get(0).isTop);
        assertEquals(0, vm.highlightedIndex());
    }

    @Test
    public void setHighlightedChangesIndex() {
        CallStackViewModel vm = new CallStackViewModel();
        FrameSnapshot s = new FrameSnapshot();
        s.addFrame(new StackFrame("a", "A", 1, "A.java"));
        s.addFrame(new StackFrame("b", "B", 2, "B.java"));
        vm.loadFrom(s);
        vm.setHighlighted(1);
        assertEquals(1, vm.highlightedIndex());
        assertEquals("B.b:2", vm.highlightedRow().displayName);
    }
}
