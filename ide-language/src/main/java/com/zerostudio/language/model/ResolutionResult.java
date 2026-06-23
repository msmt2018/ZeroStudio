package com.zerostudio.language.model;
public final class ResolutionResult {
    public final boolean resolved;
    public final String targetFile;
    public final SourceRange targetRange;
    public final Symbol targetSymbol;
    public final String failure;

    private ResolutionResult(boolean resolved, String targetFile, SourceRange targetRange,
                             Symbol targetSymbol, String failure) {
        this.resolved = resolved; this.targetFile = targetFile;
        this.targetRange = targetRange; this.targetSymbol = targetSymbol; this.failure = failure;
    }

    public static ResolutionResult resolved(String file, SourceRange range, Symbol symbol) {
        return new ResolutionResult(true, file, range, symbol, null);
    }
    public static ResolutionResult missing(String reason) {
        return new ResolutionResult(false, null, null, null, reason);
    }
    public boolean isResolved() { return resolved; }
}