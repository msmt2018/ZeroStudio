package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.SourceLocation;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.symbols.ResolutionContext;
import com.zerostudio.language.symbols.ResolverRegistry;
import com.zerostudio.language.symbols.SymbolResolver;

import java.util.Objects;

/**
 * Provides "Go to Definition" semantics for the code editor.
 *
 * <p>Triggered by:
 * <ul>
 *   <li>Ctrl+click on a token in the editor</li>
 *   <li>"Go to Definition" from the long-press / right-click menu</li>
 *   <li>The debugger, when a breakpoint is hit and the user clicks
 *       "show declaration"</li>
 * </ul>
 */
public final class GoToDefinitionService {

    private final LanguageService language;

    public GoToDefinitionService(LanguageService language) {
        this.language = Objects.requireNonNull(language);
    }

    /**
     * Resolve a single source location to a target symbol.
     *
     * <p>The flow is:
     * <ol>
     *   <li>Load (or re-parse) the source file.</li>
     *   <li>Find the {@link Reference} at the position (or synthesize one
     *       from the surrounding identifier token).</li>
     *   <li>Ask the language's {@link SymbolResolver}.</li>
     *   <li>Return the {@link ResolutionResult}.</li>
     * </ol>
     */
    public ResolutionResult resolve(SourceLocation location) {
        ParsedFile parsed = language.index().lookup().file(location.file);
        if (parsed == null) {
            // File isn't indexed yet; ask the language service to parse it.
            try {
                parsed = language.parseFile(new java.io.File(location.file));
            } catch (java.io.IOException e) {
                return null;
            }
        }
        return resolveInternal(parsed, location.position);
    }

    /**
     * Variant that works on an already-parsed file (e.g. the editor
     * background parser is keeping it up to date).
     */
    public ResolutionResult resolve(ParsedFile parsed, SourcePosition pos) {
        return resolveInternal(parsed, pos);
    }

    private ResolutionResult resolveInternal(ParsedFile parsed, SourcePosition pos) {
        Reference ref = findReferenceAt(parsed, pos);
        if (ref == null) {
            // No match: synthesise a READ reference from the enclosing symbol.
            Symbol enclosing = language.symbolAt(parsed, pos);
            if (enclosing == null) return null;
            ref = new Reference(enclosing.name,
                    enclosing.range, Reference.ReferenceKind.READ,
                    enclosing.containerName, parsed.path, parsed.language);
        }
        SymbolResolver resolver = ResolverRegistry.get(parsed.language);
        ResolutionResult result = null;
        if (resolver != null) {
            ResolutionContext ctx = new ResolutionContext(parsed, language.index());
            result = resolver.resolve(ref, ctx);
        }
        // Fall back to import chain -> SourceResolver. This is the
        // path that makes "click on Toast -> jump to decompiled
        // android.widget.Toast" work.
        if ((result == null || !result.isResolved())
                && language.importResolver() != null
                && parsed.language == LanguageId.JAVA) {
            java.util.Optional<ResolutionResult> imported =
                    language.importResolver().resolveImport(parsed, ref);
            if (imported.isPresent()) {
                return imported.get();
            }
        }
        return result == null ? ResolutionResult.unresolved(ref) : result;
    }

    /**
     * Find the {@link Reference} whose range contains the given position.
     * If multiple match, return the smallest one.
     */
    public Reference findReferenceAt(ParsedFile parsed, SourcePosition pos) {
        Reference best = null;
        int bestSize = Integer.MAX_VALUE;
        for (Reference r : parsed.references) {
            if (!r.range.contains(pos)) continue;
            int size = r.range.end.line - r.range.start.line;
            if (size < bestSize) {
                best = r;
                bestSize = size;
            }
        }
        return best;
    }
}
