package com.zerostudio.language.service;

import org.junit.Test;
import static org.junit.Assert.*;

public class FormatterServiceTest {

    @Test
    public void formatsIndentation() {
        FormatterService fmt = new FormatterService();
        String src = "class A {\n" +
                "void m() {\n" +
                "int x=1;\n" +
                "}\n" +
                "}\n";
        String out = fmt.format(src);
        String[] lines = out.split("\n");
        assertTrue(lines[0].startsWith("class A {"));
        assertTrue("line1 should be indented 4 spaces, got: '" + lines[1] + "'",
                lines[1].startsWith("    void m()"));
        assertTrue("line2 should be indented 8 spaces, got: '" + lines[2] + "'",
                lines[2].startsWith("        int x = 1;"));
    }

    @Test
    public void spacesAroundOperators() {
        FormatterService fmt = new FormatterService();
        String out = fmt.format("int x=1+2;\n");
        assertTrue(out.contains("x = 1 + 2"));
    }

    @Test
    public void spacesAroundEqualityAndLogical() {
        FormatterService fmt = new FormatterService();
        String out = fmt.format("if(a==b&&c!=d){}\n");
        assertTrue(out.contains("a == b"));
        assertTrue(out.contains("c != d"));
    }

    @Test
    public void spacesAfterKeyword() {
        FormatterService fmt = new FormatterService();
        String out = fmt.format("if(true){}\n");
        assertTrue(out.contains("if ("));
    }

    @Test
    public void spacesAfterComma() {
        FormatterService fmt = new FormatterService();
        String out = fmt.format("foo(a,b,c);\n");
        assertTrue(out.contains("a, b, c"));
    }

    @Test
    public void trimTrailingWhitespace() {
        FormatterService fmt = new FormatterService();
        String out = fmt.format("int x = 1;   \n");
        assertFalse(out.contains("   \n"));
    }

    @Test
    public void collapsesBlankLines() {
        FormatterService fmt = new FormatterService();
        String out = fmt.format("int x = 1;\n\n\n\nint y = 2;\n");
        assertFalse(out.contains("\n\n\n"));
    }

    @Test
    public void emptyInputReturnsEmpty() {
        FormatterService fmt = new FormatterService();
        assertEquals("", fmt.format(""));
        assertEquals("", fmt.format(null));
    }

    @Test
    public void preservesComments() {
        FormatterService fmt = new FormatterService();
        String out = fmt.format("int x=1; // comment\n");
        assertTrue(out.contains("// comment"));
    }
}
