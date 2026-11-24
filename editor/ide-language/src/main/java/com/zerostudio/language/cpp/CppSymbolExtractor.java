package com.zerostudio.language.cpp;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.source.SourceLocator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C/C++ 符号解析器：基于正则模式的轻量级实现（无 tree-sitter 依赖）。
 * 支持：
 *  - #include "..." / &lt;...&gt; 头文件
 *  - class / struct / union 声明
 *  - 函数定义（返回类型 + 名称 + 参数）
 *  - 命名空间 namespace foo { ... }
 *  - using namespace / typedef
 *
 * 完整 tree-sitter 实现参见 editor/treesitter 模块（K14 JNI 集成后切换）。
 */
public final class CppSymbolExtractor {

    private static final Pattern INCLUDE = Pattern.compile(
            "^\\s*#include\\s+([<\"])([^>\"]+)[>\"]");
    private static final Pattern CLASS = Pattern.compile(
            "^\\s*(?:template\\s*<[^>]*>\\s*)?(?:class|struct|union)\\s+([A-Za-z_]\\w*)");
    private static final Pattern NAMESPACE = Pattern.compile(
            "^\\s*namespace\\s+([A-Za-z_]\\w*)");
    private static final Pattern TYPEDEF = Pattern.compile(
            "^\\s*typedef\\s+(?:[A-Za-z_][\\w\\s*]+)\\s+([A-Za-z_]\\w*)");
    private static final Pattern FUNCTION = Pattern.compile(
            "^\\s*(?:[A-Za-z_][\\w\\s*&:<>,]*?)\\s+([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern USING_NS = Pattern.compile(
            "^\\s*using\\s+namespace\\s+([A-Za-z_]\\w*(?:\\s*::\\s*[A-Za-z_]\\w*)*)");

    public ParsedFile extract(String path, String text) {
        List<Reference> refs = new ArrayList<>();
        String currentNamespace = "";
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int col = 1;

            Matcher mInc = INCLUDE.matcher(line);
            if (mInc.find()) {
                String header = mInc.group(2);
                refs.add(new Reference(header,
                        new SourceRange(new SourcePosition(path, i + 1, col),
                                new SourcePosition(path, i + 1, line.length())),
                        Reference.ReferenceKind.IMPORT, "", path, LanguageId.CPP));
                continue;
            }
            Matcher mNs = NAMESPACE.matcher(line);
            if (mNs.find()) {
                String ns = mNs.group(1);
                currentNamespace = currentNamespace.isEmpty() ? ns : currentNamespace + "::" + ns;
                refs.add(new Reference(ns,
                        new SourceRange(new SourcePosition(path, i + 1, mNs.start(1) + 1),
                                new SourcePosition(path, i + 1, mNs.end(1))),
                        Reference.ReferenceKind.TYPE, "", path, LanguageId.CPP));
                continue;
            }
            Matcher mCls = CLASS.matcher(line);
            if (mCls.find()) {
                String name = mCls.group(1);
                String fqn = currentNamespace.isEmpty() ? name : currentNamespace + "::" + name;
                refs.add(new Reference(fqn,
                        new SourceRange(new SourcePosition(path, i + 1, mCls.start(1) + 1),
                                new SourcePosition(path, i + 1, mCls.end(1))),
                        Reference.ReferenceKind.CLASS, currentNamespace, path, LanguageId.CPP));
                continue;
            }
            Matcher mTd = TYPEDEF.matcher(line);
            if (mTd.find()) {
                String name = mTd.group(1);
                refs.add(new Reference(name,
                        new SourceRange(new SourcePosition(path, i + 1, mTd.start(1) + 1),
                                new SourcePosition(path, i + 1, mTd.end(1))),
                        Reference.ReferenceKind.TYPE, currentNamespace, path, LanguageId.CPP));
                continue;
            }
            Matcher mUn = USING_NS.matcher(line);
            if (mUn.find()) {
                String ns = mUn.group(1);
                refs.add(new Reference(ns,
                        new SourceRange(new SourcePosition(path, i + 1, mUn.start(1) + 1),
                                new SourcePosition(path, i + 1, mUn.end(1))),
                        Reference.ReferenceKind.IMPORT, "", path, LanguageId.CPP));
                continue;
            }
            // 函数定义：跳过宏、控制流语句、注释行
            if (line.contains("//") || line.trim().startsWith("#")
                    || line.trim().startsWith("return")
                    || line.trim().startsWith("if")
                    || line.trim().startsWith("for")
                    || line.trim().startsWith("while")) {
                continue;
            }
            Matcher mFn = FUNCTION.matcher(line);
            if (mFn.find()) {
                String name = mFn.group(1);
                if (isKeyword(name)) continue;
                refs.add(new Reference(name,
                        new SourceRange(new SourcePosition(path, i + 1, mFn.start(1) + 1),
                                new SourcePosition(path, i + 1, mFn.end(1))),
                        Reference.ReferenceKind.METHOD, currentNamespace, path, LanguageId.CPP));
            }
        }
        return new ParsedFile(path, LanguageId.CPP, currentNamespace, refs, text);
    }

    public Optional<com.zerostudio.language.model.ResolutionResult> resolve(
            ParsedFile pf, Reference ref, SourceLocator locator) {
        if (locator == null) return Optional.empty();
        SourceLocator.LocatedSource src = locator.locate(pf.packageName + "::" + ref.name);
        if (src.isResolved()) {
            return Optional.of(com.zerostudio.language.model.ResolutionResult.resolved(
                    src.displayPath, null,
                    new com.zerostudio.language.model.Symbol(ref.name,
                            pf.packageName + "::" + ref.name,
                            com.zerostudio.language.model.SymbolKind.CLASS,
                            pf.packageName, src.displayPath)));
        }
        return Optional.empty();
    }

    private static boolean isKeyword(String s) {
        switch (s) {
            case "if": case "else": case "for": case "while":
            case "return": case "switch": case "case": case "do":
            case "break": case "continue": case "goto":
            case "sizeof": case "typeof": case "alignof":
                return true;
            default: return false;
        }
    }
}
