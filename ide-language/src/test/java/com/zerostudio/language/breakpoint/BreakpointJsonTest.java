package com.zerostudio.language.breakpoint;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class BreakpointJsonTest {

    @Test
    public void roundTripLineBreakpoint() {
        Breakpoint bp = Breakpoint.builder()
                .id("bp1").sourceFile("X.java").line(10)
                .kind(Breakpoint.Kind.LINE).enabled(true)
                .build();
        String json = BreakpointJson.serialize(Arrays.asList(bp));
        assertTrue(json.contains("\"id\":\"bp1\""));
        assertTrue(json.contains("\"kind\":\"LINE\""));
        List<Breakpoint> restored = BreakpointJson.deserialize(json);
        assertEquals(1, restored.size());
        assertEquals("bp1", restored.get(0).id);
        assertEquals("X.java", restored.get(0).sourceFile);
        assertEquals(10, restored.get(0).line);
        assertEquals(Breakpoint.Kind.LINE, restored.get(0).kind);
        assertTrue(restored.get(0).enabled);
    }

    @Test
    public void roundTripConditionalBreakpoint() {
        Breakpoint bp = Breakpoint.builder()
                .id("bp2").sourceFile("Y.java").line(20)
                .condition("x > 0").build();
        String json = BreakpointJson.serialize(Arrays.asList(bp));
        List<Breakpoint> restored = BreakpointJson.deserialize(json);
        assertEquals(1, restored.size());
        assertEquals(Breakpoint.Kind.CONDITIONAL, restored.get(0).kind);
        assertEquals("x > 0", restored.get(0).condition);
    }

    @Test
    public void roundTripLogpoint() {
        Breakpoint bp = Breakpoint.builder()
                .id("bp3").sourceFile("Z.java").line(5)
                .logMessage("hello {name}").build();
        String json = BreakpointJson.serialize(Arrays.asList(bp));
        List<Breakpoint> restored = BreakpointJson.deserialize(json);
        assertEquals(Breakpoint.Kind.LOGPOINT, restored.get(0).kind);
        assertEquals("hello {name}", restored.get(0).logMessage);
    }

    @Test
    public void roundTripExceptionBreakpoint() {
        Breakpoint bp = Breakpoint.builder()
                .id("bp4").sourceFile("W.java").line(99)
                .exceptionType("java.lang.NullPointerException").build();
        String json = BreakpointJson.serialize(Arrays.asList(bp));
        List<Breakpoint> restored = BreakpointJson.deserialize(json);
        assertEquals(Breakpoint.Kind.EXCEPTION, restored.get(0).kind);
        assertEquals("java.lang.NullPointerException", restored.get(0).exceptionType);
    }

    @Test
    public void emptyInputProducesEmptyList() {
        assertTrue(BreakpointJson.deserialize("").isEmpty());
        assertTrue(BreakpointJson.deserialize(null).isEmpty());
    }

    @Test
    public void serializeMultipleBreakpoints() {
        Breakpoint bp1 = Breakpoint.builder().id("a").sourceFile("A.java").line(1).build();
        Breakpoint bp2 = Breakpoint.builder().id("b").sourceFile("B.java").line(2).build();
        String json = BreakpointJson.serialize(Arrays.asList(bp1, bp2));
        assertTrue(json.contains("\"id\":\"a\""));
        assertTrue(json.contains("\"id\":\"b\""));
        List<Breakpoint> restored = BreakpointJson.deserialize(json);
        assertEquals(2, restored.size());
    }
}
