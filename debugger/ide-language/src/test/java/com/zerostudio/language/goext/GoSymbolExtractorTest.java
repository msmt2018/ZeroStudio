package com.zerostudio.language.goext;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoSymbolExtractorTest {

    @Test
    public void extractsPackageAndImports() {
        GoSymbolExtractor ext = new GoSymbolExtractor();
        String src = "package main\n" +
                "import \"fmt\"\n" +
                "import os \"os\"\n" +
                "import (\n" +
                "    \"strings\"\n" +
                "    io \"io/ioutil\"\n" +
                ")\n";
        ParsedFile pf = ext.extract("main.go", src);
        assertEquals("main", pf.packageName);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("main") && r.kind == Reference.ReferenceKind.TYPE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("fmt") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("os") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("strings") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("io/ioutil") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("os") && r.kind == Reference.ReferenceKind.TYPE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("io") && r.kind == Reference.ReferenceKind.TYPE));
    }

    @Test
    public void extractsTypeStructAndInterface() {
        GoSymbolExtractor ext = new GoSymbolExtractor();
        String src = "package x\n" +
                "type User struct { Name string }\n" +
                "type Stringer interface { String() string }\n" +
                "type Counter int\n";
        ParsedFile pf = ext.extract("types.go", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("User") && r.kind == Reference.ReferenceKind.CLASS));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Stringer") && r.kind == Reference.ReferenceKind.CLASS));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Counter") && r.kind == Reference.ReferenceKind.TYPE));
    }

    @Test
    public void extractsFunctionAndMethod() {
        GoSymbolExtractor ext = new GoSymbolExtractor();
        String src = "package x\n" +
                "func NewUser() *User { return nil }\n" +
                "func (u *User) Greet() string { return \"hi\" }\n" +
                "func (User) Static() {}\n";
        ParsedFile pf = ext.extract("methods.go", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("NewUser") && r.kind == Reference.ReferenceKind.METHOD));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Greet") && r.kind == Reference.ReferenceKind.METHOD));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Static") && r.kind == Reference.ReferenceKind.METHOD));
    }

    @Test
    public void extractsVarAndConst() {
        GoSymbolExtractor ext = new GoSymbolExtractor();
        String src = "package x\n" +
                "var count int\n" +
                "const MaxSize = 100\n";
        ParsedFile pf = ext.extract("vars.go", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("count") && r.kind == Reference.ReferenceKind.VARIABLE));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("MaxSize") && r.kind == Reference.ReferenceKind.VARIABLE));
    }

    @Test
    public void handlesEmptyFile() {
        ParsedFile pf = new GoSymbolExtractor().extract("a.go", "");
        assertNotNull(pf);
        assertEquals(LanguageId.GO, pf.language);
        assertTrue(pf.references.isEmpty());
    }
}
