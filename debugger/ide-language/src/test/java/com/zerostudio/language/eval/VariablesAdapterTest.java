package com.zerostudio.language.eval;

import com.zerostudio.language.runtime.FrameSnapshot;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class VariablesAdapterTest {

    @Test
    public void allRowsIncludeAllKinds() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new FrameSnapshot.Value("x", "int", "Local", 42));
        f.addValue(new FrameSnapshot.Value("name", "String", "Field", "Alice"));
        f.addValue(new FrameSnapshot.Value("PI", "double", "Static", 3.14));
        VariablesAdapter a = new VariablesAdapter();
        List<VariablesAdapter.Row> rows = a.toRows(f);
        assertEquals(3, rows.size());
    }

    @Test
    public void localsEditableFieldsAndStaticsNot() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new FrameSnapshot.Value("x", "int", "Local", 1));
        f.addValue(new FrameSnapshot.Value("y", "int", "Field", 2));
        f.addValue(new FrameSnapshot.Value("z", "int", "Static", 3));
        VariablesAdapter a = new VariablesAdapter();
        List<VariablesAdapter.Row> locals = a.locals(f);
        assertEquals(1, locals.size());
        assertTrue(locals.get(0).editable);
    }

    @Test
    public void nullValueDisplaysAsNull() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new FrameSnapshot.Value("o", "Object", "Field", null));
        VariablesAdapter a = new VariablesAdapter();
        List<VariablesAdapter.Row> rows = a.toRows(f);
        assertEquals("null", rows.get(0).value);
    }

    @Test
    public void stringValueQuoted() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new FrameSnapshot.Value("s", "String", "Local", "hello"));
        VariablesAdapter a = new VariablesAdapter();
        assertEquals("\"hello\"", a.toRows(f).get(0).value);
    }

    @Test
    public void emptyFrameReturnsEmpty() {
        assertTrue(new VariablesAdapter().toRows(new FrameSnapshot()).isEmpty());
        assertTrue(new VariablesAdapter().toRows(null).isEmpty());
    }
}
