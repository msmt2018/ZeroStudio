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
 * Kotlin declaration extractor. Walks the token stream looking for
 * top-level {@code fun}, {@code class}, {@code object}, {@code interface},
 * nested class bodies, properties, etc.
 */
final class KotlinDeclExtractor {

    private final String path;
    private final List<Token> tokens;
    private final List<Symbol> symbols;
    private final List<Reference> refs;
    private final Deque<String> classStack = new ArrayDeque<>();
    private int p;

    KotlinDeclExtractor(String path, List<Token> tokens,
                        List<Symbol> symbols, List<Reference> refs) {
        this.path = path;
        this.tokens = tokens;
        this.symbols = symbols;
        this.refs = refs;
    }

    void extract() {
        p = 0;
        while (p < tokens.size()) {
            Token t = tokens.get(p);
            if (t.kind == Token.Kind.ANNOTATION) { p++; continue; }
            if (t.kind == Token.Kind.KEYWORD) {
                switch (t.text) {
                    case "package":
                        // skip to newline / ;
                        p++;
                        while (p < tokens.size() && tokens.get(p).kind != Token.Kind.NEWLINE
                                && tokens.get(p).kind != Token.Kind.OPERATOR
                                && tokens.get(p).kind != Token.Kind.EOF) {
                            if (tokens.get(p).kind == Token.Kind.IDENTIFIER) {
                                refs.add(new Reference(tokens.get(p).text,
                                        tokens.get(p).range,
                                        Reference.ReferenceKind.IMPORT, null, path,
                                        LanguageId.KOTLIN));
                            }
                            p++;
                        }
                        continue;
                    case "import":
                        p++;
                        while (p < tokens.size() && tokens.get(p).kind != Token.Kind.NEWLINE
                                && tokens.get(p).kind != Token.Kind.EOF) {
                            if (tokens.get(p).kind == Token.Kind.IDENTIFIER) {
                                refs.add(new Reference(tokens.get(p).text,
                                        tokens.get(p).range,
                                        Reference.ReferenceKind.IMPORT, null, path,
                                        LanguageId.KOTLIN));
                            }
                            p++;
                        }
                        continue;
                    case "class":
                    case "interface":
                        tryClass(t.text.equals("interface"));
                        continue;
                    case "object":
                        tryObject();
                        continue;
                    case "fun":
                        tryFun();
                        continue;
                    case "val":
                    case "var":
                        tryProperty();
                        continue;
                    case "enum":
                        tryEnum();
                        continue;
                    default:
                        p++;
                        continue;
                }
            }
            p++;
        }
    }

