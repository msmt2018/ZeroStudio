package com.zerostudio.language.parser;

import com.zerostudio.language.jni.TreeSitterAvailability;
import com.zerostudio.language.jni.TreeSitterCNativeParser;
import com.zerostudio.language.lexer.CLexer;
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
 * C language parser.
 *
 * <p>This parser prefers the high-fidelity Tree-Sitter path when the native
 * libraries are loadable, and falls back to the hand-written token-based
 * extractor otherwise. The fallback path is always safe to use.
 *
 * <p>The high-fidelity path understands:
 * <ul>
 *   <li>typedefs, function pointers, complex initializers</li>
 *   <li>macros and preprocessor expansion</li>
 *   <li>the full C11/C17 grammar</li>
 * </ul>
 *
 * <p>The fallback path (hand-written) covers:
 * <ul>
 *   <li>top-level function definitions</li>
 *   <li>struct / union / enum declarations</li>
 *   <li>basic typedefs and call references</li>
 * </ul>
 */
public final class CParser implements Parser {

    private final Lexer lexer = LexerRegistry.get(LanguageId.C);
    private final TreeSitterCNativeParser nativeParser =
            new TreeSitterCNativeParser(LanguageId.C);

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
            new CDeclExtractor(path, text, tokens, symbols, refs).extract();
        } catch (RuntimeException ex) {
            err = "C parse failure: " + ex.getMessage();
        }
        return new ParsedFile(path, LanguageId.C, System.currentTimeMillis(),
                text, symbols, refs, tokens, err);
    }

    @Override
    public LanguageId language() { return LanguageId.C; }
}
