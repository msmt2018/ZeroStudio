package com.zerostudio.language.model;

import java.util.Objects;

/**
 * A reference to a symbol at a particular source position. A reference is the
 * text-level occurrence of a name; the same name may be referenced many times.
 */
public final class Reference {
    public final String name;
    public final SourceRange range;
    public final ReferenceKind kind;
    public final String containerFqn;   // symbol that contains this reference
    public final String sourceFile;
    public final LanguageId language;

    public Reference(String name,
                     SourceRange range,
                     ReferenceKind kind,
                     String containerFqn,
                     String sourceFile,
                     LanguageId language) {
        this.name = Objects.requireNonNull(name, "name");
        this.range = Objects.requireNonNull(range, "range");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.containerFqn = containerFqn;
        this.sourceFile = sourceFile;
        this.language = language;
    }

    public enum ReferenceKind {
        CALL,                // method / function call
        TYPE_USE,            // type reference in declaration
        READ,                // variable read
        WRITE,               // variable write / assignment
        IMPORT,              // import / using statement
        EXTENDS,             // class extends
        IMPLEMENTS,          // implements / interface
        FIELD_ACCESS,        // field / member access
        OVERRIDE             // @Override
    }
}
