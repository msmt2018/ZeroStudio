package com.zerostudio.language.parser;

import com.zerostudio.language.lexer.Token;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * C++ declaration extractor. C++ has nested scopes (namespaces, classes,
 * templates), so we keep a scope stack as we descend.
 */
final class CppDeclExtractor {

    private final String path;
    private final String text;
    private final List<Token> tokens;
    private final List<Symbol> symbols;
    private final List<Reference> refs;
    private final Deque<String> scopeStack = new ArrayDeque<>();
    private int p;

    CppDeclExtractor(String path, String text, List<Token> tokens,
                     List<Symbol> symbols, List<Reference> refs) {
        this.path = path;
        this.text = text;
        this.tokens = tokens;
        this.symbols = symbols;
        this.refs = refs;
    }

    void extract() {
        p = 0;
        while (p < tokens.size()) {
            Token t = tokens.get(p);
            if (t.kind == Token.Kind.PREPROCESSOR) { p++; continue; }
            if (t.kind == Token.Kind.KEYWORD) {
                switch (t.text) {
                    case "namespace":
                        tryNamespace();
                        continue;
                    case "class":
                    case "struct":
                        tryClassLike(t.text);
                        continue;
                    case "template":
                        skipTemplate();
                        continue;
                    case "enum":
                        tryEnum();
                        continue;
                    case "typedef":
                        tryTypedef();
                        continue;
                    case "using":
                        tryUsing();
                        continue;
                    default:
                        tryFunction();
                        continue;
                }
            }
            if (t.kind == Token.Kind.IDENTIFIER) {
                tryFunction();
                continue;
            }
            p++;
        }
    }

