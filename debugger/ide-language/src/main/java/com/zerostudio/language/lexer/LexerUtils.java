package com.zerostudio.language.lexer;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.SourceRange;

/**
 * Utilities shared by hand-written lexers. Tree-Sitter and JavaParser-based
 * lexers do not need these; the Java hand-written fallback lexer does.
 */
public final class LexerUtils {

    private LexerUtils() {}

    public static boolean isJavaIdentifierStart(int c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    public static boolean isJavaIdentifierPart(int c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    public static boolean isCIdentifierStart(int c) {
        return Character.isLetter(c) || c == '_';
    }

    public static boolean isCIdentifierPart(int c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    public static Token eof(int line, int col, LanguageId lang) {
        return new Token(
                Token.Kind.EOF,
                "",
                new SourceRange(line, col, line, col),
                lang);
    }
}
