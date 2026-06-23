package com.zerostudio.language.javascript;

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
 * JavaScript / TypeScript 符号解析器：
 *  - import ... from '...'
 *  - import ... = require('...')   (CommonJS)
 *  - export class / function / const / let / var
 *  - class X / class X extends Y / class X implements Z
 *  - function name() / async function / function* generator
 *  - interface / type alias  (TS)
 *  - enum Foo { ... }           (TS)
 *  - method shorthand:   foo() {}
 *  - arrow const: const fn = () => {}
 *  - 顶层 var / let / const
 */
public final class JsSymbolExtractor {

    private static final Pattern IMPORT_FROM = Pattern.compile(
            "^\\s*import\\s+(?:type\\s+)?(?:\\{([^}]+)\\}|([A-Za-z_$][\\w$]*)|\\*\\s+as\\s+([A-Za-z_$][\\w$]*))\\s+from\\s+['\"]([^'\"]+)['\"]");
    private static final Pattern IMPORT_SIDE = Pattern.compile(
            "^\\s*import\\s+['\"]([^'\"]+)['\"]");
    private static final Pattern REQUIRE = Pattern.compile(
            "^\\s*(?:const|let|var)\\s+(?:\\{([^}]+)\\}|([A-Za-z_$][\\w$]*))\\s*=\\s*require\\s*\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern CLASS = Pattern.compile(
            "^\\s*(?:export\\s+(?:default\\s+)?)?(?:abstract\\s+)?class\\s+([A-Za-z_$][\\w$]*)(?:\\s+extends\\s+([A-Za-z_$][\\w$.]*))?(?:\\s+implements\\s+([^{]+))?");
    private static final Pattern FUNCTION = Pattern.compile(
            "^\\s*(?:export\\s+(?:default\\s+)?)?(?:async\\s+)?function\\*?\\s+([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern ARROW_CONST = Pattern.compile(
            "^\\s*(?:export\\s+(?:default\\s+)?)?(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*(?:async\\s+)?(?:\\([^)]*\\)|[A-Za-z_$][\\w$]*)\\s*=>");
    private static final Pattern VAR_DECL = Pattern.compile(
            "^\\s*(?:export\\s+(?:default\\s+)?)?(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern INTERFACE = Pattern.compile(
            "^\\s*(?:export\\s+)?interface\\s+([A-Za-z_$][\\w$]*)(?:\\s+extends\\s+([^{]+))?");
    private static final Pattern TYPE_ALIAS = Pattern.compile(
            "^\\s*(?:export\\s+)?type\\s+([A-Za-z_$][\\w$]*)\\s*=");
    private static final Pattern ENUM = Pattern.compile(
            "^\\s*(?:export\\s+(?:default\\s+)?)?(?:const\\s+)?enum\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*(?:public|private|protected|static|async|readonly|abstract)?\\s*([A-Za-z_$][\\w$]*)\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern FIELD = Pattern.compile(
            "^\\s*(?:public|private|protected|static|readonly)?\\s*([A-Za-z_$][\\w$]*)\\s*[?:]?\\s*=");

    private final LanguageId languageId;

    public JsSymbolExtractor() {
        this(LanguageId.JAVASCRIPT);
    }

    public JsSymbolExtractor(LanguageId languageId) {
        // 允许传入 JAVASCRIPT 或 TYPESCRIPT
        if (languageId != LanguageId.JAVASCRIPT && languageId != LanguageId.TYPESCRIPT) {
            this.languageId = LanguageId.JAVASCRIPT;
        } else {
            this.languageId = languageId;
        }
    }

