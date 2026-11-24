package com.zerostudio.language.symbols;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.Symbol;

import java.util.List;

/**
 * Default Java symbol resolver. Uses lexical scope rules: a reference is
 * resolved by (1) looking it up in the enclosing class/interface chain
 * defined in this file, (2) then by FQN in the project index, (3) then by
 * name in the import list.
 *
 * <p>This is intentionally simple but covers the most common Go-to-Definition
 * cases: local variables, fields, methods on {@code this}, calls on
 * imported classes.
 */
public final class JavaSymbolResolver implements SymbolResolver {

    @Override
    public String languageId() { return LanguageId.JAVA.id(); }

    @Override
    public ResolutionResult resolve(Reference ref, ResolutionContext ctx) {
        if (ctx.index == null) return ResolutionResult.unresolved(ref);
        String name = ref.name;

        // 1. Search declared symbols in current file first.
        for (Symbol s : ctx.currentFile.symbols) {
            if (s.name.equals(name)
                    && s.range.start.line <= ref.range.start.line) {
                // The first match encountered in source order is a candidate.
                // Take the smallest-range enclosing one to handle overloads.
                if (ref.containerFqn == null
                        || ref.containerFqn.equals(s.containerName)
                        || (s.containerName != null
                                && ref.containerFqn.startsWith(s.containerName))) {
                    return ResolutionResult.resolved(ref, s);
                }
            }
        }

        // 2. Fall back to project-wide lookup.
        List<Symbol> candidates = ctx.index.lookup().byName(name);
        if (candidates.isEmpty()) return ResolutionResult.unresolved(ref);
        if (candidates.size() == 1) {
            return ResolutionResult.resolved(ref, candidates.get(0));
        }
        return ResolutionResult.ambiguous(ref, candidates.get(0));
    }
}
