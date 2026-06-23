package com.zerostudio.language.service;

import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.SourceLocation;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;
import com.zerostudio.language.source.SourceResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bridges Go-to-Definition with the {@link SourceResolver}.
 *
 * <p>When the user clicks on a simple name that is NOT defined in the
 * current file (e.g. {@code Toast} in {@code Toast.makeText(ctx, ...)}),
 * the {@link com.zerostudio.language.symbols.JavaSymbolResolver}
 * returns no workspace symbol. We then look at the file's
 * {@code import} declarations: if the simple name is the trailing
 * component of an import (or matches a star import), we ask the
 * {@link SourceResolver} to materialise the class and return a
 * {@link ResolutionResult} pointing into the decompiled / source-jar
 * / workspace source of that class.
 *
 * <p>The result's {@code targetFile} is a virtual identifier - either
 * the workspace path (e.g. {@code android/widget/Toast.java}) or a
 * display string ({@code [decompiled from android.jar]}). The editor
 * inspects the {@link SourceRange} to decide whether to scroll to a
 * real file or open a virtual buffer.
 */
public final class ImportResolver {

    private final SourceResolver sources;

    public ImportResolver(SourceResolver sources) {
        this.sources = Objects.requireNonNull(sources);
    }

    /**
     * Try to resolve {@code ref} via the file's import declarations.
     *
     * @return a resolved result if an import matched and the class
     *         was found; otherwise {@code Optional.empty()}.
     */
    public Optional<ResolutionResult> resolveImport(ParsedFile file,
                                                    Reference ref) {
        if (ref.kind == Reference.ReferenceKind.IMPORT) {
            // The user clicked on the import line itself - the
            // "target" is the imported class directly.
            return resolveFqn(ref.name, ref);
        }
        // Otherwise, find the import that the simple name is a
        // suffix of.
        String simple = ref.name;
        int dot = simple.lastIndexOf('.');
        String tail = dot < 0 ? simple : simple.substring(dot + 1);
        for (Reference imp : file.references) {
            if (imp.kind != Reference.ReferenceKind.IMPORT) continue;
            String fqn = imp.name;
            if (fqn.endsWith(".*")) {
                String pkg = fqn.substring(0, fqn.length() - 2);
                // The simple name is the resolved FQN - try resolving.
                if (matches(tail, simple, pkg + "." + simple)) {
                    Optional<ResolutionResult> r =
                            resolveFqn(pkg + "." + simple, ref);
                    if (r.isPresent()) return r;
                }
            } else if (fqn.endsWith("." + tail)) {
                Optional<ResolutionResult> r = resolveFqn(fqn, ref);
                if (r.isPresent()) return r;
            }
        }
        return Optional.empty();
    }

    private Optional<ResolutionResult> resolveFqn(String fqn, Reference ref) {
        SourceResolver.ResolvedSource src = sources.resolve(fqn);
        if (src.kind == SourceResolver.Kind.MISSING) {
            return Optional.empty();
        }
        // The editor will open src.displayPath with src.sourceText
        // as the buffer content and put the cursor at the start of
        // the (decompiled) class. We don't have a precise line for
        // the class declaration in decompiled output, so we
        // synthesise a range at line 0 - the editor will scroll to
        // the top and the user can navigate from there.
        SourceRange range = new SourceRange(0, 0, 0, 0);
        Symbol synthetic = new Symbol(
                className(fqn), fqn,
                SymbolKind.CLASS,
                "",
                src.displayPath,
                range,
                ref.language);
        return Optional.of(new ResolutionResult(
                ref, synthetic, src.displayPath, range, false));
    }

    private static String className(String fqn) {
        int i = fqn.lastIndexOf('.');
        return i < 0 ? fqn : fqn.substring(i + 1);
    }

    private static boolean matches(String tail, String ref, String fqn) {
        // Star-import match: a star import covers any class in the
        // package whose simple name equals the reference's tail.
        return tail.equals(ref) || ref.endsWith("." + tail) || ref.equals(fqn);
    }

    /**
     * Helper: build a {@link SourceLocation} for the resolved class.
     * Useful for the editor's open-orchestration code.
     */
    public SourceLocation asLocation(SourceResolver.ResolvedSource res) {
        return new SourceLocation(res.displayPath, new SourcePosition(0, 0));
    }

    /** Aggregated list of every import the file declares. */
    public static List<String> imports(ParsedFile file) {
        List<String> out = new ArrayList<>();
        for (Reference r : file.references) {
            if (r.kind == Reference.ReferenceKind.IMPORT) out.add(r.name);
        }
        return out;
    }
}
