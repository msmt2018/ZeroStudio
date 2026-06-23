package com.zerostudio.decompiler.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Output of a decompilation request.
 *
 * <p>The {@code source} field is the reconstructed Java source code.
 * The {@code lineMapping} field maps every 1-based source line back to
 * the original bytecode offset, which is the same convention that
 * Android's debuggers and the {@code LineNumberTable} attribute use.
 * This lets a debugger display "paused at decompiled line 17" and have
 * the editor highlight exactly that line.
 */
public final class DecompileResult {

    @NonNull
    public final String className;

    @NonNull
    public final String source;

    @NonNull
    public final Map<Integer, Long> lineMapping;

    @Nullable
    public final String failure;

    private DecompileResult(@NonNull String className,
                            @NonNull String source,
                            @NonNull Map<Integer, Long> lineMapping,
                            @Nullable String failure) {
        this.className = className;
        this.source = source;
        this.lineMapping = lineMapping;
        this.failure = failure;
    }

    public boolean isOk() {
        return failure == null;
    }

    public static DecompileResult ok(String name, String src,
                                     Map<Integer, Long> mapping) {
        return new DecompileResult(name, src,
                mapping == null ? Collections.<Integer, Long>emptyMap() : mapping,
                null);
    }

    public static DecompileResult fail(String name, String error) {
        return new DecompileResult(name, "", Collections.emptyMap(), error);
    }
}
