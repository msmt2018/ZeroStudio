package com.zerostudio.language.symbols;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.Symbol;

import java.util.List;

/**
 * C symbol resolver. C has no classes; names are resolved as
 * (1) function/struct in current file, (2) global names in the project.
 */
public final class CSymbolResolver implements SymbolResolver {
    @Override
    public String languageId() { return LanguageId.C.id(); }

    @Override
    public ResolutionResult resolve(Reference ref, ResolutionContext ctx) {
        if (ctx.index == null) return ResolutionResult.unresolved(ref);
        String name = ref.name;
        for (Symbol s : ctx.currentFile.symbols) {
            if (s.name.equals(name)) {
                return ResolutionResult.resolved(ref, s);
            }
        }
        List<Symbol> candidates = ctx.index.lookup().byName(name);
        if (candidates.isEmpty()) return ResolutionResult.unresolved(ref);
        if (candidates.size() == 1) {
            return ResolutionResult.resolved(ref, candidates.get(0));
        }
        // C names are usually unambiguous; pick the first one.
        return ResolutionResult.resolved(ref, candidates.get(0));
    }
}
