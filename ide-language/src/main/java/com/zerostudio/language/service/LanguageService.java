package com.zerostudio.language.service;

import com.zerostudio.language.index.InMemoryProjectIndex;
import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.lexer.Lexer;
import com.zerostudio.language.lexer.LexerRegistry;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.parser.Parser;
import com.zerostudio.language.parser.ParserRegistry;
import com.zerostudio.language.source.SourceResolver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The unified entry point for language services.
 *
 * <p>Responsibilities:
 *
 * <ol>
 *   <li>Parse a single file (with caching).</li>
 *   <li>Manage the project-wide index of symbols.</li>
 *   <li>Expose {@link GoToDefinitionService} and friends.</li>
 * </ol>
 *
 * <p>A single instance per IDE process is sufficient. The service is
 * thread-safe.
 */
public final class LanguageService {

    private final ProjectIndex index;
    private final java.util.Map<String, ParsedFile> parsedCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final GoToDefinitionService goToDefinition;
    private volatile SourceResolver sourceResolver;
    private volatile ImportResolver importResolver;

    public LanguageService() {
        this(new InMemoryProjectIndex());
    }

    public LanguageService(ProjectIndex index) {
        this.index = Objects.requireNonNull(index);
        this.goToDefinition = new GoToDefinitionService(this);
    }

    public final ProjectIndex index() { return index; }

    public GoToDefinitionService goToDefinition() { return goToDefinition; }

    /**
     * Install a {@link SourceResolver}. Once installed, the
     * {@link GoToDefinitionService} will follow import chains to
     * source jars and class-jar decompilations, in addition to the
     * workspace.
     */
    public void setSourceResolver(SourceResolver resolver) {
        this.sourceResolver = resolver;
        this.importResolver = resolver == null
                ? null : new ImportResolver(resolver);
    }

    /** @return the installed {@link SourceResolver} or {@code null}. */
    public SourceResolver sourceResolver() { return sourceResolver; }

    /** @return the {@link ImportResolver} (null until
     *          {@link #setSourceResolver} is called). */
    public ImportResolver importResolver() { return importResolver; }

    /**
     * Lex a file or in-memory text.
     */
    public List<com.zerostudio.language.lexer.Token> lex(String text,
                                                          LanguageId lang) {
        Lexer lexer = LexerRegistry.get(lang);
        if (lexer == null) return List.of();
        return lexer.tokenize(text);
    }

    /**
     * Parse a file. Results are cached by absolute path; if the file is
     * modified, callers must invoke {@link #invalidate(String)} first.
     */
    public ParsedFile parseFile(File file) throws IOException {
        String path = file.getAbsolutePath();
        ParsedFile cached = parsedCache.get(path);
        if (cached != null) {
            return cached;
        }
        LanguageId lang = LanguageId.fromExtension(file.getName());
        if (lang == null) {
            throw new IOException("Unknown language for file: " + file);
        }
        Parser p = ParserRegistry.get(lang);
        if (p == null) {
            throw new IOException("No parser for language: " + lang);
        }
        ParsedFile parsed = p.parse(file);
        parsedCache.put(path, parsed);
        index.updateFile(parsed);
        return parsed;
    }

    /**
     * Parse an in-memory text (path is informational only). The result is
     * NOT cached by content.
     */
    public ParsedFile parseText(String path, String text, LanguageId lang) {
        Parser p = ParserRegistry.get(lang);
        if (p == null) return null;
        ParsedFile parsed = p.parse(path, text);
        parsedCache.put(path, parsed);
        index.updateFile(parsed);
        return parsed;
    }

    /** Force re-parse next time. */
    public void invalidate(String path) {
        parsedCache.remove(path);
        index.removeFile(path);
    }

    /** Read a file from disk as text. */
    public String readText(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /**
     * Find every symbol declared in the given file whose range contains the
     * given position. Used by hover / definition.
     */
    public Symbol symbolAt(ParsedFile parsed, SourcePosition pos) {
        Symbol best = null;
        int bestSize = Integer.MAX_VALUE;
        for (Symbol s : parsed.symbols) {
            if (!s.range.contains(pos)) continue;
            int size = s.range.end.line - s.range.start.line;
            if (size < bestSize) {
                best = s;
                bestSize = size;
            }
        }
        return best;
    }
}
