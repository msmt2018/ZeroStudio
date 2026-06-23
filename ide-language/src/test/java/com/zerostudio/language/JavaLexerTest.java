package com.zerostudio.language;

import com.zerostudio.language.lexer.JavaLexer;
import com.zerostudio.language.lexer.Lexer;
import com.zerostudio.language.lexer.Token;
import com.zerostudio.language.model.LanguageId;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JavaLexerTest {

    @Test
    public void keywordsAreTaggedAsKeywords() {
        Lexer lexer = new JavaLexer();
        List<Token> tokens = lexer.tokenize("class Foo { void bar() {} }");
        long keywords = tokens.stream()
                .filter(t -> t.kind == Token.Kind.KEYWORD)
                .count();
        assertEquals(2, keywords);   // class, void
    }

    @Test
    public void identifiersAreTaggedAsIdentifiers() {
        Lexer lexer = new JavaLexer();
        List<Token> tokens = lexer.tokenize("int answer = 42;");
        Token ident = tokens.stream()
                .filter(t -> t.kind == Token.Kind.IDENTIFIER)
                .findFirst()
                .orElse(null);
        assertNotNull(ident);
        assertEquals("answer", ident.text);
    }

    @Test
    public void languageIsJava() {
        assertEquals(LanguageId.JAVA, new JavaLexer().language());
    }

    @Test
    public void stringLiteralIsString() {
        Lexer lexer = new JavaLexer();
        List<Token> tokens = lexer.tokenize("String s = \"hi\";");
        Token str = tokens.stream()
                .filter(t -> t.kind == Token.Kind.STRING)
                .findFirst()
                .orElse(null);
        assertNotNull(str);
        assertTrue(str.text.contains("hi"));
    }
}
