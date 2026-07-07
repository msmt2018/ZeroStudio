package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import com.zerostudio.language.rust.RustSymbolExtractor;
import com.zerostudio.language.source.SourceResolver;

import java.util.Optional;

public final class RustLanguageService extends LanguageService {
    private final RustSymbolExtractor extractor = new RustSymbolExtractor();

    public RustLanguageService() {
        super(LanguageId.RUST);
    }

    @Override
    public Optional<ParsedFile> parse(String path, String text) {
        try {
            ParsedFile pf = extractor.extract(path, text);
            notifyChanged(pf);
            return Optional.of(pf);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ResolutionResult> resolve(ParsedFile file, Reference ref) {
        if (sourceResolver() != null) {
            String fqn = resolveCratePath(file, ref.name);
            SourceResolver.ResolvedSource src = sourceResolver().resolve(fqn);
            if (src.isResolved()) {
                SymbolKind kind = mapKind(ref.kind);
                return Optional.of(ResolutionResult.resolved(
                        src.displayPath,
                        null,
                        new Symbol(ref.name, fqn, kind, "", src.displayPath)
                ));
            }
        }
        return Optional.empty();
    }

    private SymbolKind mapKind(Reference.ReferenceKind kind) {
        switch (kind) {
            case CLASS: return SymbolKind.CLASS;
            case METHOD: return SymbolKind.METHOD;
            case FIELD: return SymbolKind.FIELD;
            case VARIABLE: return SymbolKind.LOCAL_VAR;
            default: return SymbolKind.UNKNOWN;
        }
    }

    private String resolveCratePath(ParsedFile file, String name) {
        if (name.contains("::")) return name;
        String crate = deriveCrateName(file.packageName);
        if (crate != null && !name.startsWith(crate + "::")) {
            return crate + "::" + name;
        }
        return name;
    }

    private String deriveCrateName(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        int idx = pkg.lastIndexOf('/');
        return idx >= 0 ? pkg.substring(idx + 1) : pkg;
    }
}
