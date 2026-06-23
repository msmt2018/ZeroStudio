package com.zerostudio.language.breakpoint;

import com.zerostudio.language.breakpoint.Breakpoint.HitResult;
import com.zerostudio.language.eval.EvalEngine;
import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.Value;
import org.junit.Test;
import static org.junit.Assert.*;

public class BreakpointTest {
    @Test
    public void lineBreakpointAlwaysStops() {
        Breakpoint bp = Breakpoint.builder()
                .id("b1").sourceFile("F.java").line(10)
                .kind(Breakpoint.Kind.LINE).build();
        HitResult r = bp.onHit(new FrameSnapshot());
        assertTrue(r.stop);
    }

    @Test
    public void conditionalBreaksOnTrue() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("x", "int", "Local", 5L));
        Breakpoint bp = Breakpoint.builder()
                .id("b2").sourceFile("F.java").line(10)
                .condition("x > 0").build();
        assertTrue(bp.onHit(f).stop);
    }

    @Test
    public void conditionalSkipsOnFalse() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("x", "int", "Local", -1L));
        Breakpoint bp = Breakpoint.builder()
                .id("b3").sourceFile("F.java").line(10)
                .condition("x > 0").build();
        assertTrue(bp.onHit(f).skip);
    }

    @Test
    public void logpointExpandsExpression() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("name", "java.lang.String", "Local", "Alice"));
        Breakpoint bp = Breakpoint.builder()
                .id("b4").sourceFile("F.java").line(10)
                .logMessage("user = {name}").build();
        HitResult r = bp.onHit(f);
        assertEquals("user = Alice", r.log);
    }

    @Test
    public void hitThresholdSkipsEarly() {
        Breakpoint bp = Breakpoint.builder()
                .id("b5").sourceFile("F.java").line(10)
                .kind(Breakpoint.Kind.LINE)
                .hitCount(0).hitThreshold(3).build();
        assertTrue(bp.onHit(new FrameSnapshot()).skip);
    }

    @Test
    public void registryAddAndRetrieve() {
        Breakpoint.Registry reg = new Breakpoint.Registry();
        reg.add(Breakpoint.builder().id("a").sourceFile("A.java").line(1).build());
        reg.add(Breakpoint.builder().id("b").sourceFile("A.java").line(2).build());
        assertEquals(2, reg.forFile("A.java").size());
        reg.remove("a");
        assertEquals(1, reg.forFile("A.java").size());
    }
}
