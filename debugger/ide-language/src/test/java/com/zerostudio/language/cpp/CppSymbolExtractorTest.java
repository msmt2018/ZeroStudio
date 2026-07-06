package com.zerostudio.language.cpp;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import org.junit.Test;
import static org.junit.Assert.*;

public class CppSymbolExtractorTest {

    @Test
    public void extractsClassAndFunction() {
        CppSymbolExtractor ext = new CppSymbolExtractor();
        String src = "#include <iostream>\n" +
                "class Foo {\n" +
                "    int bar(int x) { return x; }\n" +
                "};\n";
        ParsedFile pf = ext.extract("F.cpp", src);
        assertTrue(pf.references.stream().anyMatch(r ->
                r.kind == Reference.ReferenceKind.IMPORT && r.name.equals("iostream")));
        assertTrue(pf.references.stream().anyMatch(r ->
                r.kind == Reference.ReferenceKind.CLASS && r.name.equals("Foo")));
        assertTrue(pf.references.stream().anyMatch(r ->
                r.kind == Reference.ReferenceKind.METHOD && r.name.equals("bar")));
    }

    @Test
    public void tracksNamespace() {
        CppSymbolExtractor ext = new CppSymbolExtractor();
        String src = "namespace myns {\n" +
                "class Helper { void run(); };\n" +
                "}\n";
        ParsedFile pf = ext.extract("F.cpp", src);
        assertEquals("myns", pf.packageName);
        assertTrue(pf.references.stream().anyMatch(r ->
                r.kind == Reference.ReferenceKind.CLASS && r.name.equals("myns::Helper")));
    }

    @Test
    public void skipsControlFlowKeywords() {
        CppSymbolExtractor ext = new CppSymbolExtractor();
        String src = "int main() {\n" +
                "    if (true) return 0;\n" +
                "    for (int i=0; i<10; i++) {}\n" +
                "}\n";
        ParsedFile pf = ext.extract("F.cpp", src);
        // main should be detected; if/for/return should not
        assertTrue(pf.references.stream().anyMatch(r ->
                r.kind == Reference.ReferenceKind.METHOD && r.name.equals("main")));
        assertFalse(pf.references.stream().anyMatch(r -> r.name.equals("if")));
        assertFalse(pf.references.stream().anyMatch(r -> r.name.equals("for")));
        assertFalse(pf.references.stream().anyMatch(r -> r.name.equals("return")));
    }

    @Test
    public void handlesTypedefAndUsing() {
        CppSymbolExtractor ext = new CppSymbolExtractor();
        String src = "typedef unsigned int uint32;\n" +
                "using namespace std;\n";
        ParsedFile pf = ext.extract("F.cpp", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("uint32")));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("std")));
    }

    @Test
    public void handlesEmptyFile() {
        CppSymbolExtractor ext = new CppSymbolExtractor();
        ParsedFile pf = ext.extract("F.cpp", "");
        assertNotNull(pf);
        assertEquals(LanguageId.CPP, pf.language);
    }
}