    private void tryNamespace() {
        // namespace foo { ... }  or  namespace { ... }
        int save = p;
        p++;
        skipWS();
        String name = null;
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.IDENTIFIER) {
            name = tokens.get(p).text;
            symbols.add(new Symbol(name, name, SymbolKind.NAMESPACE, currentScope(),
                    path, tokens.get(p).range, LanguageId.CPP));
            p++;
        }
        skipWS();
        if (p < tokens.size() && isToken("{")) {
            int open = p;
            int close = matchBrace(open);
            if (name != null) scopeStack.push(name);
            // Recurse into body
            p = open + 1;
            // inner declarations until matching '}'
            int saved = p;
            extract();
            // We will resume after the close brace below.
            if (name != null) scopeStack.pop();
            p = close + 1;
            return;
        }
        p = save + 1;
    }

    private void tryClassLike(String kw) {
        int save = p;
        p++;
        skipWS();
        // optional 'final' / alignment / __attribute__
        while (p < tokens.size() && tokens.get(p).kind == Token.Kind.IDENTIFIER
                && (tokens.get(p).text.equals("final")
                        || tokens.get(p).text.startsWith("__"))) {
            p++; skipWS();
        }
        if (p >= tokens.size() || tokens.get(p).kind != Token.Kind.IDENTIFIER) {
            p = save + 1;
            return;
        }
        Token nameTok = tokens.get(p);
        SymbolKind kind = kw.equals("struct") ? SymbolKind.STRUCT : SymbolKind.CLASS;
        symbols.add(new Symbol(nameTok.text, nameTok.text, kind, currentScope(),
                path, nameTok.range, LanguageId.CPP));
        p++;
        skipWS();
        // optional inheritance ': Base1, Base2'
        if (p < tokens.size() && isToken(":")) {
            p++;
            while (p < tokens.size() && !isToken("{")) {
                Token tk = tokens.get(p);
                if (tk.kind == Token.Kind.IDENTIFIER) {
                    refs.add(new Reference(tk.text, tk.range,
                            Reference.ReferenceKind.EXTENDS, currentScope(),
                            path, LanguageId.CPP));
                }
                p++;
            }
        }
        skipWS();
        if (p < tokens.size() && isToken("{")) {
            int open = p;
            int close = matchBrace(open);
            scopeStack.push(nameTok.text);
            p = open + 1;
            extractClassMembers(close);
            scopeStack.pop();
            p = close + 1;
            return;
        }
        p = save + 1;
    }

    private void extractClassMembers(int endBrace) {
        // Walk tokens until we see '}' at the same depth.
        while (p < endBrace) {
            Token t = tokens.get(p);
            if (t.kind == Token.Kind.PREPROCESSOR) { p++; continue; }
            if (t.kind == Token.Kind.KEYWORD) {
                switch (t.text) {
                    case "public":
                    case "private":
                    case "protected":
                    case "static":
                    case "virtual":
                    case "explicit":
                    case "inline":
                    case "constexpr":
                    case "friend":
                    case "mutable":
                    case "const":
                    case "noexcept":
                    case "override":
                    case "final":
                        p++;
                        continue;
                    case "class":
                    case "struct":
                        tryClassLike(t.text);
                        continue;
                    case "enum":
                        tryEnum();
                        continue;
                    case "template":
                        skipTemplate();
                        continue;
                    case "typedef":
                        tryTypedef();
                        continue;
                    default:
                        tryFunction();
                        continue;
                }
            } else if (t.kind == Token.Kind.IDENTIFIER) {
                // Could be a constructor (NAME(...)) or a method.
                tryFunction();
                continue;
            } else {
                p++;
            }
        }
    }

    private void skipTemplate() {
        // template < ... > declaration ;
        int save = p;
        p++;
        skipWS();
        if (p < tokens.size() && isToken("<")) {
            p = matchAngle(p) + 1;
        }
        // continue with whatever declaration follows
    }

    private void tryEnum() {
        int save = p;
        p++;
        skipWS();
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.IDENTIFIER) {
            Token name = tokens.get(p);
            symbols.add(new Symbol(name.text, name.text, SymbolKind.ENUM,
                    currentScope(), path, name.range, LanguageId.CPP));
            p++;
            skipWS();
            if (p < tokens.size() && isToken("{")) {
                int close = matchBrace(p);
                p = close + 1;
            }
            while (p < tokens.size() && isToken(";")) p++;
            return;
        }
        p = save + 1;
    }

    private void tryTypedef() {
        int save = p;
        p++;
        skipWS();
        int lastIdent = -1;
        while (p < tokens.size() && !isToken(";")) {
            if (tokens.get(p).kind == Token.Kind.IDENTIFIER) lastIdent = p;
            p++;
        }
        if (lastIdent >= 0 && p < tokens.size()) {
            Token name = tokens.get(lastIdent);
            symbols.add(new Symbol(name.text, name.text, SymbolKind.TYPE_PARAMETER,
                    currentScope(), path, name.range, LanguageId.CPP));
            p++;
            return;
        }
        p = save + 1;
    }

    private void tryUsing() {
        int save = p;
        p++;
        skipWS();
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.IDENTIFIER
                && tokens.get(p).text.equals("namespace")) {
            // using namespace foo;
            p++;
            skipWS();
            while (p < tokens.size() && !isToken(";")) {
                Token tk = tokens.get(p);
                if (tk.kind == Token.Kind.IDENTIFIER) {
                    refs.add(new Reference(tk.text, tk.range,
                            Reference.ReferenceKind.IMPORT, currentScope(),
                            path, LanguageId.CPP));
                }
                p++;
            }
            if (p < tokens.size()) p++;
            return;
        }
        p = save + 1;
    }

    private void tryFunction() {
        int save = p;
        int nameIdx = -1;
        int scan = p;
        int safety = 0;
        int parenStart = -1;
        while (scan < tokens.size() && safety++ < 4096) {
            Token tk = tokens.get(scan);
            if (tk.kind == Token.Kind.OPERATOR) {
                if (tk.text.equals("(")) { parenStart = scan; break; }
                if (tk.text.equals(";") || tk.text.equals("=")
                        || tk.text.equals("{") || tk.text.equals("}")) {
                    p = save + 1;
                    return;
                }
            }
            if (tk.kind == Token.Kind.IDENTIFIER) nameIdx = scan;
            scan++;
        }
        if (parenStart < 0 || nameIdx < 0) { p = save + 1; return; }
        int parenEnd = matchMatching(parenStart, "(", ")");
        if (parenEnd < 0) { p = save + 1; return; }
        int bodyStart = parenEnd + 1;
        while (bodyStart < tokens.size()
                && (tokens.get(bodyStart).kind == Token.Kind.WHITESPACE
                        || tokens.get(bodyStart).kind == Token.Kind.NEWLINE
                        || tokens.get(bodyStart).kind == Token.Kind.COMMENT)) {
            bodyStart++;
        }
        boolean isBody = bodyStart < tokens.size()
                && tokens.get(bodyStart).kind == Token.Kind.OPERATOR
                && tokens.get(bodyStart).text.equals("{");
        Token nameTok = tokens.get(nameIdx);
        if (isBody) {
            SourceRange fnRange = new SourceRange(
                    tokens.get(save).range.start,
                    tokens.get(bodyStart).range.start);
            symbols.add(new Symbol(nameTok.text, nameTok.text,
                    SymbolKind.METHOD, currentScope(), path, fnRange,
                    LanguageId.CPP));
            int bodyEnd = matchBrace(bodyStart);
            for (int q = bodyStart + 1; q < bodyEnd && q < tokens.size(); q++) {
                Token tk = tokens.get(q);
                if (tk.kind == Token.Kind.IDENTIFIER) {
                    refs.add(new Reference(tk.text, tk.range,
                            Reference.ReferenceKind.READ, nameTok.text,
                            path, LanguageId.CPP));
                } else if (tk.kind == Token.Kind.OPERATOR
                        && tk.text.equals("(") && q > 0
                        && tokens.get(q - 1).kind == Token.Kind.IDENTIFIER) {
                    Token callName = tokens.get(q - 1);
                    refs.add(new Reference(callName.text, callName.range,
                            Reference.ReferenceKind.CALL, nameTok.text,
                            path, LanguageId.CPP));
                }
            }
            p = bodyEnd + 1;
        } else {
            // declaration without body — just skip the line
            while (p < tokens.size() && !isToken(";")) p++;
            if (p < tokens.size()) p++;
        }
    }

    private void skipWS() {
        while (p < tokens.size()
                && (tokens.get(p).kind == Token.Kind.WHITESPACE
                        || tokens.get(p).kind == Token.Kind.NEWLINE
                        || tokens.get(p).kind == Token.Kind.COMMENT)) {
            p++;
        }
    }

    private int matchBrace(int open) {
        return matchMatching(open, "{", "}");
    }

    private int matchAngle(int open) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            Token tk = tokens.get(i);
            if (tk.kind == Token.Kind.OPERATOR) {
                if (tk.text.equals("<")) depth++;
                else if (tk.text.equals(">")) {
                    depth--;
                    if (depth == 0) return i;
                }
                if (tk.text.equals(";")) return -1; // not a template arg list
            }
        }
        return -1;
    }

    private int matchMatching(int start, String open, String close) {
        int depth = 0;
        for (int i = start; i < tokens.size(); i++) {
            Token tk = tokens.get(i);
            if (tk.kind == Token.Kind.OPERATOR) {
                if (tk.text.equals(open)) depth++;
                else if (tk.text.equals(close)) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private boolean isToken(String s) {
        return p < tokens.size()
                && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals(s);
    }

    private String currentScope() {
        return String.join("::", scopeStack.descendingIterator());
    }
}
