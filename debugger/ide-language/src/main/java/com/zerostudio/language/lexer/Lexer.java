package com.zerostudio.language.lexer;

import com.zerostudio.language.model.LanguageId;

import java.util.List;

/**
 * Lexer interface. Each language has its own implementation.
 *
 * <p>Implementations are expected to be stateless and thread-safe.
 */
public interface Lexer {
    /**
     * Tokenize the given source text.
     *
     * @param text raw source text
     * @return ordered list of tokens; the last token is usually an EOF token
     */
    List<Token> tokenize(String text);

    /**
     * The language this lexer handles.
     */
    LanguageId language();
}
