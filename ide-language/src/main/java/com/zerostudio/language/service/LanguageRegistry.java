package com.zerostudio.language.service;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.parser.Parser;
import com.zerostudio.language.parser.ParserRegistry;

import java.util.EnumMap;
import java.util.Map;

/**
 * Process-wide registry of {@link LanguageService} instances. The IDE keeps
 * one service per project; tests may keep one per test.
 */
public final class LanguageRegistry {

    private static final Map<LanguageId, Parser> PARSERS =
            new EnumMap<>(LanguageId.class);

    static {
        for (LanguageId id : LanguageId.values()) {
            Parser p = ParserRegistry.get(id);
            if (p != null) PARSERS.put(id, p);
        }
    }

    private LanguageRegistry() {}

    public static Parser parserFor(LanguageId id) { return PARSERS.get(id); }
}
