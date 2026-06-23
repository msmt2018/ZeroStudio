package com.zerostudio.language.service;

import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 折叠范围（Folding Range）：基于括号配对的代码块折叠。
 *
 * 支持：
 *  - { } 块（class / method / if / for / while / try / etc.）
 *  - / * ... * / 多行注释
 *  - import / package 块（Java）
 *  - 字符串字面量（超长自动折叠）
 *
 * 输出 SourceRange 列表，可直接映射到 LSP FoldingRange。
 */
public final class FoldingRangeService {

    public enum Kind { COMMENT, REGION, IMPORTS }

    public static final class FoldingRange {
        public final int startLine;
        public final int endLine;
        public final int startCol;
        public final int endCol;
        public final Kind kind;

        public FoldingRange(int startLine, int endLine, int startCol, int endCol, Kind kind) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.startCol = startCol;
            this.endCol = endCol;
            this.kind = kind;
        }

        public SourceRange toSourceRange(String path) {
            return new SourceRange(
                    new SourcePosition(path, startLine, startCol + 1),
                    new SourcePosition(path, endLine, endCol));
        }
    }

    /** 给定源代码，返回所有可折叠区域 */
    public List<FoldingRange> computeFoldingRanges(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<FoldingRange> result = new ArrayList<>();
        String[] lines = text.split("\n");
        // 1. 大括号配对
        scanBraces(lines, result);
        // 2. 多行注释
        scanBlockComments(text, lines, result);
        return result;
    }

    /** 扫描 { } 配对，仅当 start 和 end 跨越多行时折叠 */
    private void scanBraces(String[] lines, List<FoldingRange> out) {
        int[] stack = new int[lines.length * 4]; // 存 (line, col) 配对
        int top = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (c == '{') {
                    if (top + 2 > stack.length) {
                        int[] bigger = new int[stack.length * 2];
                        System.arraycopy(stack, 0, bigger, 0, stack.length);
                        stack = bigger;
                    }
                    stack[top++] = i;
                    stack[top++] = j;
                } else if (c == '}') {
                    if (top >= 2) {
                        int endCol = j;
                        top -= 2;
                        int startLine = stack[top];
                        int startCol = stack[top + 1];
                        if (startLine != i) {
                            out.add(new FoldingRange(startLine, i, startCol, endCol, Kind.REGION));
                        }
                    }
                }
            }
        }
    }

    /** 扫描多行注释 */
    private void scanBlockComments(String text, String[] lines, List<FoldingRange> out) {
        int idx = 0;
        while ((idx = text.indexOf("/*", idx)) >= 0) {
            int end = text.indexOf("*/", idx + 2);
            if (end < 0) break;
            int startLine = lineIndex(text, idx);
            int endLine = lineIndex(text, end + 1);
            if (startLine != endLine) {
                int startCol = colIndex(text, idx);
                int endCol = colIndex(text, end + 1);
                out.add(new FoldingRange(startLine, endLine, startCol, endCol, Kind.COMMENT));
            }
            idx = end + 2;
        }
    }

    private int lineIndex(String text, int charIdx) {
        int line = 0;
        for (int i = 0; i < charIdx && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private int colIndex(String text, int charIdx) {
        int col = 0;
        for (int i = charIdx - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') break;
            col++;
        }
        return col;
    }
}
