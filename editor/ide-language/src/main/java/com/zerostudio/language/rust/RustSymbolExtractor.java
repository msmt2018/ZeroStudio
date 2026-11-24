package com.zerostudio.language.rust;

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
 * Rust 符号解析器：
 *  - use foo::bar::{baz, qux} / use foo::bar as fb
 *  - mod foo / mod foo { ... } / mod foo;
 *  - struct Foo { ... } / struct Foo(...);
 *  - enum Foo { ... }
 *  - trait Foo { ... }
 *  - fn foo() / pub fn / async fn / const fn / unsafe fn
 *  - impl Foo { ... } / impl Trait for Foo { ... }
 *  - type Foo = Bar;
 *  - const X / static X
 *  - let binding (顶层简化)
 */
public final class RustSymbolExtractor {

    private static final Pattern USE = Pattern.compile(
            "^\\s*(?:pub\\s+)?use\\s+([\\w:]+?)(?:::\\s*\\{([^}]+)\\})?(?:\\s+as\\s+([A-Za-z_]\\w*))?\\s*;");
    private static final Pattern MOD = Pattern.compile(
            "^\\s*(?:pub\\s+)?mod\\s+([A-Za-z_]\\w*)");
    private static final Pattern STRUCT = Pattern.compile(
            "^\\s*(?:pub\\s+)?struct\\s+([A-Za-z_]\\w*)");
    private static final Pattern ENUM = Pattern.compile(
            "^\\s*(?:pub\\s+)?enum\\s+([A-Za-z_]\\w*)");
    private static final Pattern TRAIT = Pattern.compile(
            "^\\s*(?:pub\\s+)?trait\\s+([A-Za-z_]\\w*)");
    private static final Pattern FN = Pattern.compile(
            "^\\s*(?:pub\\s+)?(?:async\\s+|const\\s+|unsafe\\s+)*fn\\s+([A-Za-z_]\\w*)");
    private static final Pattern IMPL = Pattern.compile(
            "^\\s*impl(?:<[^>]*>)?\\s+(?:([A-Za-z_]\\w*)\\s+for\\s+)?([A-Za-z_]\\w*)");
    private static final Pattern TYPE_ALIAS = Pattern.compile(
            "^\\s*(?:pub\\s+)?type\\s+([A-Za-z_]\\w*)");
    private static final Pattern CONST = Pattern.compile(
            "^\\s*(?:pub\\s+)?const\\s+([A-Za-z_]\\w*)");
    private static final Pattern STATIC = Pattern.compile(
            "^\\s*(?:pub\\s+)?static\\s+(?:mut\\s+)?([A-Za-z_]\\w*)");

    public ParsedFile extract(String path, String text) {
        List<Reference> refs = new ArrayList<>();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // use foo::bar as fb / use foo::{a, b}
            Matcher mUse = USE.matcher(line);
            if (mUse.find()) {
                String full = mUse.group(1);
                refs.add(new Reference(full,
                        range(path, i, line, mUse.start(1), mUse.end(1)),
                        Reference.ReferenceKind.IMPORT, "", path, LanguageId.RUST));
                if (mUse.group(3) != null) {
                    refs.add(new Reference(mUse.group(3),
                            range(path, i, line, mUse.start(3), mUse.end(3)),
                            Reference.ReferenceKind.TYPE, "", path, LanguageId.RUST));
                }
                if (mUse.group(2) != null) {
                    for (String n : mUse.group(2).split(",")) {
                        String name = n.trim().split("\\s+as\\s+")[0].trim();
                        if (!name.isEmpty()) {
                            refs.add(new Reference(name,
                                    range(path, i, line, 0, line.length()),
                                    Reference.ReferenceKind.TYPE, "", path, LanguageId.RUST));
                        }
                    }
                }
                continue;
            }
            // mod foo
            Matcher mMod = MOD.matcher(line);
            if (mMod.find()) {
                refs.add(new Reference(mMod.group(1),
                        range(path, i, line, mMod.start(1), mMod.end(1)),
                        Reference.ReferenceKind.CLASS, "", path, LanguageId.RUST));
                continue;
            }
            // struct
            Matcher mStruct = STRUCT.matcher(line);
            if (mStruct.find()) {
                refs.add(new Reference(mStruct.group(1),
                        range(path, i, line, mStruct.start(1), mStruct.end(1)),
                        Reference.ReferenceKind.CLASS, "", path, LanguageId.RUST));
                continue;
            }
            // enum
            Matcher mEnum = ENUM.matcher(line);
            if (mEnum.find()) {
                refs.add(new Reference(mEnum.group(1),
                        range(path, i, line, mEnum.start(1), mEnum.end(1)),
                        Reference.ReferenceKind.TYPE, "", path, LanguageId.RUST));
                continue;
            }
            // trait
            Matcher mTrait = TRAIT.matcher(line);
            if (mTrait.find()) {
                refs.add(new Reference(mTrait.group(1),
                        range(path, i, line, mTrait.start(1), mTrait.end(1)),
                        Reference.ReferenceKind.CLASS, "", path, LanguageId.RUST));
                continue;
            }
            // fn
            Matcher mFn = FN.matcher(line);
            if (mFn.find()) {
                refs.add(new Reference(mFn.group(1),
                        range(path, i, line, mFn.start(1), mFn.end(1)),
                        Reference.ReferenceKind.METHOD, "", path, LanguageId.RUST));
                continue;
            }
            // impl
            Matcher mImpl = IMPL.matcher(line);
            if (mImpl.find()) {
                String traitName = mImpl.group(1);
                String target = mImpl.group(2);
                if (target != null) {
                    refs.add(new Reference(target,
                            range(path, i, line, mImpl.start(2), mImpl.end(2)),
                            Reference.ReferenceKind.CLASS, "", path, LanguageId.RUST));
                }
                if (traitName != null) {
                    refs.add(new Reference(traitName,
                            range(path, i, line, mImpl.start(1), mImpl.end(1)),
                            Reference.ReferenceKind.CLASS, "", path, LanguageId.RUST));
                }
                continue;
            }
            // type
            Matcher mType = TYPE_ALIAS.matcher(line);
            if (mType.find()) {
                refs.add(new Reference(mType.group(1),
                        range(path, i, line, mType.start(1), mType.end(1)),
                        Reference.ReferenceKind.TYPE, "", path, LanguageId.RUST));
                continue;
            }
            // const
            Matcher mConst = CONST.matcher(line);
            if (mConst.find()) {
                refs.add(new Reference(mConst.group(1),
                        range(path, i, line, mConst.start(1), mConst.end(1)),
                        Reference.ReferenceKind.VARIABLE, "", path, LanguageId.RUST));
                continue;
            }
            // static
            Matcher mStatic = STATIC.matcher(line);
            if (mStatic.find()) {
                refs.add(new Reference(mStatic.group(1),
                        range(path, i, line, mStatic.start(1), mStatic.end(1)),
                        Reference.ReferenceKind.VARIABLE, "", path, LanguageId.RUST));
            }
        }
        return new ParsedFile(path, LanguageId.RUST, "", refs, text);
    }

    private SourceRange range(String path, int i, String line, int start, int end) {
        return new SourceRange(
                new SourcePosition(path, i + 1, start + 1),
                new SourcePosition(path, i + 1, end));
    }
}
