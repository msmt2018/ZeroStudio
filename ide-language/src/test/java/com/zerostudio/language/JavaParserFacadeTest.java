package com.zerostudio.language;

import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;
import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.parser.Parser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JavaParserFacadeTest {

    @Test
    public void parsesSimpleClass() {
        Parser p = new JavaParserFacade();
        String src =
                "package com.example;\n" +
                "public class Greeter {\n" +
                "    private final String name;\n" +
                "    public Greeter(String name) { this.name = name; }\n" +
                "    public String greet() { return \"hi \" + name; }\n" +
                "}\n";
        ParsedFile parsed = p.parse("Greeter.java", src);
        assertNull(parsed.parseError);
        List<Symbol> syms = parsed.symbols;
        assertTrue(syms.stream().anyMatch(s -> s.kind == SymbolKind.CLASS
                && s.name.equals("Greeter")));
        assertTrue(syms.stream().anyMatch(s -> s.kind == SymbolKind.METHOD
                && s.name.equals("greet")));
        assertTrue(syms.stream().anyMatch(s -> s.kind == SymbolKind.CONSTRUCTOR));
        assertTrue(syms.stream().anyMatch(s -> s.kind == SymbolKind.FIELD
                && s.name.equals("name")));
    }

    @Test
    public void findsCallReferences() {
        Parser p = new JavaParserFacade();
        String src =
                "class Calc {\n" +
                "    int add(int a, int b) { return a + b; }\n" +
                "    int test() { return add(1, 2); }\n" +
                "}\n";
        ParsedFile parsed = p.parse("Calc.java", src);
        List<Reference> calls = parsed.references;
        assertTrue(calls.stream().anyMatch(r -> r.name.equals("add")
                && r.kind == Reference.ReferenceKind.CALL));
    }
}