    private void tryClass(boolean isInterface) {
        int save = p;
        p++;
        skipWS();
        // optional annotations are already skipped
        if (p >= tokens.size() || tokens.get(p).kind != Token.Kind.IDENTIFIER) {
            p = save + 1;
            return;
        }
        Token name = tokens.get(p);
        SymbolKind kind = isInterface ? SymbolKind.INTERFACE : SymbolKind.CLASS;
        symbols.add(new Symbol(name.text, name.text, kind, currentClass(), path,
                name.range, LanguageId.KOTLIN));
        p++;
        skipWS();
        // optional generic <...>
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals("<")) {
            int close = matchAngle(p);
            if (close > 0) p = close + 1;
        }
        skipWS();
        // optional primary constructor (...)
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals("(")) {
            int close = matchMatching(p, "(", ")");
            if (close > 0) p = close + 1;
        }
        skipWS();
        // optional : SuperType, Interface1, ...
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals(":")) {
            p++;
            while (p < tokens.size()
                    && !(tokens.get(p).kind == Token.Kind.OPERATOR
                            && (tokens.get(p).text.equals("{")
                                    || tokens.get(p).text.equals(";")))) {
                Token tk = tokens.get(p);
                if (tk.kind == Token.Kind.IDENTIFIER) {
                    refs.add(new Reference(tk.text, tk.range,
                            Reference.ReferenceKind.EXTENDS, currentClass(),
                            path, LanguageId.KOTLIN));
                }
                p++;
            }
        }
        skipWS();
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals("{")) {
            int open = p;
            int close = matchBrace(open);
            classStack.push(name.text);
            p = open + 1;
            extractClassMembers(close);
            classStack.pop();
            p = close + 1;
            return;
        }
        p = save + 1;
    }

    private void tryObject() {
        int save = p;
        p++;
        skipWS();
        if (p >= tokens.size()) { p = save + 1; return; }
        // anonymous object:  object : Foo { ... }
        if (tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals(":")) {
            p++;
            skipWS();
            while (p < tokens.size()
                    && !(tokens.get(p).kind == Token.Kind.OPERATOR
                            && tokens.get(p).text.equals("{"))) {
                Token tk = tokens.get(p);
                if (tk.kind == Token.Kind.IDENTIFIER) {
                    refs.add(new Reference(tk.text, tk.range,
                            Reference.ReferenceKind.EXTENDS, currentClass(),
                            path, LanguageId.KOTLIN));
                }
                p++;
            }
        }
        String name = null;
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.IDENTIFIER) {
            name = tokens.get(p).text;
            symbols.add(new Symbol(name, name, SymbolKind.CLASS, currentClass(),
                    path, tokens.get(p).range, LanguageId.KOTLIN));
            p++;
        }
        skipWS();
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals("{")) {
            int open = p;
            int close = matchBrace(open);
            if (name != null) classStack.push(name);
            p = open + 1;
            extractClassMembers(close);
            if (name != null) classStack.pop();
            p = close + 1;
            return;
        }
        p = save + 1;
    }

    private void tryEnum() {
        int save = p;
        p++;
        skipWS();
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.IDENTIFIER) {
            Token name = tokens.get(p);
            symbols.add(new Symbol(name.text, name.text, SymbolKind.ENUM,
                    currentClass(), path, name.range, LanguageId.KOTLIN));
            p++;
            skipWS();
            if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                    && tokens.get(p).text.equals("{")) {
                int close = matchBrace(p);
                p = close + 1;
            }
            return;
        }
        p = save + 1;
    }

    private void tryFun() {
        int save = p;
        p++;
        skipWS();
        if (p >= tokens.size() || tokens.get(p).kind != Token.Kind.IDENTIFIER) {
            p = save + 1;
            return;
        }
        Token name = tokens.get(p);
        SourceRange range = name.range;
        symbols.add(new Symbol(name.text, name.text, SymbolKind.METHOD,
                currentClass(), path, range, LanguageId.KOTLIN));
        p++;
        skipWS();
        // optional generic <...>
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals("<")) {
            int close = matchAngle(p);
            if (close > 0) p = close + 1;
        }
        skipWS();
        // parameter list (...)
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals("(")) {
            int close = matchMatching(p, "(", ")");
            if (close > 0) p = close + 1;
        }
        skipWS();
        // optional : ReturnType
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals(":")) {
            p++;
            while (p < tokens.size()
                    && !(tokens.get(p).kind == Token.Kind.OPERATOR
                            && (tokens.get(p).text.equals("{")
                                    || tokens.get(p).text.equals(";")))) {
                p++;
            }
        }
        skipWS();
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals("=")) {
            // expression body
            p++;
            int depth = 0;
            while (p < tokens.size()) {
                Token tk = tokens.get(p);
                if (tk.kind == Token.Kind.OPERATOR) {
                    if (tk.text.equals("(") || tk.text.equals("{")) depth++;
                    else if (tk.text.equals(")") || tk.text.equals("}")) depth--;
                }
                if (depth == 0
                        && (tk.kind == Token.Kind.NEWLINE
                                || (tk.kind == Token.Kind.OPERATOR
                                        && tk.text.equals(";")))) {
                    p++;
                    return;
                }
                if (tk.kind == Token.Kind.IDENTIFIER) {
                    refs.add(new Reference(tk.text, tk.range,
                            Reference.ReferenceKind.READ, name.text,
                            path, LanguageId.KOTLIN));
                }
                p++;
            }
            return;
        }
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals("{")) {
            int open = p;
            int close = matchBrace(open);
            for (int q = open + 1; q < close && q < tokens.size(); q++) {
                Token tk = tokens.get(q);
                if (tk.kind == Token.Kind.IDENTIFIER) {
                    refs.add(new Reference(tk.text, tk.range,
                            Reference.ReferenceKind.READ, name.text,
                            path, LanguageId.KOTLIN));
                } else if (tk.kind == Token.Kind.OPERATOR
                        && tk.text.equals("(") && q > 0
                        && tokens.get(q - 1).kind == Token.Kind.IDENTIFIER) {
                    Token callName = tokens.get(q - 1);
                    refs.add(new Reference(callName.text, callName.range,
                            Reference.ReferenceKind.CALL, name.text,
                            path, LanguageId.KOTLIN));
                }
            }
            p = close + 1;
            return;
        }
        p = save + 1;
    }

    private void tryProperty() {
        int save = p;
        boolean isVal = tokens.get(p).text.equals("val");
        p++;
        skipWS();
        if (p >= tokens.size() || tokens.get(p).kind != Token.Kind.IDENTIFIER) {
            p = save + 1;
            return;
        }
        Token name = tokens.get(p);
        SymbolKind kind = classStack.isEmpty()
                ? (isVal ? SymbolKind.FIELD : SymbolKind.FIELD)
                : SymbolKind.FIELD;
        symbols.add(new Symbol(name.text, name.text, kind, currentClass(),
                path, name.range, LanguageId.KOTLIN));
        p++;
        skipWS();
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals(":")) {
            // type
            p++;
            while (p < tokens.size()
                    && !(tokens.get(p).kind == Token.Kind.OPERATOR
                            && (tokens.get(p).text.equals("=")
                                    || tokens.get(p).text.equals(";")))) {
                p++;
            }
        }
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals("=")) {
            p++;
            int depth = 0;
            while (p < tokens.size()) {
                Token tk = tokens.get(p);
                if (tk.kind == Token.Kind.OPERATOR) {
                    if (tk.text.equals("(") || tk.text.equals("{")) depth++;
                    else if (tk.text.equals(")") || tk.text.equals("}")) depth--;
                }
                if (depth == 0
                        && (tk.kind == Token.Kind.NEWLINE
                                || (tk.kind == Token.Kind.OPERATOR
                                        && tk.text.equals(";")))) {
                    p++;
                    return;
                }
                if (tk.kind == Token.Kind.IDENTIFIER) {
                    refs.add(new Reference(tk.text, tk.range,
                            Reference.ReferenceKind.READ, currentClass(),
                            path, LanguageId.KOTLIN));
                }
                p++;
            }
            return;
        }
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals(";")) p++;
    }

    private void extractClassMembers(int endBrace) {
        while (p < endBrace) {
            Token t = tokens.get(p);
            if (t.kind == Token.Kind.ANNOTATION) { p++; continue; }
            if (t.kind == Token.Kind.KEYWORD) {
                switch (t.text) {
                    case "class":
                    case "interface":
                        tryClass(t.text.equals("interface"));
                        continue;
                    case "object":
                        tryObject();
                        continue;
                    case "fun":
                        tryFun();
                        continue;
                    case "val":
                    case "var":
                        tryProperty();
                        continue;
                    case "enum":
                        tryEnum();
                        continue;
                    case "private":
                    case "public":
                    case "internal":
                    case "protected":
                    case "open":
                    case "final":
                    case "abstract":
                    case "override":
                    case "lateinit":
                    case "const":
                    case "companion":
                    case "suspend":
                    case "inline":
                    case "noinline":
                    case "crossinline":
                    case "reified":
                    case "operator":
                    case "infix":
                    case "tailrec":
                    case "external":
                        p++;
                        continue;
                    default:
                        p++;
                        continue;
                }
            }
            p++;
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

    private String currentClass() {
        return String.join(".", classStack.descendingIterator());
    }
}
