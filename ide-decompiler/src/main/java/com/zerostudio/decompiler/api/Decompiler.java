package com.zerostudio.decompiler.api;

import androidx.annotation.NonNull;

/**
 * Decompiles a single Java class to readable Java source.
 *
 * <p>Implementations must be thread-safe. The {@code Decompiler} is
 * stateless between calls; caching is the responsibility of the
 * {@link com.zerostudio.decompiler.cache.CachingDecompiler} decorator.
 */
public interface Decompiler {

    /**
     * @return the human-readable name of the engine, e.g. {@code "cfr"}.
     */
    @NonNull
    String name();

    /**
     * @return a short version string for display in the editor status bar.
     */
    @NonNull
    String version();

    /**
     * Decompile the class described by {@code request}.
     *
     * <p>Implementations must never throw; they must return a
     * {@link DecompileResult} with a populated {@code failure} field
     * instead. The caller is responsible for deciding what to do with
     * a failed decompilation (typically: show a "Source not found" UI
     * and offer a bytecode view as a fallback).
     */
    @NonNull
    DecompileResult decompile(@NonNull DecompileRequest request);
}
