package com.zerostudio.language.lexer;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hand-written Kotlin lexer. Same shape as {@link JavaLexer}; mostly the same
 * keywords plus {@code fun}, {@code val}, {@code var}, soft-keywords etc.
 *
 * <p>The primary path for full parsing goes through
 * {@code org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment}; this
 * lexer is used for the high-frequency highlighting pass and as a recovery
 * path when Kotlin compile is unavailable.
 */
public final class KotlinLexer implements Lexer {

    private static final Set<String> HARD_KEYWORDS = Set.of(
            "as", "break", "class", "continue", "do", "else", "false", "for",
            "fun", "if", "in", "interface", "is", "null", "object", "package",
            "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while"
    );

    private static final Set<String> SOFT_KEYWORDS = Set.of(
            "by", "catch", "constructor", "delegate", "dynamic", "field",
            "file", "finally", "get", "import", "init", "param", "property",
            "receiver", "set", "setparam", "value", "where",
            "abstract", "actual", "annotation", "companion", "const",
            "crossinline", "data", "enum", "expect", "external", "final",
            "infix", "inline", "inner", "internal", "lateinit", "noinline",
            "open", "operator", "out", "override", "private", "protected",
            "public", "reified", "sealed", "suspend", "tailrec", "vararg"
    );

    @Override
    public List<Token> tokenize(String text) {
        List<Token> out = new ArrayList<>();
        int len = text.length();
        int line = 0, col = 0;
        int i = 0;
        while (i < len) {
            int c = text.charAt(i);

            if (c == ' ' || c == '\t' || c == '\r') {
                int sLine = line, sCol = col;
                int start = i;
                while (i < len) {
                    int d = text.charAt(i);
                    if (d == '\n' || (d != ' ' && d != '\t' && d != '\r')) break;
                    i++; col++;
                }
                out.add(new Token(Token.Kind.WHITESPACE, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.KOTLIN));
                continue;
            }

            if (c == '\n') {
                out.add(new Token(Token.Kind.NEWLINE, "\n",
                        new SourceRange(line, col, line, col), LanguageId.KOTLIN));
                i++; line++; col = 0;
                continue;
            }

            // line comment
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '/') {
                int sLine = line, sCol = col;
                while (i < len && text.charAt(i) != '\n') { i++; col++; }
                out.add(new Token(Token.Kind.COMMENT, text.substring(sCol, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.KOTLIN));
                continue;
            }

            // block comment (Kotlin allows nesting)
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '*') {
                int sLine = line, sCol = col;
                int depth = 1;
                i += 2; col += 2;
                while (i + 1 < len && depth > 0) {
                    if (text.charAt(i) == '/' && text.charAt(i + 1) == '*') {
                        depth++; i += 2; col += 2;
                    } else if (text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
                        depth--; i += 2; col += 2;
                    } else if (text.charAt(i) == '\n') { line++; col = 0; i++; }
                    else { i++; col++; }
                }
                out.add(new Token(Token.Kind.COMMENT, text.substring(sCol, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.KOTLIN));
                continue;
            }

            // annotation / label
            if (c == '@' && i + 1 < len
                    && LexerUtils.isJavaIdentifierStart(text.charAt(i + 1))) {
                int sLine = line, sCol = col;
                i++; col++;
                while (i < len && LexerUtils.isJavaIdentifierPart(text.charAt(i))) {
                    i++; col++;
                }
                out.add(new Token(Token.Kind.ANNOTATION, text.substring(sCol, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.KOTLIN));
                continue;
            }

            // identifier / keyword
            if (LexerUtils.isJavaIdentifierStart(c)) {
                int sLine = line, sCol = col;
                int start = i;
                while (i < len && LexerUtils.isJavaIdentifierPart(text.charAt(i))) {
                    i++; col++;
                }
                String word = text.substring(start, i);
                Token.Kind k;
                if (HARD_KEYWORDS.contains(word)) k = Token.Kind.KEYWORD;
                else if (SOFT_KEYWORDS.contains(word)) k = Token.Kind.KEYWORD;
                else k = Token.Kind.IDENTIFIER;
                out.add(new Token(k, word,
                        new SourceRange(sLine, sCol, line, col), LanguageId.KOTLIN));
                continue;
            }

            // number
            if (LexerUtils.isDigit(c)) {
                int sLine = line, sCol = col;
                int start = i;
                while (i < len) {
                    int d = text.charAt(i);
                    if (LexerUtils.isDigit(d) || d == '.' || d == 'e' || d == 'E'
                            || d == 'x' || d == 'X' || d == 'b' || d == 'B'
                            || d == 'f' || d == 'F' || d == 'd' || d == 'D'
                            || d == 'l' || d == 'L' || d == '_') {
                        i++; col++;
                    } else {
                        break;
                    }
                }
                out.add(new Token(Token.Kind.NUMBER, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.KOTLIN));
                continue;
            }

            // triple-quoted string
            if (c == '"' && i + 2 < len && text.charAt(i + 1) == '"'
                    && text.charAt(i + 2) == '"') {
                int sLine = line, sCol = col;
                int start = i;
                i += 3; col += 3;
                while (i + 2 < len
                        && !(text.charAt(i) == '"' && text.charAt(i + 1) == '"'
                                && text.charAt(i + 2) == '"')) {
                    if (text.charAt(i) == '\n') { line++; col = 0; i++; }
                    else { i++; col++; }
                }
                if (i + 2 < len) { i += 3; col += 3; }
                out.add(new Token(Token.Kind.STRING, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.KOTLIN));
                continue;
            }

            // string
            if (c == '"') {
                int sLine = line, sCol = col;
                int start = i;
                i++; col++;
                while (i < len && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < len) {
                        if (text.charAt(i + 1) == '\n') { line++; col = 0; i += 2; }
                        else { i += 2; col += 2; }
                    } else if (text.charAt(i) == '\n') { line++; col = 0; i++; }
                    else { i++; col++; }
                }
                if (i < len) { i++; col++; }
                out.add(new Token(Token.Kind.STRING, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.KOTLIN));
                continue;
            }

            if (c == '\'') {
                int sLine = line, sCol = col;
                int start = i;
                i++; col++;
                while (i < len && text.charAt(i) != '\'') {
                    if (text.charAt(i) == '\\' && i + 1 < len) { i += 2; col += 2; }
                    else { i++; col++; }
                }
                if (i < len) { i++; col++; }
                out.add(new Token(Token.Kind.CHAR, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.KOTLIN));
                continue;
            }

            if (isOpStart((char) c)) {
                int sLine = line, sCol = col;
                int start = i;
                while (i < len && isOpPart(text.charAt(i))) { i++; col++; }
                out.add(new Token(Token.Kind.OPERATOR, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.KOTLIN));
                continue;
            }

            int sLine = line, sCol = col;
            out.add(new Token(Token.Kind.UNKNOWN, String.valueOf((char) c),
                    new SourceRange(sLine, sCol, line, col + 1), LanguageId.KOTLIN));
            col++; i++;
        }
        out.add(LexerUtils.eof(line, col, LanguageId.KOTLIN));
        return out;
    }

    @Override
    public LanguageId language() { return LanguageId.KOTLIN; }

    private static boolean isOpStart(char c) {
        return "+-*/%=<>!&|^~?:.,;()[]{}@$".indexOf(c) >= 0;
    }
    private static boolean isOpPart(char c) {
        return "+-*/%=<>!&|^~?:.,;()[]{}@$".indexOf(c) >= 0;
    }
}
