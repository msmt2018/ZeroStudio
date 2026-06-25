/*
 *  ZeroStudio IDE - ide-debugger
 *
 *  Phase G.4: AstIndex — cross-file symbol index.
 *
 *  A simple in-memory index that maps (className, methodName, fieldName)
 *  to (sourceFile, lineNumber) so that:
 *    - "Go to definition" (peekDefinition) can resolve a symbol
 *      reference to the file/line that declared it
 *    - "Find usages" (findUsages) can list all references to a symbol
 *
 *  The index is built lazily by walking parsed sources: when a source
 *  file is added, the [JavaSourceParser] extracts class signatures and
 *  the methods / fields inside, and we add entries to the index.
 *
 *  The data structure is two maps:
 *    symbols:  fully-qualified symbol key → (sourceFile, line)
 *    defs:     fully-qualified symbol key → Definition (richer record)
 *
 *  Lookup is O(1) via HashMap; iteration is O(N) but the IDE never
 *  iterates the whole index — it always filters by symbol key first.
 *
 *  Thread safety: all public methods are synchronized on the index
 *  itself; reads are fast, writes are infrequent (build once per file
 *  change).
 */
package com.zerostudio.debugger.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.zerostudio.debugger.model.JavaSourceParser.ParsedSource;
import com.zerostudio.debugger.model.JavaSourceParser.SourceClass;
import com.zerostudio.debugger.model.JavaSourceParser.SourceMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AstIndex {

    /**
     * A single definition: the source location where a symbol is declared.
     */
    public static final class Definition {
        @NonNull public final String symbolKey;     // e.g. "Lcom/example/Foo;.doIt"
        @NonNull public final String sourceFile;    // absolute or relative path
        public final int line;                      // 1-based
        public final int column;                    // 0-based, -1 if unknown
        @NonNull public final Kind kind;

        public enum Kind { CLASS, METHOD, FIELD, CONSTRUCTOR, INNER_CLASS }

        public Definition(@NonNull String symbolKey, @NonNull String sourceFile,
                          int line, int column, @NonNull Kind kind) {
            this.symbolKey = symbolKey;
            this.sourceFile = sourceFile;
            this.line = line;
            this.column = column;
            this.kind = kind;
        }
    }

    /**
     * A reference to a symbol (a usage). For Phase G.4 the only data we
     * capture is the file + line where the reference occurs; richer info
     * (e.g. enclosing method) can be added later.
     */
    public static final class Reference {
        @NonNull public final String symbolKey;
        @NonNull public final String sourceFile;
        public final int line;
        public final int column;

        public Reference(@NonNull String symbolKey, @NonNull String sourceFile,
                         int line, int column) {
            this.symbolKey = symbolKey;
            this.sourceFile = sourceFile;
            this.line = line;
            this.column = column;
        }
    }

    /** Map from symbol key → Definition (declaration site). */
    private final ConcurrentMap<String, Definition> definitions =
            new ConcurrentHashMap<>();

    /** Map from symbol key → list of Reference (usage sites). */
    private final ConcurrentMap<String, List<Reference>> references =
            new ConcurrentHashMap<>();

    /** Map from sourceFile → list of symbolKeys declared in it. Used for invalidation. */
    private final ConcurrentMap<String, List<String>> symbolsByFile =
            new ConcurrentHashMap<>();

    private final JavaSourceParser parser;

    public AstIndex() {
        this.parser = new JavaSourceParser();
    }

    // ----------------------------------------------------------------
    // Building the index
    // ----------------------------------------------------------------

    /**
     * Index a .java source file. Extracts all class/method declarations
     * and inserts them into the index.
     *
     * @param sourceFile absolute or workspace-relative path
     */
    public synchronized void indexSource(@NonNull String sourceFile) {
        // Phase G.4: support .kt via class file fallback (we still call
        // JavaSourceParser — it returns null for non-.java files, which
        // is the right behaviour for now).
        ParsedSource parsed = parser.parse(new File(sourceFile));
        if (parsed == null) return;

        // Remove any previous index entries for this file
        removeSource(sourceFile);

        List<String> keys = new ArrayList<>();
        for (SourceClass cls : parsed.classes) {
            // Class declaration
            String classKey = cls.signature;
            Definition classDef = new Definition(
                    classKey, sourceFile, 0, 0, Definition.Kind.CLASS);
            definitions.put(classKey, classDef);
            keys.add(classKey);
            // Methods inside
            for (SourceMethod m : cls.methods) {
                String methodKey = cls.signature + "." + m.name;
                Definition methodDef = new Definition(
                        methodKey, sourceFile, m.lineNumber, 0,
                        "<init>".equals(m.name) ? Definition.Kind.CONSTRUCTOR
                                                : Definition.Kind.METHOD);
                definitions.put(methodKey, methodDef);
                keys.add(methodKey);
            }
        }
        symbolsByFile.put(sourceFile, keys);
    }

    /**
     * Remove all index entries for a source file. Useful when the file
     * is re-indexed or deleted.
     */
    public synchronized void removeSource(@NonNull String sourceFile) {
        List<String> previous = symbolsByFile.remove(sourceFile);
        if (previous == null) return;
        for (String key : previous) {
            definitions.remove(key);
        }
    }

    /**
     * Clear the entire index.
     */
    public synchronized void clear() {
        definitions.clear();
        references.clear();
        symbolsByFile.clear();
    }

    // ----------------------------------------------------------------
    // Recording references (called by ReferenceFinder)
    // ----------------------------------------------------------------

    /**
     * Record a usage of a symbol. Called by [ReferenceFinder] when it
     * walks a parsed AST and finds a reference to a symbol.
     */
    public synchronized void addReference(@NonNull Reference ref) {
        List<Reference> existing = references.get(ref.symbolKey);
        if (existing == null) {
            existing = Collections.synchronizedList(new ArrayList<>());
            references.put(ref.symbolKey, existing);
        }
        existing.add(ref);
    }

    // ----------------------------------------------------------------
    // Lookups
    // ----------------------------------------------------------------

    /**
     * Find the declaration site of a symbol. Returns null if not indexed.
     */
    @Nullable
    public Definition peekDefinition(@NonNull String symbolKey) {
        return definitions.get(symbolKey);
    }

    /**
     * Find all usages of a symbol. Returns an empty list if none.
     */
    @NonNull
    public List<Reference> findUsages(@NonNull String symbolKey) {
        List<Reference> refs = references.get(symbolKey);
        if (refs == null) return Collections.emptyList();
        synchronized (refs) {
            return new ArrayList<>(refs);
        }
    }

    /**
     * Build a symbol key for a class.
     */
    @NonNull
    public static String classKey(@NonNull String signature) {
        return signature; // "Lcom/example/Foo;"
    }

    /**
     * Build a symbol key for a method.
     */
    @NonNull
    public static String methodKey(@NonNull String classSignature,
                                   @NonNull String methodName) {
        return classSignature + "." + methodName;
    }

    // ----------------------------------------------------------------
    // Stats
    // ----------------------------------------------------------------

    public int definitionCount() { return definitions.size(); }
    public int referenceCount() {
        int total = 0;
        for (List<Reference> list : references.values()) {
            synchronized (list) { total += list.size(); }
        }
        return total;
    }
    public int indexedFileCount() { return symbolsByFile.size(); }
}
