package com.zerostudio.language.index;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Symbol;

import java.util.Collection;
import java.util.List;

/**
 * Cross-file index of symbols. Implementations are responsible for:
 *
 * <ul>
 *   <li>Storing symbols by file, by FQN, and by simple name.</li>
 *   <li>Resolving file changes incrementally (the {@link DeltaIndex}
 *       contract).</li>
 *   <li>Surviving concurrent reads.</li>
 * </ul>
 */
public interface ProjectIndex {

    /** Add or replace all symbols and references of a parsed file. */
    void updateFile(ParsedFile parsed);

    /** Drop everything associated with the given file path. */
    void removeFile(String path);

    /** Return the {@link Lookup} facade (cheap to acquire; views are stable). */
    Lookup lookup();

    /** Clear the entire index. */
    void clear();

    /** Number of indexed files. */
    int fileCount();

    /**
     * Lookup facade. All methods are safe to call concurrently; they may
     * block briefly during updates but never throw.
     */
    interface Lookup {
        List<Symbol> byName(String name);
        List<Symbol> byFqn(String fqn);
        List<Symbol> byKind(SymbolKind kind);
        List<Symbol> inFile(String path);
        List<ParsedFile> files();
        List<ParsedFile> filesOfLanguage(LanguageId lang);
        ParsedFile file(String path);
    }
}
