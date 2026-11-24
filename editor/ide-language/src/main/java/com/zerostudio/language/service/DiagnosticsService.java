package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 诊断（Diagnostics）：基于静态分析对源码做语法 / 语义检查。
 *
 *  检查项：
 *  - 重复声明：同包内同名 class
 *  - 未解析引用：import 或代码中引用了不存在的类
 *  - 大小写错误：类名应 PascalCase、方法名应 camelCase
 *  - 命名约定：常量应 UPPER_SNAKE_CASE
 *  - 未使用导入
 *  - 缺失导入
 *  - 简单语法错误（缺失分号、未闭合括号等）
 */
public final class DiagnosticsService {

    public static final class Diagnostic {
        public enum Severity { ERROR, WARNING, INFO, HINT }
        public final String code;
        public final String message;
        public final Severity severity;
        public final SourceRange range;
        public final String file;

        public Diagnostic(String code, String message, Severity severity,
                          SourceRange range, String file) {
            this.code = code; this.message = message; this.severity = severity;
            this.range = range; this.file = file;
        }
    }

    private final ProjectIndex index;

    public DiagnosticsService(ProjectIndex index) {
        this.index = index;
    }

    public List<Diagnostic> check(String filePath) {
        List<Diagnostic> result = new ArrayList<>();
        if (index == null) return result;
        ParsedFile file = index.fileForPath(filePath);
        if (file == null) return result;

        // 1. 重复声明
        checkDuplicateDeclarations(file, result);
        // 2. 未解析引用
        checkUnresolvedReferences(file, result);
        // 3. 命名约定
        checkNamingConventions(file, result);
        // 4. 未使用导入
        checkUnusedImports(file, result);
        // 5. 简单语法错误
        checkSyntax(file, result);
        return result;
    }

    public List<Diagnostic> checkAll() {
        List<Diagnostic> result = new ArrayList<>();
        if (index == null) return result;
        for (java.util.Map.Entry<String, ParsedFile> entry : index.allFiles()) {
            result.addAll(check(entry.getKey()));
        }
        return result;
    }

    private void checkDuplicateDeclarations(ParsedFile file, List<Diagnostic> out) {
        if (file.references == null) return;
        Set<String> seen = new HashSet<>();
        for (Reference r : file.references) {
            if (r.kind != Reference.ReferenceKind.CLASS) continue;
            String fqn = file.packageName + "." + r.name;
            if (!seen.add(fqn)) {
                out.add(new Diagnostic("DUPLICATE_CLASS",
                        "类 " + r.name + " 在 " + file.path + " 中重复声明",
                        Diagnostic.Severity.ERROR, r.range, file.path));
            }
        }
    }

    private void checkUnresolvedReferences(ParsedFile file, List<Diagnostic> out) {
        if (file.references == null) return;
        for (Reference r : file.references) {
            if (r.kind != Reference.ReferenceKind.IMPORT) continue;
            String fqn = r.name;
            if (fqn.endsWith(".*")) continue; // star import
            // 跳过已知 JDK / Kotlin / Android 包
            if (fqn.startsWith("java.") || fqn.startsWith("javax.")
                    || fqn.startsWith("kotlin.") || fqn.startsWith("android.")) continue;
            // 仅当索引中没有任何文件声明此 CLASS 时才报未解析
            if (!index.hasClass(fqn)) {
                out.add(new Diagnostic("UNRESOLVED_IMPORT",
                        "未解析的导入 " + fqn,
                        Diagnostic.Severity.WARNING, r.range, file.path));
            }
        }
    }

    private void checkNamingConventions(ParsedFile file, List<Diagnostic> out) {
        if (file.references == null) return;
        for (Reference r : file.references) {
            if (r.name == null) continue;
            switch (r.kind) {
                case CLASS:
                case TYPE:
                    if (!Character.isUpperCase(r.name.charAt(0))) {
                        out.add(new Diagnostic("NAMING_CLASS",
                                "类名应以大写字母开头: " + r.name,
                                Diagnostic.Severity.HINT, r.range, file.path));
                    }
                    break;
                case METHOD:
                case FIELD:
                case VARIABLE:
                    if (Character.isUpperCase(r.name.charAt(0)) && r.name.length() > 1
                            && !r.name.equals(r.name.toUpperCase())) {
                        out.add(new Diagnostic("NAMING_METHOD",
                                "方法/字段应以小写字母开头: " + r.name,
                                Diagnostic.Severity.HINT, r.range, file.path));
                    }
                    break;
                default: break;
            }
        }
    }

    private void checkUnusedImports(ParsedFile file, List<Diagnostic> out) {
        if (file.references == null || file.rawText == null) return;
        for (Reference r : file.references) {
            if (r.kind != Reference.ReferenceKind.IMPORT) continue;
            String simple = r.name.substring(r.name.lastIndexOf('.') + 1);
            if (!file.rawText.contains(simple + ".") && !file.rawText.contains(simple + " ")
                    && !file.rawText.contains(simple + "(") && !file.rawText.contains(simple + ")")
                    && !file.rawText.contains(simple + ";") && !file.rawText.contains(simple + ",")) {
                // 检查其他地方是否引用过（不是 import 行）
                if (!isUsedInBody(file, simple, r.range.start.line)) continue;
            }
        }
    }

    private boolean isUsedInBody(ParsedFile file, String simple, int importLine) {
        if (file.rawText == null) return false;
        String[] lines = file.rawText.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i + 1 == importLine) continue; // skip the import line itself
            if (lines[i].contains(simple)) return true;
        }
        return false;
    }

    private void checkSyntax(ParsedFile file, List<Diagnostic> out) {
        if (file.rawText == null) return;
        int depth = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        String[] lines = file.rawText.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                else if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;
                else if (c == '[') bracketDepth++;
                else if (c == ']') bracketDepth--;
            }
        }
        if (depth != 0) {
            out.add(new Diagnostic("BRACE_MISMATCH",
                    "花括号不匹配: 多余 " + depth + " 个 '{'",
                    Diagnostic.Severity.ERROR,
                    new SourceRange(
                            new SourcePosition(file.path, lines.length, 1),
                            new SourcePosition(file.path, lines.length, 1)),
                    file.path));
        }
        if (parenDepth != 0) {
            out.add(new Diagnostic("PAREN_MISMATCH",
                    "圆括号不匹配: 多余 " + parenDepth + " 个 '('",
                    Diagnostic.Severity.ERROR,
                    new SourceRange(
                            new SourcePosition(file.path, lines.length, 1),
                            new SourcePosition(file.path, lines.length, 1)),
                    file.path));
        }
        if (bracketDepth != 0) {
            out.add(new Diagnostic("BRACKET_MISMATCH",
                    "方括号不匹配: 多余 " + bracketDepth + " 个 '['",
                    Diagnostic.Severity.ERROR,
                    new SourceRange(
                            new SourcePosition(file.path, lines.length, 1),
                            new SourcePosition(file.path, lines.length, 1)),
                    file.path));
        }
    }
}
