package com.zerostudio.language.model;
public final class ResolutionResult {
    public final boolean resolved;
    public final String targetFile;
    public final SourceRange targetRange;
    public final Symbol targetSymbol;
    public final String failure;
    /** 关联的原始引用，便于上层做引用计数 / 缓存。 */
    public final Reference reference;

    private ResolutionResult(boolean resolved, String targetFile, SourceRange targetRange,
                             Symbol targetSymbol, String failure, Reference reference) {
        this.resolved = resolved; this.targetFile = targetFile;
        this.targetRange = targetRange; this.targetSymbol = targetSymbol;
        this.failure = failure;
        this.reference = reference;
    }

    /** 旧 API：直接通过文件 / 范围 / 符号构造。 */
    public static ResolutionResult resolved(String file, SourceRange range, Symbol symbol) {
        return new ResolutionResult(true, file, range, symbol, null, null);
    }
    public static ResolutionResult missing(String reason) {
        return new ResolutionResult(false, null, null, null, reason, null);
    }

    /** 新 API：基于 Reference 构造，便于把 ref 与 result 绑定。 */
    public static ResolutionResult resolved(Reference ref, Symbol symbol) {
        if (ref == null) return resolved(null, null, symbol);
        return new ResolutionResult(true,
                ref.filePath,
                ref.range,
                symbol,
                null,
                ref);
    }
    public static ResolutionResult unresolved(Reference ref) {
        return new ResolutionResult(false, null, null, null, "unresolved", ref);
    }
    public static ResolutionResult ambiguous(Reference ref, Symbol firstCandidate) {
        return new ResolutionResult(false, null, null, firstCandidate, "ambiguous", ref);
    }

    public boolean isResolved() { return resolved; }
}
