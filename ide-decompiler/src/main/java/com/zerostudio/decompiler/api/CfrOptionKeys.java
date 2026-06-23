package com.zerostudio.decompiler.api;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Canonical CFR option keys.
 *
 * <p>CFR accepts a set of string-typed options via the
 * {@code CfrDriver.Builder.withOptions(Map)}. The values are not
 * documented well upstream; this file captures the ones the editor cares
 * about and gives them a single source of truth.
 */
public final class CfrOptionKeys {

    private CfrOptionKeys() {}

    /** Hide the "Decompiled by CFR" banner from the output. */
    public static final String HIDE_BANNER = "hidebanner";
    /** Show the {@code @Deprecated} annotation. */
    public static final String SHOW_DEPRECATED = "showdeprecated";
    /** Don't recover the implicit lambda parameter names. */
    public static final String HIDE_LAMBDA_NAMES = "hidelambdanames";
    /** Aggressively try to recover the original variable names. */
    public static final String RECOVER_NAMES = "usenametable";
    /** Treat the input as class version 8. Defaults to 0 which means "auto". */
    public static final String CLASS_VERSION = "classversion";
    /** Force comments off. */
    public static final String HIDE_COMMENTS = "hideutf";

    public static Map<String, String> defaultOptions() {
        Map<String, String> m = new HashMap<>();
        m.put(HIDE_BANNER, "true");
        m.put(SHOW_DEPRECATED, "true");
        m.put(RECOVER_NAMES, "true");
        m.put(CLASS_VERSION, "0");
        return m;
    }

    @NonNull
    public static Map<String, String> mergedWith(
            @NonNull Map<String, String> overrides) {
        Map<String, String> m = defaultOptions();
        m.putAll(overrides);
        return m;
    }
}
