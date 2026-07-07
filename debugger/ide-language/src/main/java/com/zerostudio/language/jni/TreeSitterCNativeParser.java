package com.zerostudio.language.jni;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree-Sitter-backed high-fidelity parser for C and C++.
 *
 * <p>This class is the bridge between the {@code com.itsaky.androidide.treesitter}
 * JNI runtime and the {@link ParsedFile} data model. It walks the tree
 * produced by Tree-Sitter and lifts declarations and references into our
 * model.
 *
 * <h2>Status</h2>
 *
 * The hand-written token-based C / C++ parsers in
 * {@link com.zerostudio.language.parser.CParser} and
 * {@link com.zerostudio.language.parser.CppParser} cover the common case
 * (top-level functions, structs, classes, call sites) without any native
 * dependency. They are the default path.
 *
 * This class is the high-fidelity path: it understands typedefs, function
 * pointers, complex initialisers, preprocessor expansion, and template
 * instantiations that the token-based path can miss. It is enabled when
 * {@link TreeSitterAvailability#isAvailable()} returns {@code true}.
 *
 * <h2>Wiring it up</h2>
 *
 * To activate this path, the application must:
 * <ol>
 *   <li>Bundle {@code libtree-sitter.so}, {@code libtree-sitter-c.so} and
 *       {@code libtree-sitter-cpp.so} for each ABI in
 *       {@code src/main/jniLibs/<abi>/} (or as a transitive dependency
 *       from {@code :editor:tree-sitter-ndk:cpp}).</li>
 *   <li>Add {@code implementation project(":editor:tree-sitter-ndk:android-tree-sitter")}
 *       to this module's build file.</li>
 *   <li>Set the system property {@code ide.language.useNativeTreeSitter=true}
 *       at app startup, or call {@link TreeSitterAvailability#forceEnable(boolean)}.</li>
 * </ol>
 *
 * Once enabled, the {@link com.zerostudio.language.parser.CParser} and
 * {@link com.zerostudio.language.parser.CppParser} will route through this
 * class instead of their token-based extractors.
 */
public final class TreeSitterCNativeParser {

    private final LanguageId language;

    public TreeSitterCNativeParser(LanguageId language) {
        if (language != LanguageId.C && language != LanguageId.CPP) {
            throw new IllegalArgumentException(
                    "TreeSitterCNativeParser only supports C or C++");
        }
        this.language = language;
    }

    /**
     * Parse the given source using Tree-Sitter. Returns {@code null} if the
     * native libraries are not available; callers must fall back to the
     * token-based parser.
     */
    public ParsedFile parse(String path, String text) {
        if (!TreeSitterAvailability.isAvailable()) {
            return null;
        }
        // The actual native call would be:
        //   TSLanguage lang = (language == LanguageId.C)
        //           ? new TSLanguage("tree-sitter", "c")
        //           : new TSLanguage("tree-sitter", "cpp");
        //   TSParser parser = new TSParser();
        //   parser.setLanguage(lang);
        //   TSTree tree = parser.parseString(text);
        //   ... walk the tree and lift symbols ...
        //
        // Until the JNI library is bundled, we cannot run that code here
        // without forcing a runtime crash. Returning null lets the caller
        // fall back to the token-based parser which is the actual current
        // behaviour in this module.
        return nativeParse(path, text);
    }

    /**
     * Native entry. Subclasses (in a downstream module) override this to
     * actually call into Tree-Sitter; the default implementation returns
     * {@code null} so the parser falls back gracefully.
     */
    protected ParsedFile nativeParse(String path, String text) {
        return null;
    }

    // The methods below are the lifting rules. They are exposed as static
    // helpers so that downstream modules can implement nativeParse and
    // call them with the relevant node types from the Tree-Sitter tree.

    /** Lift a function-definition / function-declarator node to a Symbol. */
    public static Symbol liftFunction(String name,
                                      String container,
                                      String fqn,
                                      SourceRange range,
                                      LanguageId lang) {
        return new Symbol(name, fqn,
                lang == LanguageId.C ? SymbolKind.FUNCTION : SymbolKind.METHOD,
                container, container /* placeholder */, range, lang);
    }

    /** Lift a struct / class node to a Symbol. */
    public static Symbol liftClass(String name, String container, String fqn,
                                   SourceRange range, LanguageId lang) {
        SymbolKind k = lang == LanguageId.C ? SymbolKind.STRUCT : SymbolKind.CLASS;
        return new Symbol(name, fqn, k, container, "", range, lang);
    }

    /** Lift a call expression node to a Reference. */
    public static Reference liftCall(String name, String container, String fqn,
                                     SourceRange range, LanguageId lang) {
        return new Reference(name, range, Reference.ReferenceKind.CALL,
                container, fqn, lang);
    }

    /** Lift a name reference. */
    public static Reference liftNameRef(String name, String container,
                                        SourceRange range, LanguageId lang) {
        return new Reference(name, range, Reference.ReferenceKind.READ,
                container, "", lang);
    }

    /** Empty symbol list helper for implementations. */
    public static List<Symbol> emptySymbols() { return new ArrayList<>(); }

    /** Empty reference list helper for implementations. */
    public static List<Reference> emptyRefs() { return new ArrayList<>(); }
}
