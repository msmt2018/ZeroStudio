package com.zerostudio.language.goext;

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
 * Go 符号解析器：
 *  - package foo
 *  - import "fmt" / import ( ... )
 *  - type Foo struct { ... } / type Foo interface { ... } / type Foo int
 *  - func foo() / func (r *Receiver) Method() / func (T) Generic()
 *  - var x int / const y = 1
 *  - 多行 import (...)
 */
public final class GoSymbolExtractor {

    private static final Pattern PACKAGE = Pattern.compile(
            "^\\s*package\\s+([A-Za-z_]\\w*)");
    private static final Pattern IMPORT_SINGLE = Pattern.compile(
            "^\\s*import\\s+(?:[A-Za-z_]\\w*\\s+)?\"([^\"]+)\"");
    private static final Pattern IMPORT_ALIAS = Pattern.compile(
            "^\\s*import\\s+([A-Za-z_]\\w*)\\s+\"([^\"]+)\"");
    private static final Pattern IMPORT_BARE = Pattern.compile(
            "^\\s*\"([^\"]+)\"");
    private static final Pattern IMPORT_BLOCK_ALIAS = Pattern.compile(
            "^\\s*([A-Za-z_]\\w*)\\s+\"([^\"]+)\"");
    private static final Pattern TYPE_STRUCT = Pattern.compile(
            "^\\s*type\\s+([A-Za-z_]\\w*)\\s+struct\\b");
    private static final Pattern TYPE_INTERFACE = Pattern.compile(
            "^\\s*type\\s+([A-Za-z_]\\w*)\\s+interface\\b");
    private static final Pattern TYPE_ALIAS = Pattern.compile(
            "^\\s*type\\s+([A-Za-z_]\\w*)\\s+([A-Za-z_]\\w*)");
    private static final Pattern FUNC = Pattern.compile(
            "^\\s*func\\s+(?:\\(\\s*([A-Za-z_]\\w*)\\s*\\*?\\s*([A-Za-z_]\\w*)?\\s*\\)\\s*)?([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern VAR = Pattern.compile(
            "^\\s*var\\s+([A-Za-z_]\\w*)");
    private static final Pattern CONST = Pattern.compile(
            "^\\s*const\\s+([A-Za-z_]\\w*)");

    public ParsedFile extract(String path, String text) {
        List<Reference> refs = new ArrayList<>();
        String packageName = "";
        String[] lines = text.split("\n");
        boolean inImportBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // 多行 import 块处理
            if (inImportBlock) {
                if (trimmed.startsWith(")")) {
                    inImportBlock = false;
                    continue;
                }
                // alias  "path"
                Matcher mAl = IMPORT_BLOCK_ALIAS.matcher(trimmed);
                if (mAl.find()) {
                    refs.add(new Reference(mAl.group(2),
                            range(path, i, line, 0, line.length()),
                            Reference.ReferenceKind.IMPORT, packageName, path, LanguageId.GO));
                    refs.add(new Reference(mAl.group(1),
                            range(path, i, line, 0, line.length()),
                            Reference.ReferenceKind.TYPE, packageName, path, LanguageId.GO));
                    continue;
                }
                // bare "path"
                Matcher mBare = IMPORT_BARE.matcher(trimmed);
                if (mBare.find()) {
                    refs.add(new Reference(mBare.group(1),
                            range(path, i, line, 0, line.length()),
                            Reference.ReferenceKind.IMPORT, packageName, path, LanguageId.GO));
                    continue;
                }
                continue;
            }
            // 进入 import 块
            if (trimmed.startsWith("import (")) {
                inImportBlock = true;
                continue;
            }
            // package
            Matcher mPkg = PACKAGE.matcher(line);
            if (mPkg.find()) {
                packageName = mPkg.group(1);
                refs.add(new Reference(packageName,
                        range(path, i, line, mPkg.start(1), mPkg.end(1)),
                        Reference.ReferenceKind.TYPE, "", path, LanguageId.GO));
                continue;
            }
            // import alias "path"  (在 import 之前检查，避免 IMPORT_SINGLE 吃掉 alias)
            Matcher mAlSingle = IMPORT_ALIAS.matcher(line);
            if (mAlSingle.find()) {
                refs.add(new Reference(mAlSingle.group(2),
                        range(path, i, line, 0, line.length()),
                        Reference.ReferenceKind.IMPORT, packageName, path, LanguageId.GO));
                refs.add(new Reference(mAlSingle.group(1),
                        range(path, i, line, 0, line.length()),
                        Reference.ReferenceKind.TYPE, packageName, path, LanguageId.GO));
                continue;
            }
            // import single
            Matcher mImp = IMPORT_SINGLE.matcher(line);
            if (mImp.find()) {
                refs.add(new Reference(mImp.group(1),
                        range(path, i, line, 0, line.length()),
                        Reference.ReferenceKind.IMPORT, packageName, path, LanguageId.GO));
                continue;
            }
            // type ... struct
            Matcher mStr = TYPE_STRUCT.matcher(line);
            if (mStr.find()) {
                refs.add(new Reference(mStr.group(1),
                        range(path, i, line, mStr.start(1), mStr.end(1)),
                        Reference.ReferenceKind.CLASS, packageName, path, LanguageId.GO));
                continue;
            }
            // type ... interface
            Matcher mIf = TYPE_INTERFACE.matcher(line);
            if (mIf.find()) {
                refs.add(new Reference(mIf.group(1),
                        range(path, i, line, mIf.start(1), mIf.end(1)),
                        Reference.ReferenceKind.CLASS, packageName, path, LanguageId.GO));
                continue;
            }
            // type X Y  (alias)
            Matcher mTa = TYPE_ALIAS.matcher(line);
            if (mTa.find() && !line.contains("struct") && !line.contains("interface")) {
                refs.add(new Reference(mTa.group(1),
                        range(path, i, line, mTa.start(1), mTa.end(1)),
                        Reference.ReferenceKind.TYPE, packageName, path, LanguageId.GO));
                continue;
            }
            // func
            Matcher mFn = FUNC.matcher(line);
            if (mFn.find()) {
                String name = mFn.group(3);
                refs.add(new Reference(name,
                        range(path, i, line, mFn.start(3), mFn.end(3)),
                        Reference.ReferenceKind.METHOD, packageName, path, LanguageId.GO));
                continue;
            }
            // var / const
            Matcher mVar = VAR.matcher(line);
            if (mVar.find()) {
                refs.add(new Reference(mVar.group(1),
                        range(path, i, line, mVar.start(1), mVar.end(1)),
                        Reference.ReferenceKind.VARIABLE, packageName, path, LanguageId.GO));
                continue;
            }
            Matcher mConst = CONST.matcher(line);
            if (mConst.find()) {
                refs.add(new Reference(mConst.group(1),
                        range(path, i, line, mConst.start(1), mConst.end(1)),
                        Reference.ReferenceKind.VARIABLE, packageName, path, LanguageId.GO));
            }
        }
        return new ParsedFile(path, LanguageId.GO, packageName, refs, text);
    }

    private SourceRange range(String path, int i, String line, int start, int end) {
        return new SourceRange(
                new SourcePosition(path, i + 1, start + 1),
                new SourcePosition(path, i + 1, end));
    }
}
