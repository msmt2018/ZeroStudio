package com.zerostudio.language;

import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;
import com.zerostudio.language.parser.CParser;
import com.zerostudio.language.parser.CppParser;
import com.zerostudio.language.parser.KotlinParser;
import com.zerostudio.language.parser.Parser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class LanguageParsersTest {

    @Test
    public void kotlinParserFindsFunctions() {
        Parser p = new KotlinParser();
        String src =
                "package com.example\n" +
                "class Greeter(val name: String) {\n" +
                "    fun greet(): String = \"hi $name\"\n" +
                "}\n" +
                "fun topLevel(): Int = 42\n";
        ParsedFile parsed = p.parse("G.kt", src);
        assertTrue(parsed.symbols.stream().anyMatch(s -> s.kind == SymbolKind.CLASS
                && s.name.equals("Greeter")));
        assertTrue(parsed.symbols.stream().anyMatch(s -> s.kind == SymbolKind.METHOD
                && s.name.equals("greet")));
        assertTrue(parsed.symbols.stream().anyMatch(s -> s.kind == SymbolKind.METHOD
                && s.name.equals("topLevel")));
    }

    @Test
    public void cParserFindsFunctions() {
        Parser p = new CParser();
        String src =
                "int add(int a, int b) { return a + b; }\n" +
                "int main(void) { return add(1, 2); }\n";
        ParsedFile parsed = p.parse("main.c", src);
        List<Symbol> fns = parsed.symbols;
        assertTrue(fns.stream().anyMatch(s -> s.kind == SymbolKind.FUNCTION
                && s.name.equals("add")));
        assertTrue(fns.stream().anyMatch(s -> s.kind == SymbolKind.FUNCTION
                && s.name.equals("main")));
    }

    @Test
    public void cppParserFindsClassAndMethod() {
        Parser p = new CppParser();
        String src =
                "namespace app {\n" +
                "class Greeter {\n" +
                "public:\n" +
                "    std::string greet();\n" +
                "};\n" +
                "std::string Greeter::greet() { return std::string(\"hi\"); }\n" +
                "}\n";
        ParsedFile parsed = p.parse("g.cpp", src);
        assertTrue(parsed.symbols.stream().anyMatch(s -> s.kind == SymbolKind.CLASS
                && s.name.equals("Greeter")));
        assertTrue(parsed.symbols.stream().anyMatch(s -> s.kind == SymbolKind.METHOD
                && s.name.equals("greet")));
    }
}
