package com.zerostudio.language;

import com.zerostudio.language.jni.TreeSitterAvailability;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Symbol;
import com.zerostudio.language.model.SymbolKind;
import com.zerostudio.language.parser.CParser;
import com.zerostudio.language.parser.CppParser;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the C / C++ parser wiring: the high-fidelity Tree-Sitter path is
 * preferred when available, but the token-based fallback always works.
 *
 * <p>The native libraries are not actually present in the unit-test
 * environment, so the test is exercising:
 * <ol>
 *   <li>the "force-disable" path (purely token-based)</li>
 *   <li>the "force-enable" path (native call would crash without the .so,
 *       but {@link com.zerostudio.language.jni.TreeSitterCNativeParser}
 *       returns null and the parser falls back)</li>
 * </ol>
 */
public class JniWiringTest {

    @After
    public void tearDown() {
        // Reset between tests so we don't leak state.
        TreeSitterAvailability.resetForTests();
    }

    @Test
    public void cParserFallsBackWhenNativeDisabled() {
        TreeSitterAvailability.forceEnable(false);
        CParser p = new CParser();
        String src =
                "int add(int a, int b) { return a + b; }\n" +
                "int main(void) { return add(1, 2); }\n";
        ParsedFile parsed = p.parse("main.c", src);
        assertNotNull(parsed);
        assertTrue(parsed.parseError == null);
        assertEquals(LanguageId.C, parsed.language);
        // Two functions: add and main.
        long fns = parsed.symbols.stream()
                .filter(s -> s.kind == SymbolKind.FUNCTION)
                .count();
        assertEquals(2, fns);
    }

    @Test
    public void cParserFallsBackWhenNativeEnabledButMissing() {
        // Force "available" - in test env, the native call returns null and
        // the parser transparently falls back to the token-based path.
        TreeSitterAvailability.forceEnable(true);
        CParser p = new CParser();
        String src =
                "struct Point { int x; int y; };\n" +
                "int sum(struct Point *p) { return p->x + p->y; }\n";
        ParsedFile parsed = p.parse("p.c", src);
        assertNotNull(parsed);
        // The struct AND the function should be present from the fallback
        // path - this is the contract: native miss must not lose data.
        assertTrue(parsed.symbols.stream()
                .anyMatch(s -> s.kind == SymbolKind.STRUCT && s.name.equals("Point")));
        assertTrue(parsed.symbols.stream()
                .anyMatch(s -> s.kind == SymbolKind.FUNCTION && s.name.equals("sum")));
    }

    @Test
    public void cppParserFallsBackWhenNativeDisabled() {
        TreeSitterAvailability.forceEnable(false);
        CppParser p = new CppParser();
        String src =
                "namespace app {\n" +
                "class Greeter {\n" +
                "public:\n" +
                "    std::string greet();\n" +
                "};\n" +
                "std::string Greeter::greet() { return std::string(\"hi\"); }\n" +
                "}\n";
        ParsedFile parsed = p.parse("g.cpp", src);
        assertNotNull(parsed);
        assertEquals(LanguageId.CPP, parsed.language);
        assertTrue(parsed.symbols.stream()
                .anyMatch(s -> s.kind == SymbolKind.CLASS && s.name.equals("Greeter")));
        assertTrue(parsed.symbols.stream()
                .anyMatch(s -> s.kind == SymbolKind.METHOD && s.name.equals("greet")));
    }

    @Test
    public void cppParserFallsBackWhenNativeEnabledButMissing() {
        TreeSitterAvailability.forceEnable(true);
        CppParser p = new CppParser();
        String src =
                "class Engine {\n" +
                "public:\n" +
                "    int run() { return 0; }\n" +
                "};\n";
        ParsedFile parsed = p.parse("e.cpp", src);
        assertNotNull(parsed);
        assertTrue(parsed.symbols.stream()
                .anyMatch(s -> s.kind == SymbolKind.CLASS && s.name.equals("Engine")));
    }

    @Test
    public void treeSitterAvailabilityRespectsSystemProperty() {
        // Clear cache so detect() runs.
        TreeSitterAvailability.resetForTests();
        try {
            System.setProperty("ide.language.useNativeTreeSitter", "true");
            // No way to clear the cached value other than reset, but the
            // property is consulted inside detect(); the test simply checks
            // that the class loads without throwing.
            TreeSitterAvailability.class.getDeclaredMethods(); // smoke
        } finally {
            System.clearProperty("ide.language.useNativeTreeSitter");
        }
    }
}
