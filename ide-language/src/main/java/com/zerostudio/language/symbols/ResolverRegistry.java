package com.zerostudio.language.symbols;

import com.zerostudio.language.model.LanguageId;

import java.util.EnumMap;
import java.util.Map;

/** Process-wide resolver registry. */
public final class ResolverRegistry {

    private static final Map<LanguageId, SymbolResolver> INSTANCES =
            new EnumMap<>(LanguageId.class);

    static {
        INSTANCES.put(LanguageId.JAVA,    new JavaSymbolResolver());
        INSTANCES.put(LanguageId.KOTLIN,  new KotlinSymbolResolver());
        INSTANCES.put(LanguageId.C,       new CSymbolResolver());
        INSTANCES.put(LanguageId.CPP,     new CppSymbolResolver());
    }

    private ResolverRegistry() {}

    public static SymbolResolver get(LanguageId id) { return INSTANCES.get(id); }

    public static void register(LanguageId id, SymbolResolver r) {
        INSTANCES.put(id, r);
    }
}
