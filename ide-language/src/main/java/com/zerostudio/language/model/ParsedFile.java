package com.zerostudio.language.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The result of parsing a single file: a token stream, a list of declared
 * symbols, a list of references, and a link to the underlying parser-specific
 * AST (which is opaque to the rest of the library).
 */
public final class ParsedFile {
    public final String path;             // absolute path
    public final LanguageId language;
    public final long parsedAtMillis;
    public final String sourceText;       // raw file content
    public final List<Symbol> symbols;
    public final List<Reference> references;
    public final Object nativeAst;        // JavaParser CompilationUnit / Kotlin
                                          // PsiFile / Tree-Sitter Tree
    public final String parseError;       // null if parse succeeded

    public ParsedFile(String path,
                      LanguageId language,
                      long parsedAtMillis,
                      String sourceText,
                      List<Symbol> symbols,
                      List<Reference> references,
                      Object nativeAst,
                      String parseError) {
        this.path = path;
        this.language = language;
        this.parsedAtMillis = parsedAtMillis;
        this.sourceText = sourceText;
        this.symbols = symbols == null ? Collections.emptyList() : symbols;
        this.references = references == null ? Collections.emptyList() : references;
        this.nativeAst = nativeAst;
        this.parseError = parseError;
    }

    public boolean hasErrors() { return parseError != null; }
}
