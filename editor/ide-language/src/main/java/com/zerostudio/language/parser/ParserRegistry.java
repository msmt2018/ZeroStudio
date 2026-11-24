package com.zerostudio.language.parser;

import com.zerostudio.language.model.LanguageId;

import java.util.EnumMap;
import java.util.Map;

/**
 * Process-wide parser registry. Like {@link com.zerostudio.language.lexer.LexerRegistry}
 * but for full file parsers.
 */
public final class ParserRegistry {

    private static final Map<LanguageId, Parser> INSTANCES =
            new EnumMap<>(LanguageId.class);

    static {
        INSTANCES.put(LanguageId.JAVA,    new JavaParserFacade());
        INSTANCES.put(LanguageId.KOTLIN,  new KotlinParser());
        INSTANCES.put(LanguageId.C,       new CParser());
        INSTANCES.put(LanguageId.CPP,     new CppParser());
    }

    private ParserRegistry() {}

    public static Parser get(LanguageId id) { return INSTANCES.get(id); }

    public static void register(LanguageId id, Parser p) { INSTANCES.put(id, p); }
}
