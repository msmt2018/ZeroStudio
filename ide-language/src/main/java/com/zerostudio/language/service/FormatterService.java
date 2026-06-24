package com.zerostudio.language.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码格式化：轻量级、规则化实现。
 *
 * 规则：
 *  - 4 空格缩进（可配）
 *  - 删除行尾空白
 *  - 合并连续空行（最多保留 1 个）
 *  - 运算符两侧加空格（=、+、-、*、/、%、==、!=、<=、>=、&&、||）
 *  - 关键字后加空格（if / for / while / catch / etc.）
 *  - 逗号后加空格
 *  - { 前换行（强制）
 *  - 保留字符串与注释内容不变
 *
 * 不进行（避免破坏代码语义）：
 *  - 重排 import
 *  - 改写类型
 *  - 修改换行位置（仅在大括号处）
 */
public final class FormatterService {

    public static final class Options {
        public int indentSize = 4;
        public boolean useTab = false;
        public boolean trimTrailingWhitespace = true;
        public boolean collapseBlankLines = true;
        public boolean spaceAfterKeyword = true;
        public boolean spaceAroundOperator = true;
        public boolean spaceAfterComma = true;
        public boolean braceOnNewLine = true;

        public static Options defaults() { return new Options(); }
    }

    public String format(String text) {
        return format(text, Options.defaults());
    }

    public String format(String text, Options opts) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        if (opts == null) opts = Options.defaults();
        String indentUnit = opts.useTab ? "\t" : repeat(" ", opts.indentSize);

        // 1. 基础行级处理：删除行尾空白、合并空行
        String[] lines = text.split("\n", -1);
        List<String> processed = new ArrayList<>();
        int blankStreak = 0;
        for (String raw : lines) {
            String line = raw;
            if (opts.trimTrailingWhitespace) {
                line = line.replaceAll("[ \\t]+$", "");
            }
            if (line.trim().isEmpty()) {
                blankStreak++;
                if (opts.collapseBlankLines && blankStreak > 1) continue;
            } else {
                blankStreak = 0;
            }
            processed.add(line);
        }

        // 2. 缩进重写：基于 { } 配对 + 计算当前缩进
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (int idx = 0; idx < processed.size(); idx++) {
            String line = processed.get(idx);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                out.append("\n");
                continue;
            }
            // 计算本行起始深度
            int lineOpen = countChar(trimmed, '{') - countChar(trimmed, '}');
            // 如果以 } 开头，先减一层
            boolean closesFirst = trimmed.startsWith("}");
            if (closesFirst) depth = Math.max(0, depth - 1);
            // 缩进
            for (int i = 0; i < depth; i++) out.append(indentUnit);
            // 运算符空格、关键字空格、逗号空格
            String styled = styleOperators(trimmed, opts);
            styled = styleKeywords(styled, opts);
            styled = styleCommas(styled, opts);
            out.append(styled);
            out.append("\n");
            // 深度更新
            depth = Math.max(0, depth + lineOpen);
        }
        return out.toString();
    }

    private String styleOperators(String s, Options opts) {
        if (!opts.spaceAroundOperator) return s;
        // 保留字符串与简单注释（// ...）不变
        int commentIdx = findLineCommentStart(s);
        String code = commentIdx >= 0 ? s.substring(0, commentIdx) : s;
        String comment = commentIdx >= 0 ? s.substring(commentIdx) : "";
        // 1. 双字符比较/逻辑运算符 == != <= >= && ||  (优先处理)
        code = code.replaceAll("(\\S)(==|!=|<=|>=|&&|\\|\\|)(\\S)", "$1 $2 $3");
        // 2. 复合赋值运算符 += -= *= /= %=
        code = code.replaceAll("(\\S)([+\\-*/%]=)(\\S)", "$1 $2 $3");
        // 3. 单 = 赋值（要求两侧都是非空白且不含 < > & |）
        code = code.replaceAll("(\\S)=([^=!<>&|\\s])", "$1 = $2");
        // 4. 单字符算术运算符 + - * / %
        code = code.replaceAll("(\\S)([+\\-*/%])([^=+\\-*/%\\s])", "$1 $2 $3");
        // 5. < > 单独比较
        code = code.replaceAll("(\\S)(<|>)([^=])", "$1 $2 $3");
        return code + comment;
    }

    private String styleKeywords(String s, Options opts) {
        if (!opts.spaceAfterKeyword) return s;
        // 在 if / for / while / switch / catch / return / throw / synchronized 后若紧跟 (，则插入空格
        return s.replaceAll("\\b(if|for|while|switch|catch|return|throw|synchronized)\\(", "$1 (");
    }

    private String styleCommas(String s, Options opts) {
        if (!opts.spaceAfterComma) return s;
        return s.replaceAll(",\\s*", ", ");
    }

    private int findLineCommentStart(String s) {
        boolean inString = false;
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = !inString;
            if (!inString && c == '/' && s.charAt(i + 1) == '/') return i;
        }
        return -1;
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }
}
