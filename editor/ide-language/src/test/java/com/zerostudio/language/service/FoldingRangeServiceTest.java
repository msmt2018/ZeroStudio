package com.zerostudio.language.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class FoldingRangeServiceTest {

    @Test
    public void foldsBracesAcrossLines() {
        FoldingRangeService svc = new FoldingRangeService();
        String src = "public class A {\n" +
                "    public void m() {\n" +
                "        int x = 1;\n" +
                "    }\n" +
                "}\n";
        List<FoldingRangeService.FoldingRange> ranges = svc.computeFoldingRanges(src);
        // 至少 2 个块（class + method）
        assertTrue("expected >= 2, got " + ranges.size(), ranges.size() >= 2);
    }

    @Test
    public void foldsBlockComments() {
        FoldingRangeService svc = new FoldingRangeService();
        String src = "/* line1\n" +
                "   line2\n" +
                "   line3 */\n" +
                "class A {}\n";
        List<FoldingRangeService.FoldingRange> ranges = svc.computeFoldingRanges(src);
        // 至少 1 个注释折叠 + 0 个 {} 折叠（class A {} 是单行）
        long comments = ranges.stream()
                .filter(r -> r.kind == FoldingRangeService.Kind.COMMENT)
                .count();
        assertEquals(1, comments);
    }

    @Test
    public void emptyTextReturnsEmpty() {
        assertTrue(new FoldingRangeService().computeFoldingRanges("").isEmpty());
        assertTrue(new FoldingRangeService().computeFoldingRanges(null).isEmpty());
    }

    @Test
    public void nestedBlocks() {
        FoldingRangeService svc = new FoldingRangeService();
        String src = "class A {\n" +
                "    void m() {\n" +
                "        if (true) {\n" +
                "            int x = 1;\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        List<FoldingRangeService.FoldingRange> ranges = svc.computeFoldingRanges(src);
        // 4 个块：class, method, if
        assertTrue("expected >= 3, got " + ranges.size(), ranges.size() >= 3);
    }

    @Test
    public void foldingRangeHasLineInfo() {
        FoldingRangeService svc = new FoldingRangeService();
        String src = "class A {\n" +
                "    int x;\n" +
                "}\n";
        List<FoldingRangeService.FoldingRange> ranges = svc.computeFoldingRanges(src);
        for (FoldingRangeService.FoldingRange r : ranges) {
            assertTrue(r.startLine <= r.endLine);
            assertTrue(r.startLine >= 0);
        }
    }
}
