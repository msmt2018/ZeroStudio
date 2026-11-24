package com.zerostudio.language.source;

import org.junit.*;
import java.io.*;
import static org.junit.Assert.*;

public class SmapParserTest {
    @Test
    public void parsesBasicSmap() throws Exception {
        String smap = "SMAP\n" +
                "Main.kt\n" +
                "Kotlin\n" +
                "*S Kotlin\n" +
                "*F\n" +
                "+ 1 Main.kt\n" +
                "1 Main.kt\n" +
                "*L\n" +
                "1#1,5:1\n" +
                "6#1,3:6\n" +
                "*E\n";
        SmapParser.ParsedSmap s = new SmapParser().parse(new ByteArrayInputStream(smap.getBytes()));
        assertEquals("Main.kt", s.defaultFile);
        assertEquals(1, s.fileSection.get(1).equals("Main.kt") ? 1 : 0);
        assertEquals(1, s.inputLineForOutputLine(1));
        assertEquals(5, s.inputLineForOutputLine(5));
        assertEquals(6, s.inputLineForOutputLine(6));
    }

    @Test
    public void handlesEmptyMapping() throws Exception {
        SmapParser.ParsedSmap s = new SmapParser().parse(
                new ByteArrayInputStream("SMAP\nF.kt\nKotlin\n*E\n".getBytes()));
        assertEquals("F.kt", s.defaultFile);
    }
}
