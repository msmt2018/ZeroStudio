package com.zerostudio.language.python;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python 符号解析器：
 *  - import x / from x import y
 *  - class X / class X(Y, Z):
 *  - def foo(...) / async def foo(...)
 *  - @decorator
 *  - global / nonlocal
 *  - 顶层赋值 x = 1 (module-level variable)
 */
public final class PythonSymbolExtractor {

    private static final Pattern IMPORT_FROM = Pattern.compile(
            "^\\s*from\\s+([\\w.]+)\\s+import\\s+(.+)$");
    private static final Pattern IMPORT_SIMPLE = Pattern.compile(
            "^\\s*import\\s+([\\w.]+(?:\\s*,\\s*[\\w.]+)*)");
    private static final Pattern CLASS = Pattern.compile(
            "^\\s*class\\s+([A-Za-z_]\\w*)\\s*(?:\\(([^)]*)\\))?");
    private static final Pattern DEF = Pattern.compile(
            "^\\s*(?:async\\s+)?def\\s+([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern DECORATOR = Pattern.compile(
            "^\\s*@([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)");
    private static final Pattern ASSIGN = Pattern.compile(
            "^\\s*([A-Za-z_]\\w*)\\s*=");
    private static final Pattern GLOBAL = Pattern.compile(
            "^\\s*global\\s+([A-Za-z_]\\w*(?:\\s*,\\s*[A-Za-z_]\\w*)*)");

    public ParsedFile extract(String path, String text) {
        List<Reference> refs = new ArrayList<>();
        String[] lines = text.split("\n");
        String moduleName = deriveModuleName(path);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int col = 1;

            // from x import y
            Matcher mFrom = IMPORT_FROM.matcher(line);
            if (mFrom.find()) {
                String mod = mFrom.group(1);
                String names = mFrom.group(2);
                refs.add(new Reference(mod,
                        new SourceRange(new SourcePosition(path, i + 1, col),
                                new SourcePosition(path, i + 1, line.length())),
                        Reference.ReferenceKind.IMPORT, moduleName, path, LanguageId.PYTHON));
                for (String n : names.split(",")) {
                    String name = n.trim().split("\\s+as\\s+")[0].trim();
                    if (!name.isEmpty() && !name.equals("*")) {
                        refs.add(new Reference(name,
                                new SourceRange(new SourcePosition(path, i + 1, col),
                                        new SourcePosition(path, i + 1, line.length())),
                                Reference.ReferenceKind.IMPORT, moduleName, path, LanguageId.PYTHON));
                    }
                }
                continue;
            }
            // import x, y
            Matcher mImp = IMPORT_SIMPLE.matcher(line);
            if (mImp.find()) {
                refs.add(new Reference(mImp.group(1),
                        new SourceRange(new SourcePosition(path, i + 1, col),
                                new SourcePosition(path, i + 1, line.length())),
                        Reference.ReferenceKind.IMPORT, moduleName, path, LanguageId.PYTHON));
                continue;
            }
            // class
            Matcher mCls = CLASS.matcher(line);
            if (mCls.find()) {
                String name = mCls.group(1);
                refs.add(new Reference(name,
                        new SourceRange(new SourcePosition(path, i + 1, mCls.start(1) + 1),
                                new SourcePosition(path, i + 1, mCls.end(1))),
                        Reference.ReferenceKind.CLASS, moduleName, path, LanguageId.PYTHON));
                continue;
            }
            // def
            Matcher mDef = DEF.matcher(line);
            if (mDef.find()) {
                String name = mDef.group(1);
                refs.add(new Reference(name,
                        new SourceRange(new SourcePosition(path, i + 1, mDef.start(1) + 1),
                                new SourcePosition(path, i + 1, mDef.end(1))),
                        Reference.ReferenceKind.METHOD, moduleName, path, LanguageId.PYTHON));
                continue;
            }
            // @decorator
            Matcher mDec = DECORATOR.matcher(line);
            if (mDec.find()) {
                refs.add(new Reference(mDec.group(1),
                        new SourceRange(new SourcePosition(path, i + 1, mDec.start(1) + 1),
                                new SourcePosition(path, i + 1, mDec.end(1))),
                        Reference.ReferenceKind.TYPE, moduleName, path, LanguageId.PYTHON));
                continue;
            }
            // global x
            Matcher mG = GLOBAL.matcher(line);
            if (mG.find()) {
                refs.add(new Reference(mG.group(1),
                        new SourceRange(new SourcePosition(path, i + 1, col),
                                new SourcePosition(path, i + 1, line.length())),
                        Reference.ReferenceKind.VARIABLE, moduleName, path, LanguageId.PYTHON));
                continue;
            }
            // x = ...
            Matcher mA = ASSIGN.matcher(line);
            if (mA.find()) {
                String name = mA.group(1);
                // 跳过 self.x = ... 和 class member
                if (name.equals("self") || name.equals("cls")) continue;
                if (Character.isUpperCase(name.charAt(0))) continue; // 常量
                refs.add(new Reference(name,
                        new SourceRange(new SourcePosition(path, i + 1, mA.start(1) + 1),
                                new SourcePosition(path, i + 1, mA.end(1))),
                        Reference.ReferenceKind.VARIABLE, moduleName, path, LanguageId.PYTHON));
            }
        }
        return new ParsedFile(path, LanguageId.PYTHON, moduleName, refs, text);
    }

    private String deriveModuleName(String path) {
        if (path == null) return "";
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot >= 0) name = name.substring(0, dot);
        return name;
    }
}
