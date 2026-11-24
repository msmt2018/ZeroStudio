package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import com.zerostudio.language.source.SourceResolver;
import java.util.*;
import java.util.regex.*;

public final class ImportResolver {
    private final SourceResolver sources;
    private static final Pattern QUALIFIED = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*");

    public ImportResolver(SourceResolver sources) { this.sources = sources; }

    public Optional<ResolutionResult> resolveImport(ParsedFile file, Reference ref) {
        if (file == null || file.references == null) return Optional.empty();
        if (file.language != LanguageId.JAVA) return Optional.empty();
        if (ref.kind != Reference.ReferenceKind.CLASS && ref.kind != Reference.ReferenceKind.METHOD
                && ref.kind != Reference.ReferenceKind.FIELD && ref.kind != Reference.ReferenceKind.IMPORT)
            return Optional.empty();

        // If ref is an IMPORT reference, resolve it directly
        if (ref.kind == Reference.ReferenceKind.IMPORT) {
            SourceResolver.ResolvedSource src = sources.resolve(ref.name);
            if (src.isResolved()) {
                String simpleName = ref.name.contains(".") ?
                        ref.name.substring(ref.name.lastIndexOf('.') + 1) : ref.name;
                String pkg = ref.name.contains(".") ?
                        ref.name.substring(0, ref.name.lastIndexOf('.')) : "";
                return Optional.of(ResolutionResult.resolved(
                        src.displayPath, null,
                        new Symbol(simpleName, ref.name, SymbolKind.CLASS, pkg, src.displayPath)));
            }
            return Optional.empty();
        }

        // find matching import for the reference name
        for (Reference imp : file.references) {
            if (imp.kind != Reference.ReferenceKind.IMPORT) continue;
            String impName = imp.name;
            if (impName.endsWith(".*")) {
                String pkg = impName.substring(0, impName.length() - 2);
                String candidate = pkg + "." + ref.name;
                SourceResolver.ResolvedSource src = sources.resolve(candidate);
                if (src.isResolved()) {
                    return Optional.of(ResolutionResult.resolved(
                            src.displayPath, null,
                            new Symbol(ref.name, candidate, SymbolKind.CLASS, pkg, src.displayPath)));
                }
            } else {
                String simpleName = impName.substring(impName.lastIndexOf('.') + 1);
                if (simpleName.equals(ref.name)) {
                    SourceResolver.ResolvedSource src = sources.resolve(impName);
                    if (src.isResolved()) {
                        return Optional.of(ResolutionResult.resolved(
                                src.displayPath, null,
                                new Symbol(simpleName, impName, SymbolKind.CLASS,
                                        impName.substring(0, impName.lastIndexOf('.')), src.displayPath)));
                    }
                }
            }
        }
        return Optional.empty();
    }
}