package com.zerostudio.language.parser;

import com.zerostudio.language.jni.TreeSitterAvailability;
import com.zerostudio.language.jni.TreeSitterCNativeParser;
import com.zerostudio.language.lexer.CppLexer;
import com.zerostudio.language.lexer.Lexer;
import com.zerostudio.language.lexer.LexerRegistry;
import com.zerostudio.language.lexer.Token;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.Symbol;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * C++ language parser.
 *
 * <p>Mirrors {@link CParser}: Tree-Sitter first, token-based fallback always
 * available. The C++ high-fidelity path additionally understands namespaces,
 * classes, templates, and the C++-specific declarator grammar.
 */
public final class CppParser implements Parser {

    private final Lexer lexer = LexerRegistry.get(LanguageId.CPP);
    private final TreeSitterCNativeParser nativeParser =
            new TreeSitterCNativeParser(LanguageId.CPP);

    @Override
    public ParsedFile parse(File file) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()));
        return parse(file.getAbsolutePath(), text);
    }

    @Override
    public ParsedFile parse(String path, String text) {
        // High-fidelity path: try Tree-Sitter first.
        if (TreeSitterAvailability.isAvailable()) {
            ParsedFile nativeResult = nativeParser.parse(path, text);
            if (nativeResult != null) return nativeResult;
            // Fall through to token-based path below.
        }
        // Token-based fallback path.
        List<Token> tokens = lexer.tokenize(text);
        List<Symbol> symbols = new ArrayList<>();
        List<Reference> refs = new ArrayList<>();
        String err = null;
        try {
            new CppDeclExtractor(path, text, tokens, symbols, refs).extract();
        } catch (RuntimeException ex) {
            err = "C++ parse failure: " + ex.getMessage();
        }
        return new ParsedFile(path, LanguageId.CPP, System.currentTimeMillis(),
                text, symbols, refs, tokens, err);
    }

    @Override
    public LanguageId language() { return LanguageId.CPP; }
}
