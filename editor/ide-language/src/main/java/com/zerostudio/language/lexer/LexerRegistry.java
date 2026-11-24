package com.zerostudio.language.lexer;

import com.zerostudio.language.model.LanguageId;

import java.util.EnumMap;
import java.util.Map;

/**
 * Process-wide registry of {@link Lexer} implementations, keyed by
 * {@link LanguageId}. Lazily populated; tests may pre-register.
 */
public final class LexerRegistry {

    private static final Map<LanguageId, Lexer> INSTANCES =
            new EnumMap<>(LanguageId.class);

    static {
        INSTANCES.put(LanguageId.JAVA,    new JavaLexer());
        INSTANCES.put(LanguageId.KOTLIN,  new KotlinLexer());
        INSTANCES.put(LanguageId.C,       new CLexer());
        INSTANCES.put(LanguageId.CPP,     new CppLexer());
    }

    private LexerRegistry() {}

    public static Lexer get(LanguageId id) { return INSTANCES.get(id); }

    public static void register(LanguageId id, Lexer lexer) {
        INSTANCES.put(id, lexer);
    }
}
