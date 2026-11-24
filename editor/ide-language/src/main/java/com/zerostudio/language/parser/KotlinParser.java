package com.zerostudio.language.parser;

import com.zerostudio.language.lexer.KotlinLexer;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Kotlin parser. Token-based extraction of classes, objects, interfaces,
 * top-level functions, properties, and method declarations. The full PSI
 * path uses {@code org.jetbrains.kotlin:kotlin-compiler} and is wired in via
 * the {@link com.zerostudio.language.symbols.KotlinSymbolResolver} when the
 * classpath contains the compiler. This parser is the bootstrap path that
 * always works.
 */
public final class KotlinParser implements Parser {

    private final Lexer lexer = LexerRegistry.get(LanguageId.KOTLIN);

    @Override
    public ParsedFile parse(File file) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()));
        return parse(file.getAbsolutePath(), text);
    }

    @Override
    public ParsedFile parse(String path, String text) {
        List<Token> tokens = lexer.tokenize(text);
        List<Symbol> symbols = new java.util.ArrayList<>();
        List<Reference> refs = new java.util.ArrayList<>();
        new KotlinDeclExtractor(path, tokens, symbols, refs).extract();
        return new ParsedFile(path, LanguageId.KOTLIN, System.currentTimeMillis(),
                text, symbols, refs, tokens, null);
    }

    @Override
    public LanguageId language() { return LanguageId.KOTLIN; }
}
