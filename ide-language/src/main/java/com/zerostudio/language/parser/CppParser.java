package com.zerostudio.language.parser;

import com.zerostudio.language.lexer.CppLexer;
import com.zerostudio.language.lexer.Lexer;
import com.zerostudio.language.lexer.LexerRegistry;
import com.zerostudio.language.lexer.Token;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * C++ parser. Same token-based approach as {@link CParser} with C++ keyword
 * awareness. Extracts namespaces, classes, methods, and top-level free
 * functions.
 */
public final class CppParser implements Parser {

    private final Lexer lexer = LexerRegistry.get(LanguageId.CPP);

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
        new CppDeclExtractor(path, text, tokens, symbols, refs).extract();
        return new ParsedFile(path, LanguageId.CPP, System.currentTimeMillis(),
                text, symbols, refs, tokens, null);
    }

    @Override
    public LanguageId language() { return LanguageId.CPP; }
}
