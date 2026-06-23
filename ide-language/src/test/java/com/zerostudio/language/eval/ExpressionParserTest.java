package com.zerostudio.language.eval;

import com.zerostudio.language.eval.ExpressionParser.Node;
import com.zerostudio.language.runtime.FrameSnapshot;
import com.zerostudio.language.runtime.FrameSnapshot.Value;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExpressionParserTest {
    @Test
    public void parseNumber() {
        Node n = new ExpressionParser("42").parse();
        assertEquals(ExpressionParser.NodeKind.NUMBER, n.kind);
        assertEquals("42", n.value);
    }

    @Test
    public void parseAdd() {
        Node n = new ExpressionParser("1 + 2").parse();
        assertEquals(ExpressionParser.NodeKind.BINARY, n.kind);
        assertEquals("+", n.value);
    }

    @Test
    public void parseMethodCall() {
        Node n = new ExpressionParser("foo.bar(1, 2)").parse();
        assertEquals(ExpressionParser.NodeKind.CALL, n.kind);
    }

    @Test
    public void parseMemberAccess() {
        Node n = new ExpressionParser("a.b.c").parse();
        assertEquals(ExpressionParser.NodeKind.MEMBER, n.kind);
        assertEquals("c", n.value);
    }

    @Test
    public void parseIndex() {
        Node n = new ExpressionParser("arr[0]").parse();
        assertEquals(ExpressionParser.NodeKind.INDEX, n.kind);
    }

    @Test
    public void parseTernary() {
        Node n = new ExpressionParser("a > 0 ? 1 : 2").parse();
        assertEquals(ExpressionParser.NodeKind.BINARY, n.kind);
        assertEquals("?:", n.value);
    }

    @Test
    public void parseString() {
        Node n = new ExpressionParser("\"hello\"").parse();
        assertEquals(ExpressionParser.NodeKind.STRING, n.kind);
        assertEquals("hello", n.value);
    }
}
