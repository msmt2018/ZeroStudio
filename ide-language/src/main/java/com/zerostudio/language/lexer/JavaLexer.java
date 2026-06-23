package com.zerostudio.language.lexer;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.SourcePosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Java hand-written fallback lexer.
 *
 * <p>The primary Java path goes through JavaParser, but a small hand-written
 * lexer is kept here for: (1) syntax highlighting in the editor while the
 * parser is still working; (2) computing token-level position data even when
 * the parser fails on malformed input.
 */
public final class JavaLexer implements Lexer {

    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "yield",
            "var", "record", "sealed", "non-sealed", "permits", "module",
            "requires", "exports", "opens", "to", "transitive", "with"
    );

    @Override
    public List<Token> tokenize(String text) {
        List<Token> out = new ArrayList<>();
        int len = text.length();
        int line = 0, col = 0;
        int i = 0;
        while (i < len) {
            int c = text.charAt(i);

            // whitespace
            if (c == ' ' || c == '\t' || c == '\r') {
                int start = i;
                int sLine = line, sCol = col;
                while (i < len) {
                    int d = text.charAt(i);
                    if (d == '\n') break;
                    if (d != ' ' && d != '\t' && d != '\r') break;
                    i++; col++;
                }
                out.add(new Token(Token.Kind.WHITESPACE, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.JAVA));
                continue;
            }

            if (c == '\n') {
                int sLine = line, sCol = col;
                out.add(new Token(Token.Kind.NEWLINE, "\n",
                        new SourceRange(sLine, sCol, line, col), LanguageId.JAVA));
                i++; line++; col = 0;
                continue;
            }

            // line comment
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '/') {
                int sLine = line, sCol = col;
                while (i < len && text.charAt(i) != '\n') { i++; col++; }
                out.add(new Token(Token.Kind.COMMENT, text.substring(sCol > 0 ? sCol : 0, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.JAVA));
                continue;
            }

            // block comment
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '*') {
                int sLine = line, sCol = col;
                i += 2; col += 2;
                while (i + 1 < len && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
                    if (text.charAt(i) == '\n') { line++; col = 0; i++; }
                    else { i++; col++; }
                }
                if (i + 1 < len) { i += 2; col += 2; }
                out.add(new Token(Token.Kind.COMMENT, text.substring(sCol > 0 ? sCol : 0, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.JAVA));
                continue;
            }

            // annotation
            if (c == '@' && i + 1 < len
                    && LexerUtils.isJavaIdentifierStart(text.charAt(i + 1))) {
                int sLine = line, sCol = col;
                i++; col++;
                while (i < len && LexerUtils.isJavaIdentifierPart(text.charAt(i))) {
                    i++; col++;
                }
                out.add(new Token(Token.Kind.ANNOTATION, text.substring(sCol - 1, i),
                        new SourceRange(sLine, sCol - 1, line, col), LanguageId.JAVA));
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
                Token.Kind k = KEYWORDS.contains(word) ? Token.Kind.KEYWORD : Token.Kind.IDENTIFIER;
                out.add(new Token(k, word,
                        new SourceRange(sLine, sCol, line, col), LanguageId.JAVA));
                continue;
            }

            // number
            if (LexerUtils.isDigit(c)
                    || (c == '.' && i + 1 < len && LexerUtils.isDigit(text.charAt(i + 1)))) {
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
                        new SourceRange(sLine, sCol, line, col), LanguageId.JAVA));
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
                        new SourceRange(sLine, sCol, line, col), LanguageId.JAVA));
                continue;
            }

            if (c == '\'') {
                int sLine = line, sCol = col;
                int start = i;
                i++; col++;
                while (i < len && text.charAt(i) != '\'') {
                    if (text.charAt(i) == '\\' && i + 1 < len) { i += 2; col += 2; }
                    else if (text.charAt(i) == '\n') { line++; col = 0; i++; }
                    else { i++; col++; }
                }
                if (i < len) { i++; col++; }
                out.add(new Token(Token.Kind.CHAR, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.JAVA));
                continue;
            }

            // operator / punctuation
            if (isOpStart((char) c)) {
                int sLine = line, sCol = col;
                int start = i;
                while (i < len && isOpPart(text.charAt(i))) { i++; col++; }
                out.add(new Token(Token.Kind.OPERATOR, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.JAVA));
                continue;
            }

            // fallback
            int sLine = line, sCol = col;
            out.add(new Token(Token.Kind.UNKNOWN, String.valueOf((char) c),
                    new SourceRange(sLine, sCol, line, col + 1), LanguageId.JAVA));
            if (c == '\n') { line++; col = 0; } else { col++; }
            i++;
        }

        out.add(LexerUtils.eof(line, col, LanguageId.JAVA));
        return out;
    }

    @Override
    public LanguageId language() { return LanguageId.JAVA; }

    private static boolean isOpStart(char c) {
        return "+-*/%=<>!&|^~?:.,;()[]{}@$".indexOf(c) >= 0;
    }

    private static boolean isOpPart(char c) {
        return "+-*/%=<>!&|^~?:.,;()[]{}@$".indexOf(c) >= 0;
    }
}
