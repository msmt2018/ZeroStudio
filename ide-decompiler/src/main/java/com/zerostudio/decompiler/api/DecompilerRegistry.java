package com.zerostudio.decompiler.api;

import java.util.*;

public final class DecompilerRegistry {
    private static final Map<String, Decompiler> REGISTRY = new LinkedHashMap<>();

    public static void register(Decompiler d) { REGISTRY.put(d.name(), d); }
    public static Decompiler get(String name) { return REGISTRY.get(name); }
    public static Decompiler firstOrNull() { return REGISTRY.values().stream().findFirst().orElse(null); }
    public static Collection<Decompiler> all() { return Collections.unmodifiableCollection(REGISTRY.values()); }
    public static void clearForTests() { REGISTRY.clear(); }
}