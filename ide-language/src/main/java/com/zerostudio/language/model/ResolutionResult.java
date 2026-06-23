package com.zerostudio.language.model;

import java.util.Objects;

/**
 * The outcome of resolving a {@link Reference} against the project index.
 *
 * <p>If {@link #targetSymbol} is {@code null}, resolution failed (unresolved
 * reference, ambiguous reference, etc.).
 */
public final class ResolutionResult {
    public final Reference reference;
    public final Symbol targetSymbol;
    public final String targetFile;       // may differ from reference.sourceFile
    public final SourceRange targetRange;  // declaration range in targetFile
    public final boolean ambiguous;

    public ResolutionResult(Reference reference,
                            Symbol targetSymbol,
                            String targetFile,
                            SourceRange targetRange,
                            boolean ambiguous) {
        this.reference = reference;
        this.targetSymbol = targetSymbol;
        this.targetFile = targetFile;
        this.targetRange = targetRange == null ? SourceRange.NONE : targetRange;
        this.ambiguous = ambiguous;
    }

    public boolean isResolved() { return targetSymbol != null && !ambiguous; }

    public static ResolutionResult unresolved(Reference ref) {
        return new ResolutionResult(ref, null, ref.sourceFile, ref.range, false);
    }

    public static ResolutionResult ambiguous(Reference ref, Symbol first) {
        return new ResolutionResult(ref, first, first.sourceFile, first.range, true);
    }

    public static ResolutionResult resolved(Reference ref, Symbol target) {
        return new ResolutionResult(ref, target, target.sourceFile, target.range, false);
    }

    @Override
    public String toString() {
        return "ResolutionResult{"
                + (isResolved() ? "->" + targetSymbol : (ambiguous ? "AMBIGUOUS" : "UNRESOLVED"))
                + " " + reference.name
                + " @ " + (reference.sourceFile == null ? "?" : reference.sourceFile)
                + reference.range
                + "}";
    }
}
