package com.zerostudio.language.eval;

import com.zerostudio.language.eval.ExpressionParser.Node;
import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.Value;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class EvalEngineTest {
    private EvalEngine engine = new EvalEngine();

    @Test
    public void evalArithmetic() {
        FrameSnapshot f = new FrameSnapshot();
        Node n = new ExpressionParser("1 + 2 * 3").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals(7L, r.value);
    }

    @Test
    public void evalIdentifierFromFrame() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("x", "int", "Local", 42L));
        Node n = new ExpressionParser("x").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals(42L, r.value);
    }

    @Test
    public void nullSafety() {
        FrameSnapshot f = new FrameSnapshot();
        Node n = new ExpressionParser("null").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertTrue(r.isNull());
    }

    @Test
    public void nullComparisonYieldsTrue() {
        FrameSnapshot f = new FrameSnapshot();
        Node n = new ExpressionParser("null == null").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals(Boolean.TRUE, r.value);
    }

    @Test
    public void ternaryOperator() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("a", "int", "Local", 5L));
        Node n = new ExpressionParser("a > 0 ? 1 : 2").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals(1L, r.value);
    }

    @Test
    public void memberAccessOnList() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("arr", "java.util.List", "Field", Arrays.asList(10L, 20L, 30L)));
        Node n = new ExpressionParser("arr[1]").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals(20L, r.value);
    }

    @Test
    public void logicalAndShortCircuit() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("x", "boolean", "Field", Boolean.TRUE));
        Node n = new ExpressionParser("x && (1 == 2)").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals(Boolean.FALSE, r.value);
    }

    @Test
    public void divisionByZeroProducesError() {
        FrameSnapshot f = new FrameSnapshot();
        Node n = new ExpressionParser("1 / 0").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertTrue("should be error: " + r.error, r.isError());
    }

    @Test
    public void elvisOperatorFallsBackOnNull() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("maybeNull", "String", "Local", null));
        Node n = new ExpressionParser("maybeNull ?: \"default\"").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals("default", r.value);
    }

    @Test
    public void elvisOperatorReturnsValueWhenNotNull() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("notNull", "String", "Local", "hello"));
        Node n = new ExpressionParser("notNull ?: \"default\"").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals("hello", r.value);
    }

    @Test
    public void ternaryNested() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("a", "int", "Local", 5L));
        Node n = new ExpressionParser("a > 10 ? \"big\" : a > 0 ? \"small\" : \"zero\"").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals("small", r.value);
    }

    @Test
    public void stringConcatenation() {
        FrameSnapshot f = new FrameSnapshot();
        f.addValue(new Value("name", "String", "Local", "Alice"));
        Node n = new ExpressionParser("\"Hello, \" + name + \"!\"").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals("Hello, Alice!", r.value);
    }

    @Test
    public void moduloOperator() {
        FrameSnapshot f = new FrameSnapshot();
        Node n = new ExpressionParser("10 % 3").parse();
        EvalEngine.Result r = engine.evaluate(n, f);
        assertEquals(1L, r.value);
    }
}
