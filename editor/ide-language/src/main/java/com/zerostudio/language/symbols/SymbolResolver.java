package com.zerostudio.language.symbols;

/**
 * Pluggable symbol resolver. Each language has its own implementation.
 *
 * <p>Resolvers are stateless and thread-safe; the per-project mutable state
 * lives in {@link com.zerostudio.language.index.ProjectIndex}.
 */
public interface SymbolResolver {
    /**
     * @return short name of the language this resolver handles.
     */
    String languageId();

    /**
     * Resolve a reference to a definition.
     *
     * @param ref   the reference (name + position)
     * @param ctx   resolution context (provides the project index, plus
     *              information about the source file)
     * @return a {@link com.zerostudio.language.model.ResolutionResult}
     */
    com.zerostudio.language.model.ResolutionResult resolve(
            com.zerostudio.language.model.Reference ref,
            ResolutionContext ctx);
}
