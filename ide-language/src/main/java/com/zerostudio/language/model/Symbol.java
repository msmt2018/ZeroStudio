package com.zerostudio.language.model;

import java.util.Objects;

/**
 * A language symbol: a named, located entity in source code.
 *
 * <p>Symbols are produced by parsers and stored in the cross-file
 * {@link com.zerostudio.language.index.ProjectIndex}.
 */
public final class Symbol {
    public final String name;
    public final String fqn;             // fully qualified name (may be null)
    public final SymbolKind kind;
    public final String containerName;   // owning class / namespace
    public final String sourceFile;      // absolute file path
    public final SourceRange range;
    public final LanguageId language;

    public Symbol(String name,
                  String fqn,
                  SymbolKind kind,
                  String containerName,
                  String sourceFile,
                  SourceRange range,
                  LanguageId language) {
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sourceFile = sourceFile;
        this.range = range == null ? SourceRange.NONE : range;
        this.fqn = fqn;
        this.containerName = containerName;
        this.language = language;
    }

    public boolean isCallable() {
        return kind == SymbolKind.METHOD
                || kind == SymbolKind.CONSTRUCTOR
                || kind == SymbolKind.FUNCTION;
    }

    @Override
    public String toString() {
        return kind + " " + (fqn != null ? fqn : name) + " @ " + sourceFile + range;
    }
}
