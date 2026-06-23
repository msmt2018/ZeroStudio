package com.zerostudio.decompiler.api;

public final class DecompileResult {
    public final String className;
    public final String source;
    public final java.util.Map<Integer, Long> lineMapping;
    public final String failure;

    private DecompileResult(String className, String source, java.util.Map<Integer, Long> lineMapping, String failure) {
        this.className = className;
        this.source = source;
        this.lineMapping = lineMapping;
        this.failure = failure;
    }

    public static DecompileResult ok(String className, String source, java.util.Map<Integer, Long> lineMapping) {
        return new DecompileResult(className, source, lineMapping, null);
    }

    public static DecompileResult fail(String className, String failure) {
        return new DecompileResult(className, null, null, failure);
    }

    public boolean isOk() { return failure == null && source != null; }
}