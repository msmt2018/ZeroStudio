package com.zerostudio.language.symbols;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.Symbol;

import java.util.List;

/**
 * Kotlin symbol resolver. Mirrors the Java one with one extra rule: the
 * enclosing class is searched by name, then by simple-name matches anywhere
 * in the project index.
 */
public final class KotlinSymbolResolver implements SymbolResolver {
    @Override
    public String languageId() { return LanguageId.KOTLIN.id(); }

    @Override
    public ResolutionResult resolve(Reference ref, ResolutionContext ctx) {
        if (ctx.index == null) return ResolutionResult.unresolved(ref);
        String name = ref.name;
        // Search local file first
        for (Symbol s : ctx.currentFile.symbols) {
            if (!s.name.equals(name)) continue;
            if (s.containerName != null
                    && ref.containerFqn != null
                    && (ref.containerFqn.equals(s.containerName)
                            || ref.containerFqn.startsWith(s.containerName + "."))) {
                return ResolutionResult.resolved(ref, s);
            }
            if (s.containerName == null) {
                return ResolutionResult.resolved(ref, s);
            }
        }
        List<Symbol> candidates = ctx.index.lookup().byName(name);
        if (candidates.isEmpty()) return ResolutionResult.unresolved(ref);
        if (candidates.size() == 1) {
            return ResolutionResult.resolved(ref, candidates.get(0));
        }
        return ResolutionResult.ambiguous(ref, candidates.get(0));
    }
}
