package com.zerostudio.language.kotlin;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.source.SourceLocator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Kotlin 符号解析：使用 JavaParser 作为底层 lexer 提取 token，
 * 并基于关键字模式识别 class / object / fun / val / var 声明与 import。
 * 完整 K2/K3 Kotlin 编译器前端将作为后续阶段加入。
 */
public final class KotlinSymbolExtractor {

    private final JavaParserFacade fallback = new JavaParserFacade();

    public ParsedFile extract(String path, String text) {
        // 简化：使用 JavaParser 的 token 流作为 Kotlin 词法后备
        // 真实生产中应使用 Kotlin Embeddable Compiler 或 kotlin-compile-embeddable
        List<Reference> refs = new ArrayList<>();
        String packageName = "";
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            // strip line comments (// ...), preserving strings would need a real lexer
            String line = stripLineComment(raw).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("package ")) {
                packageName = line.substring("package ".length()).trim();
                refs.add(new Reference(packageName,
                        new SourceRange(new SourcePosition(path, i + 1, 1),
                                new SourcePosition(path, i + 1, line.length())),
                        Reference.ReferenceKind.IMPORT, packageName, path, LanguageId.KOTLIN));
                continue;
            }
            if (line.startsWith("import ")) {
                String imp = line.substring("import ".length()).trim();
                refs.add(new Reference(imp,
                        new SourceRange(new SourcePosition(path, i + 1, 1),
                                new SourcePosition(path, i + 1, line.length())),
                        Reference.ReferenceKind.IMPORT, packageName, path, LanguageId.KOTLIN));
                continue;
            }
            // class / object / interface / enum
            int clsIdx = indexOfAny(line, "class ", "object ", "interface ", "enum class ");
            if (clsIdx >= 0) {
                // skip the keyword (and any "enum " prefix for "enum class")
                String kw = matchingKeyword(line, clsIdx, "class ", "object ", "interface ", "enum class ");
                int nameStart = clsIdx + kw.length();
                while (nameStart < line.length() && line.charAt(nameStart) == ' ') nameStart++;
                String name = readIdent(line, nameStart);
                if (!name.isEmpty()) {
                    refs.add(new Reference(name,
                            new SourceRange(new SourcePosition(path, i + 1, nameStart),
                                    new SourcePosition(path, i + 1, nameStart + name.length())),
                            Reference.ReferenceKind.CLASS, packageName, path, LanguageId.KOTLIN));
                }
            }
            // fun
            int funIdx = line.indexOf("fun ");
            if (funIdx >= 0) {
                int nameStart = funIdx + 4;
                while (nameStart < line.length() && line.charAt(nameStart) == ' ') nameStart++;
                String name = readIdent(line, nameStart);
                if (!name.isEmpty()) {
                    refs.add(new Reference(name,
                            new SourceRange(new SourcePosition(path, i + 1, nameStart),
                                    new SourcePosition(path, i + 1, nameStart + name.length())),
                            Reference.ReferenceKind.METHOD, packageName, path, LanguageId.KOTLIN));
                }
            }
        }
        return new ParsedFile(path, LanguageId.KOTLIN, packageName, refs, text);
    }

    public Optional<com.zerostudio.language.model.ResolutionResult> resolve(
            ParsedFile pf, Reference ref, SourceLocator locator) {
        if (locator == null) return Optional.empty();
        SourceLocator.LocatedSource src = locator.locate(pf.packageName + "." + ref.name);
        if (src.isResolved()) {
            return Optional.of(com.zerostudio.language.model.ResolutionResult.resolved(
                    src.displayPath, null,
                    new com.zerostudio.language.model.Symbol(ref.name,
                            pf.packageName + "." + ref.name,
                            com.zerostudio.language.model.SymbolKind.CLASS,
                            pf.packageName, src.displayPath)));
        }
        return Optional.empty();
    }

    private static String stripLineComment(String s) {
        // 简单处理：不在字符串内的 // 视为行注释起点
        boolean inString = false;
        char quote = 0;
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < s.length()) { i++; continue; }
                if (c == quote) inString = false;
            } else {
                if (c == '"' || c == '\'') { inString = true; quote = c; }
                else if (c == '/' && s.charAt(i + 1) == '/') return s.substring(0, i);
            }
        }
        return s;
    }

    private static int indexOfAny(String line, String... tokens) {
        int best = -1;
        for (String t : tokens) {
            int idx = line.indexOf(t);
            if (idx >= 0 && (best < 0 || idx < best)) best = idx;
        }
        return best;
    }
    private static String matchingKeyword(String line, int idx, String... tokens) {
        for (String t : tokens) {
            if (idx + t.length() <= line.length() && line.startsWith(t, idx)) return t;
        }
        return tokens[0];
    }
    private static String readIdent(String line, int start) {
        int end = start;
        while (end < line.length()) {
            char c = line.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') end++;
            else break;
        }
        return line.substring(start, end);
    }
}
