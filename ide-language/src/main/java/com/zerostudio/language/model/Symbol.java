package com.zerostudio.language.model;
public final class Symbol {
    public final String name;
    public final String fqn;
    public final SymbolKind kind;
    public final String declaringClass;
    public final String sourcePath;
    public Symbol(String name, String fqn, SymbolKind kind, String declaringClass, String sourcePath) {
        this.name = name; this.fqn = fqn; this.kind = kind;
        this.declaringClass = declaringClass; this.sourcePath = sourcePath;
    }
}