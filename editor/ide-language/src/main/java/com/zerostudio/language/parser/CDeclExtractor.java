package com.zerostudio.language.parser;

import com.zerostudio.language.lexer.Token;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;

import java.util.List;

/**
 * Token-stream based C declaration extractor. Walks the flat token list
 * looking for top-level {@code TYPE NAME ( ... )} function definitions and
 * {@code struct NAME { ... }} declarations. Anything more elaborate (e.g.
 * function pointers, complex initializers) is left for Tree-Sitter to handle
 * when the high-fidelity parser is wired up.
 */
final class CDeclExtractor {

    private final String path;
    private final String text;
    private final List<Token> tokens;
    private final List<Symbol> symbols;
    private final List<Reference> refs;
    private int p;

    CDeclExtractor(String path, String text, List<Token> tokens,
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
            if (t.kind == Token.Kind.PREPROCESSOR) {
                p++;
                continue;
            }
            if (t.kind == Token.Kind.KEYWORD) {
                switch (t.text) {
                    case "struct":
                    case "union":
                    case "enum":
                        tryStructOrEnum(t.text);
                        continue;
                    case "typedef":
                        tryTypedef();
                        continue;
                    default:
                        // Possibly a function definition.
                        tryFunction();
                        continue;
                }
            }
            if (t.kind == Token.Kind.IDENTIFIER) {
                // A free identifier at top level: either a global variable
                // or a K&R function with implicit-int return.
                tryFunction();
                continue;
            }
            p++;
        }
    }

    private void tryStructOrEnum(String kw) {
        // struct NAME { ... } *ptr ;
        // struct NAME ;
        int save = p;
        p++;
        skipWhitespaceAndNewlines();
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.IDENTIFIER) {
            Token name = tokens.get(p);
            p++;
            skipWhitespaceAndNewlines();
            SymbolKind kind = kw.equals("enum") ? SymbolKind.ENUM
                    : (kw.equals("union") ? SymbolKind.UNION : SymbolKind.STRUCT);
            symbols.add(new Symbol(name.text, name.text, kind, null, path,
                    name.range, LanguageId.C));
            if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                    && tokens.get(p).text.equals("{")) {
                p = matchBrace(p) + 1;
            }
            while (p < tokens.size()
                    && tokens.get(p).kind == Token.Kind.OPERATOR
                    && tokens.get(p).text.equals(";")) {
                p++;
            }
            return;
        }
        if (p < tokens.size() && tokens.get(p).kind == Token.Kind.OPERATOR
                && tokens.get(p).text.equals("{")) {
            // anonymous
            p = matchBrace(p) + 1;
            while (p < tokens.size()
                    && tokens.get(p).kind == Token.Kind.OPERATOR
                    && tokens.get(p).text.equals(";")) {
                p++;
            }
            return;
        }
        p = save + 1;
    }

    private void tryTypedef() {
        // typedef oldtype newtype ;
        int save = p;
        p++;
        skipWhitespaceAndNewlines();
        // consume old type tokens until identifier at end
        int lastIdent = -1;
        while (p < tokens.size()
                && !(tokens.get(p).kind == Token.Kind.OPERATOR
                        && tokens.get(p).text.equals(";"))) {
            if (tokens.get(p).kind == Token.Kind.IDENTIFIER) lastIdent = p;
            p++;
        }
        if (lastIdent >= 0 && p < tokens.size()) {
            Token name = tokens.get(lastIdent);
            symbols.add(new Symbol(name.text, name.text, SymbolKind.TYPE_PARAMETER,
                    null, path, name.range, LanguageId.C));
            p++;
            return;
        }
        p = save + 1;
    }

    private void tryFunction() {
        // Pattern:  [type-tokens] NAME ( ... ) { body }
        // The first token is the current one. We try to find a (...) followed
        // by a '{' on the same logical line (allowing whitespace/newlines).
        int save = p;
        int nameIdx = -1;
        // find '('
        int parenStart = -1;
        int scan = p;
        int safety = 0;
        while (scan < tokens.size() && safety++ < 4096) {
            Token tk = tokens.get(scan);
            if (tk.kind == Token.Kind.OPERATOR) {
                if (tk.text.equals("(")) { parenStart = scan; break; }
                if (tk.text.equals(";") || tk.text.equals("=")
                        || tk.text.equals("{") || tk.text.equals("}")) {
                    // Not a function
                    p = save + 1;
                    return;
                }
            }
            if (tk.kind == Token.Kind.IDENTIFIER) nameIdx = scan;
            scan++;
        }
        if (parenStart < 0 || nameIdx < 0) {
            p = save + 1;
            return;
        }
        int parenEnd = matchMatching(parenStart, "(", ")");
        if (parenEnd < 0) {
            p = save + 1;
            return;
        }
        // After ')', we need to skip whitespace/newlines then expect '{'.
        int bodyStart = parenEnd + 1;
        while (bodyStart < tokens.size()
                && (tokens.get(bodyStart).kind == Token.Kind.WHITESPACE
                        || tokens.get(bodyStart).kind == Token.Kind.NEWLINE
                        || tokens.get(bodyStart).kind == Token.Kind.COMMENT)) {
            bodyStart++;
        }
        if (bodyStart >= tokens.size()
                || tokens.get(bodyStart).kind != Token.Kind.OPERATOR
                || !tokens.get(bodyStart).text.equals("{")) {
            // declaration, not a function definition
            p = save + 1;
            return;
        }
        Token nameTok = tokens.get(nameIdx);
        SourceRange fnRange = new SourceRange(
                tokens.get(save).range.start,
                tokens.get(bodyStart).range.start);
        symbols.add(new Symbol(nameTok.text, nameTok.text, SymbolKind.FUNCTION,
                null, path, fnRange, LanguageId.C));
        // Walk body for references.
        int bodyEnd = matchMatching(bodyStart, "{", "}");
        for (int q = bodyStart + 1; q < bodyEnd && q < tokens.size(); q++) {
            Token tk = tokens.get(q);
            if (tk.kind == Token.Kind.IDENTIFIER) {
                refs.add(new Reference(tk.text, tk.range,
                        Reference.ReferenceKind.READ, nameTok.text,
                        path, LanguageId.C));
            } else if (tk.kind == Token.Kind.OPERATOR
                    && tk.text.equals("(") && q > 0
                    && tokens.get(q - 1).kind == Token.Kind.IDENTIFIER) {
                Token callName = tokens.get(q - 1);
                refs.add(new Reference(callName.text, callName.range,
                        Reference.ReferenceKind.CALL, nameTok.text,
                        path, LanguageId.C));
            }
        }
        p = bodyEnd + 1;
    }

    private void skipWhitespaceAndNewlines() {
        while (p < tokens.size()
                && (tokens.get(p).kind == Token.Kind.WHITESPACE
                        || tokens.get(p).kind == Token.Kind.NEWLINE
                        || tokens.get(p).kind == Token.Kind.COMMENT)) {
            p++;
        }
    }

    private int matchBrace(int start) {
        return matchMatching(start, "{", "}");
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
}
