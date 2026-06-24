/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Unit tests for the Phase E2 changes to the Breakpoint model.
 */

package com.zerostudio.debugger.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BreakpointTest {

    @Test
    public void legacyFourArgConstructorDefaultsHitCount() {
        Breakpoint bp = new Breakpoint(1L, "Foo.java", 42, "i > 0");
        assertEquals(1L, bp.id);
        assertEquals("Foo.java", bp.sourceFile);
        assertEquals(42, bp.line);
        assertEquals("i > 0", bp.condition);
        assertNull(bp.logMessage);
        assertEquals(Breakpoint.HitCountMode.ALWAYS, bp.hitCountMode);
        assertEquals(0, bp.hitCount);
        assertFalse(bp.hasHitCountFilter());
        assertTrue(bp.isConditional());
        assertFalse(bp.isLogpoint());
    }

    @Test
    public void legacyFiveArgConstructorDefaultsHitCount() {
        Breakpoint bp = new Breakpoint(1L, "Foo.java", 42, "i > 0", "x=");
        assertEquals(Breakpoint.HitCountMode.ALWAYS, bp.hitCountMode);
        assertEquals(0, bp.hitCount);
        assertTrue(bp.isLogpoint());
        assertTrue(bp.isConditional());
    }

    @Test
    public void newConstructorCarriesHitCount() {
        Breakpoint bp = new Breakpoint(
                7L, "Foo.java", 1, "x != null", null,
                Breakpoint.HitCountMode.EQUAL, 5);
        assertEquals(7L, bp.id);
        assertEquals(Breakpoint.HitCountMode.EQUAL, bp.hitCountMode);
        assertEquals(5, bp.hitCount);
        assertTrue(bp.hasHitCountFilter());
        assertTrue(bp.isConditional());
        assertFalse(bp.isLogpoint());
    }

    @Test
    public void nullModeFallsBackToAlways() {
        Breakpoint bp = new Breakpoint(
                1L, "Foo.java", 1, null, null, null, 0);
        assertEquals(Breakpoint.HitCountMode.ALWAYS, bp.hitCountMode);
        assertFalse(bp.hasHitCountFilter());
    }

    @Test
    public void hitCountFilterFalseWhenCountZero() {
        Breakpoint bp = new Breakpoint(
                1L, "Foo.java", 1, null, null,
                Breakpoint.HitCountMode.MULTIPLE, 0);
        assertFalse(bp.hasHitCountFilter());
    }

    @Test
    public void hitCountFilterFalseWhenModeAlways() {
        Breakpoint bp = new Breakpoint(
                1L, "Foo.java", 1, null, null,
                Breakpoint.HitCountMode.ALWAYS, 100);
        assertFalse(bp.hasHitCountFilter());
    }

    @Test
    public void toStringIncludesHitCountSummary() {
        Breakpoint bp = new Breakpoint(
                1L, "Foo.java", 1, null, null,
                Breakpoint.HitCountMode.MULTIPLE, 10);
        String s = bp.toString();
        assertNotNull(s);
        assertTrue(s.contains("hitCount=MULTIPLE:10"));
    }
}
