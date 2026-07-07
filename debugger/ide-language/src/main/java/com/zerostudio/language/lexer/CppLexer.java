package com.zerostudio.language.lexer;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hand-written C++ lexer. Same shape as {@link CLexer} with extra C++ keywords
 * (class, template, namespace, virtual, ...).
 */
public final class CppLexer implements Lexer {

    private static final Set<String> CPP_KEYWORDS;
    static {
        java.util.Set<String> set = new java.util.HashSet<>();
        set.addAll(java.util.Arrays.asList(
                "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand",
                "bitor", "bool", "break", "case", "catch", "char", "char8_t",
                "char16_t", "char32_t", "class", "compl", "concept", "const",
                "consteval", "constexpr", "constinit", "const_cast", "continue",
                "co_await", "co_return", "co_yield", "decltype", "default",
                "delete", "do", "double", "dynamic_cast", "else", "enum",
                "explicit", "export", "extern", "false", "float", "for",
                "friend", "goto", "if", "inline", "int", "long", "mutable",
                "namespace", "new", "noexcept", "not", "not_eq", "nullptr",
                "operator", "or", "or_eq", "private", "protected", "public",
                "register", "reinterpret_cast", "requires", "return", "short",
                "signed", "sizeof", "static", "static_assert", "static_cast",
                "struct", "switch", "template", "this", "thread_local", "throw",
                "true", "try", "typedef", "typeid", "typename", "union",
                "unsigned", "using", "virtual", "void", "volatile", "wchar_t",
                "while", "xor", "xor_eq"
        ));
        CPP_KEYWORDS = set;
    }

    @Override
    public List<Token> tokenize(String text) {
        // C++ is a superset of C for lexer purposes; the major difference is
        // the keyword set. For now delegate to CLexer and relabel keywords
        // by re-scanning.
        CLexer base = new CLexer();
        List<Token> baseTokens = base.tokenize(text);
        List<Token> out = new ArrayList<>(baseTokens.size());
        for (Token t : baseTokens) {
            if (t.kind == Token.Kind.IDENTIFIER && CPP_KEYWORDS.contains(t.text)) {
                out.add(new Token(Token.Kind.KEYWORD, t.text, t.range, LanguageId.CPP));
            } else {
                out.add(new Token(t.kind, t.text, t.range, LanguageId.CPP));
            }
        }
        return out;
    }

    @Override
    public LanguageId language() { return LanguageId.CPP; }
}
