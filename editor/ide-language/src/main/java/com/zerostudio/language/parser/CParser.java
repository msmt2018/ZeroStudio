package com.zerostudio.language.parser;

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
 * <p>Uses the in-module lexer and declaration extractor. This implementation
 * does not depend on an optional JNI parser, so it remains available on every
 * supported runtime and in builds where native parser libraries are absent.
 *
 * <p>The extractor covers top-level function definitions, struct / union /
 * enum declarations, basic typedefs, and call references.
 */
public final class CParser implements Parser {

    private final Lexer lexer = LexerRegistry.get(LanguageId.C);

    @Override
    public ParsedFile parse(File file) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()));
        return parse(file.getAbsolutePath(), text);
    }

    @Override
    public ParsedFile parse(String path, String text) {
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
