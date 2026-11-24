package com.zerostudio.language.symbols;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.Symbol;

import java.util.List;

/**
 * C++ symbol resolver. C++ has nested scopes (namespaces, classes). The
 * current implementation uses an enclosing-scope walk: try
 * {@code outer::inner::name}, then strip one segment at a time, then
 * fall back to project-wide.
 */
public final class CppSymbolResolver implements SymbolResolver {
    @Override
    public String languageId() { return LanguageId.CPP.id(); }

    @Override
    public ResolutionResult resolve(Reference ref, ResolutionContext ctx) {
        if (ctx.index == null) return ResolutionResult.unresolved(ref);
        String name = ref.name;
        // Try the fully qualified path from the enclosing scope.
        String scope = ref.containerFqn == null ? "" : ref.containerFqn;
        while (true) {
            String fqn = scope.isEmpty() ? name : scope + "::" + name;
            List<Symbol> cand = ctx.index.lookup().byFqn(fqn);
            if (cand.size() == 1) return ResolutionResult.resolved(ref, cand.get(0));
            if (!cand.isEmpty()) return ResolutionResult.ambiguous(ref, cand.get(0));
            int idx = scope.lastIndexOf("::");
            if (idx < 0) break;
            scope = scope.substring(0, idx);
        }
        // Project-wide by simple name
        List<Symbol> candidates = ctx.index.lookup().byName(name);
        if (candidates.isEmpty()) return ResolutionResult.unresolved(ref);
        if (candidates.size() == 1) {
            return ResolutionResult.resolved(ref, candidates.get(0));
        }
        return ResolutionResult.ambiguous(ref, candidates.get(0));
    }
}
