package com.zerostudio.language.lexer;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hand-written C lexer. Handles preprocessor lines, line/block comments,
 * string/char literals, numbers, identifiers, and operators.
 *
 * <p>Used as a fallback when Tree-Sitter is unavailable and as a reference
 * implementation for tests.
 */
public final class CLexer implements Lexer {

    private static final Set<String> C_KEYWORDS = Set.of(
            "auto", "break", "case", "char", "const", "continue", "default",
            "do", "double", "else", "enum", "extern", "float", "for", "goto",
            "if", "inline", "int", "long", "register", "restrict", "return",
            "short", "signed", "sizeof", "static", "struct", "switch",
            "typedef", "union", "unsigned", "void", "volatile", "while",
            "_Bool", "_Complex", "_Imaginary", "_Atomic", "_Generic",
            "_Noreturn", "_Static_assert", "_Thread_local"
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
                        new SourceRange(sLine, sCol, line, col), LanguageId.C));
                continue;
            }

            if (c == '\n') {
                out.add(new Token(Token.Kind.NEWLINE, "\n",
                        new SourceRange(line, col, line, col), LanguageId.C));
                i++; line++; col = 0;
                continue;
            }

            // preprocessor
            if (c == '#') {
                int sLine = line, sCol = col;
                int start = i;
                while (i < len && text.charAt(i) != '\n') {
                    // skip line-continuation backslash
                    if (text.charAt(i) == '\\' && i + 1 < len
                            && text.charAt(i + 1) == '\n') {
                        i += 2; line++; col = 0;
                    } else { i++; col++; }
                }
                out.add(new Token(Token.Kind.PREPROCESSOR, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.C));
                continue;
            }

            // comments
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '/') {
                int sLine = line, sCol = col;
                while (i < len && text.charAt(i) != '\n') { i++; col++; }
                out.add(new Token(Token.Kind.COMMENT, text.substring(sCol, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.C));
                continue;
            }
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '*') {
                int sLine = line, sCol = col;
                i += 2; col += 2;
                while (i + 1 < len && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
                    if (text.charAt(i) == '\n') { line++; col = 0; i++; }
                    else { i++; col++; }
                }
                if (i + 1 < len) { i += 2; col += 2; }
                out.add(new Token(Token.Kind.COMMENT, text.substring(sCol, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.C));
                continue;
            }

            // identifier / keyword
            if (LexerUtils.isCIdentifierStart(c)) {
                int sLine = line, sCol = col;
                int start = i;
                while (i < len && LexerUtils.isCIdentifierPart(text.charAt(i))) {
                    i++; col++;
                }
                String word = text.substring(start, i);
                Token.Kind k = C_KEYWORDS.contains(word)
                        ? Token.Kind.KEYWORD : Token.Kind.IDENTIFIER;
                out.add(new Token(k, word,
                        new SourceRange(sLine, sCol, line, col), LanguageId.C));
                continue;
            }

            // numbers
            if (LexerUtils.isDigit(c) || (c == '.' && i + 1 < len
                    && LexerUtils.isDigit(text.charAt(i + 1)))) {
                int sLine = line, sCol = col;
                int start = i;
                while (i < len) {
                    int d = text.charAt(i);
                    if (LexerUtils.isDigit(d) || d == '.' || d == 'e' || d == 'E'
                            || d == 'x' || d == 'X' || d == 'b' || d == 'B'
                            || d == 'f' || d == 'F' || d == 'l' || d == 'L'
                            || d == 'u' || d == 'U' || d == '\'' || d == '_') {
                        i++; col++;
                    } else {
                        break;
                    }
                }
                out.add(new Token(Token.Kind.NUMBER, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.C));
                continue;
            }

            // string / char
            if (c == '"' || c == '\'') {
                char quote = (char) c;
                int sLine = line, sCol = col;
                int start = i;
                i++; col++;
                while (i < len && text.charAt(i) != quote) {
                    if (text.charAt(i) == '\\' && i + 1 < len) { i += 2; col += 2; }
                    else if (text.charAt(i) == '\n') { line++; col = 0; i++; }
                    else { i++; col++; }
                }
                if (i < len) { i++; col++; }
                Token.Kind k = (quote == '"') ? Token.Kind.STRING : Token.Kind.CHAR;
                out.add(new Token(k, text.substring(start, i),
                        new SourceRange(sLine, sCol, line, col), LanguageId.C));
                continue;
            }

            // operators
            if (isOpStart((char) c)) {
                int sLine = line, sCol = col;
                int start = i;
                String op = readOperator(text, i);
                i += op.length();
                for (int k = 0; k < op.length(); k++) {
                    if (op.charAt(k) == '\n') { line++; col = 0; }
                    else { col++; }
                }
                out.add(new Token(Token.Kind.OPERATOR, op,
                        new SourceRange(sLine, sCol, line, col), LanguageId.C));
                continue;
            }

            int sLine = line, sCol = col;
            out.add(new Token(Token.Kind.UNKNOWN, String.valueOf((char) c),
                    new SourceRange(sLine, sCol, line, col + 1), LanguageId.C));
            col++; i++;
        }
        out.add(LexerUtils.eof(line, col, LanguageId.C));
        return out;
    }

    @Override
    public LanguageId language() { return LanguageId.C; }

    private static boolean isOpStart(char c) {
        return "+-*/%=<>!&|^~?:.,;()[]{}#@$".indexOf(c) >= 0;
    }

    private static String readOperator(String text, int start) {
        int i = start;
        int len = text.length();
        // 3-char operators (C++)
        if (i + 2 < len) {
            String t3 = text.substring(i, i + 3);
            if (t3.equals("<<=") || t3.equals(">>=") || t3.equals("->*")
                    || t3.equals("...")) {
                return t3;
            }
        }
        // 2-char operators
        if (i + 1 < len) {
            String t2 = text.substring(i, i + 2);
            if (t2.equals("==") || t2.equals("!=") || t2.equals("<=")
                    || t2.equals(">=") || t2.equals("&&") || t2.equals("||")
                    || t2.equals("++") || t2.equals("--") || t2.equals("+=")
                    || t2.equals("-=") || t2.equals("*=") || t2.equals("/=")
                    || t2.equals("%=") || t2.equals("&=") || t2.equals("|=")
                    || t2.equals("^=") || t2.equals("<<") || t2.equals(">>")
                    || t2.equals("->") || t2.equals("::")) {
                return t2;
            }
        }
        return text.substring(i, i + 1);
    }
}
