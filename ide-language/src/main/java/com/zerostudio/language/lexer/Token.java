package com.zerostudio.language.lexer;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.SourceRange;

import java.util.Collections;
import java.util.List;

/**
 * Lexer output: a stream of tokens plus their kind and source range.
 *
 * <p>Tokens are 0-indexed; a token's range is {@code [start, end)} in the
 * source file, both 0-based.
 */
public final class Token {
    public enum Kind {
        IDENTIFIER,
        KEYWORD,
        NUMBER,
        STRING,
        CHAR,
        OPERATOR,
        PUNCTUATION,
        COMMENT,
        WHITESPACE,
        PREPROCESSOR,    // C #include / #define
        ANNOTATION,      // Java/Kotlin @Foo
        NEWLINE,
        EOF,
        UNKNOWN
    }

    public final Kind kind;
    public final String text;
    public final SourceRange range;
    public final LanguageId language;

    public Token(Kind kind, String text, SourceRange range, LanguageId language) {
        this.kind = kind;
        this.text = text;
        this.range = range;
        this.language = language;
    }

    public static final List<Token> EMPTY = Collections.emptyList();
}