    public ParsedFile extract(String path, String text) {
        List<Reference> refs = new ArrayList<>();
        String[] lines = text.split("\n");
        String moduleName = deriveModuleName(path);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // import { a, b } from 'mod' / import x from 'mod' / import * as x from 'mod'
            Matcher mIf = IMPORT_FROM.matcher(line);
            if (mIf.find()) {
                String mod = mIf.group(4);
                if (mod != null && !mod.isEmpty()) {
                    refs.add(new Reference(mod,
                            range(path, i, line, 0, line.length()),
                            Reference.ReferenceKind.IMPORT, moduleName, path, languageId));
                }
                if (mIf.group(1) != null) {
                    for (String n : mIf.group(1).split(",")) {
                        String name = n.trim().split("\\s+as\\s+")[0].trim();
                        if (!name.isEmpty()) addTypeRef(refs, path, i, line, name, moduleName);
                    }
                } else if (mIf.group(2) != null) {
                    addTypeRef(refs, path, i, line, mIf.group(2), moduleName);
                } else if (mIf.group(3) != null) {
                    addTypeRef(refs, path, i, line, mIf.group(3), moduleName);
                }
                continue;
            }
            // import 'side-effect'
            Matcher mSide = IMPORT_SIDE.matcher(line);
            if (mSide.find()) {
                refs.add(new Reference(mSide.group(1),
                        range(path, i, line, 0, line.length()),
                        Reference.ReferenceKind.IMPORT, moduleName, path, languageId));
                continue;
            }
            // const { a, b } = require('mod') / const x = require('mod')
            Matcher mReq = REQUIRE.matcher(line);
            if (mReq.find()) {
                refs.add(new Reference(mReq.group(3),
                        range(path, i, line, 0, line.length()),
                        Reference.ReferenceKind.IMPORT, moduleName, path, languageId));
                if (mReq.group(1) != null) {
                    for (String n : mReq.group(1).split(",")) {
                        String name = n.trim().split(":")[0].trim();
                        if (!name.isEmpty()) addVarRef(refs, path, i, line, name, moduleName);
                    }
                } else if (mReq.group(2) != null) {
                    addVarRef(refs, path, i, line, mReq.group(2), moduleName);
                }
                continue;
            }
            // class
            Matcher mCls = CLASS.matcher(line);
            if (mCls.find()) {
                refs.add(new Reference(mCls.group(1),
                        range(path, i, line, mCls.start(1), mCls.end(1)),
                        Reference.ReferenceKind.CLASS, moduleName, path, languageId));
                if (mCls.group(2) != null) {
                    addTypeRef(refs, path, i, line, mCls.group(2).trim(), moduleName);
                }
                if (mCls.group(3) != null) {
                    // implements A, B, C → split by comma
                    for (String impl : mCls.group(3).split(",")) {
                        String n = impl.trim();
                        if (!n.isEmpty()) addTypeRef(refs, path, i, line, n, moduleName);
                    }
                }
                continue;
            }
            // function
            Matcher mFn = FUNCTION.matcher(line);
            if (mFn.find()) {
                refs.add(new Reference(mFn.group(1),
                        range(path, i, line, mFn.start(1), mFn.end(1)),
                        Reference.ReferenceKind.METHOD, moduleName, path, languageId));
                continue;
            }
            // const x = () => ...
            Matcher mArrow = ARROW_CONST.matcher(line);
            if (mArrow.find()) {
                refs.add(new Reference(mArrow.group(1),
                        range(path, i, line, mArrow.start(1), mArrow.end(1)),
                        Reference.ReferenceKind.METHOD, moduleName, path, languageId));
                continue;
            }
            // interface / type / enum (TS)
            Matcher mIf2 = INTERFACE.matcher(line);
            if (mIf2.find()) {
                refs.add(new Reference(mIf2.group(1),
                        range(path, i, line, mIf2.start(1), mIf2.end(1)),
                        Reference.ReferenceKind.CLASS, moduleName, path, languageId));
                if (mIf2.group(2) != null) {
                    for (String ext : mIf2.group(2).split(",")) {
                        String n = ext.trim();
                        if (!n.isEmpty() && !n.startsWith("{")) addTypeRef(refs, path, i, line, n, moduleName);
                    }
                }
                continue;
            }
            Matcher mType = TYPE_ALIAS.matcher(line);
            if (mType.find()) {
                refs.add(new Reference(mType.group(1),
                        range(path, i, line, mType.start(1), mType.end(1)),
                        Reference.ReferenceKind.TYPE, moduleName, path, languageId));
                continue;
            }
            Matcher mEnum = ENUM.matcher(line);
            if (mEnum.find()) {
                refs.add(new Reference(mEnum.group(1),
                        range(path, i, line, mEnum.start(1), mEnum.end(1)),
                        Reference.ReferenceKind.TYPE, moduleName, path, languageId));
                continue;
            }
            // const / let / var (顶层)
            Matcher mVar = VAR_DECL.matcher(line);
            if (mVar.find()) {
                addVarRef(refs, path, i, line, mVar.group(1), moduleName);
            }
        }
        return new ParsedFile(path, languageId, moduleName, refs, text);
    }

    private void addTypeRef(List<Reference> refs, String path, int i, String line, String name, String mod) {
        refs.add(new Reference(name,
                range(path, i, line, 0, line.length()),
                Reference.ReferenceKind.TYPE, mod, path, languageId));
    }

    private void addVarRef(List<Reference> refs, String path, int i, String line, String name, String mod) {
        refs.add(new Reference(name,
                range(path, i, line, 0, line.length()),
                Reference.ReferenceKind.VARIABLE, mod, path, languageId));
    }

    private SourceRange range(String path, int i, String line, int start, int end) {
        return new SourceRange(
                new SourcePosition(path, i + 1, start + 1),
                new SourcePosition(path, i + 1, end));
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
