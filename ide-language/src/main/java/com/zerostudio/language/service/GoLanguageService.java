package com.zerostudio.language.service;

import com.zerostudio.language.model.*;
import com.zerostudio.language.goext.GoSymbolExtractor;
import com.zerostudio.language.source.SourceResolver;

import java.util.Optional;

public final class GoLanguageService extends LanguageService {
    private final GoSymbolExtractor extractor = new GoSymbolExtractor();

    public GoLanguageService() {
        super(LanguageId.GO);
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
            String fqn = resolvePackagePath(file, ref.name);
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

    private String resolvePackagePath(ParsedFile file, String name) {
        if (name.contains("/")) return name;
        if (file.packageName != null && !file.packageName.isEmpty()) {
            int idx = file.packageName.lastIndexOf('/');
            if (idx >= 0) {
                return file.packageName.substring(0, idx + 1) + name;
            }
        }
        return name;
    }
}
