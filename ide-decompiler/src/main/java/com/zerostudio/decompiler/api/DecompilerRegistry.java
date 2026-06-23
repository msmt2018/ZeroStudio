package com.zerostudio.decompiler.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of available decompilers. Decompilers are
 * looked up by short name; the editor UI can use the registry to build
 * a "Decompile with..." menu.
 */
public final class DecompilerRegistry {

    private static final Map<String, Decompiler> engines =
            new ConcurrentHashMap<>();

    private DecompilerRegistry() {}

    /** Register an engine. Replaces any previous registration with the same name. */
    public static void register(@NonNull Decompiler d) {
        Objects.requireNonNull(d);
        engines.put(d.name(), d);
    }

    @Nullable
    public static Decompiler get(@NonNull String name) {
        return engines.get(name);
    }

    @NonNull
    public static Decompiler firstOrNull() {
        return engines.isEmpty() ? null : engines.values().iterator().next();
    }

    @NonNull
    public static Map<String, Decompiler> all() {
        return new LinkedHashMap<>(engines);
    }

    /** Test-only: clear the registry. */
    public static void clearForTests() {
        engines.clear();
    }
}
