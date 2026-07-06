package com.zerostudio.language.python;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import org.junit.Test;
import static org.junit.Assert.*;

public class PythonSymbolExtractorTest {

    @Test
    public void extractsClassAndFunctions() {
        PythonSymbolExtractor ext = new PythonSymbolExtractor();
        String src = "from typing import List\n" +
                "import os\n" +
                "class Foo:\n" +
                "    def bar(self, x: int) -> int:\n" +
                "        return x\n" +
                "def baz():\n" +
                "    pass\n";
        ParsedFile pf = ext.extract("foo.py", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Foo") && r.kind == Reference.ReferenceKind.CLASS));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("bar") && r.kind == Reference.ReferenceKind.METHOD));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("baz") && r.kind == Reference.ReferenceKind.METHOD));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("typing") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("List") && r.kind == Reference.ReferenceKind.IMPORT));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("os") && r.kind == Reference.ReferenceKind.IMPORT));
    }

    @Test
    public void extractsAsyncDef() {
        PythonSymbolExtractor ext = new PythonSymbolExtractor();
        String src = "async def fetch():\n    pass\n";
        ParsedFile pf = ext.extract("foo.py", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("fetch")));
    }

    @Test
    public void extractsDecorator() {
        PythonSymbolExtractor ext = new PythonSymbolExtractor();
        String src = "@staticmethod\ndef my_method(): pass\n";
        ParsedFile pf = ext.extract("foo.py", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("staticmethod")));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("my_method")));
    }

    @Test
    public void extractsClassWithInheritance() {
        PythonSymbolExtractor ext = new PythonSymbolExtractor();
        String src = "class MyList(list):\n    pass\n";
        ParsedFile pf = ext.extract("foo.py", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("MyList")));
    }

    @Test
    public void extractsTopLevelAssignment() {
        PythonSymbolExtractor ext = new PythonSymbolExtractor();
        String src = "max_count = 10\nPI = 3.14\nself.value = 5\n";
        ParsedFile pf = ext.extract("foo.py", src);
        // max_count 应该是 variable，PI 常量大写应该跳过，self 应该跳过
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("max_count")));
        assertFalse(pf.references.stream().anyMatch(r -> r.name.equals("PI")));
        assertFalse(pf.references.stream().anyMatch(r -> r.name.equals("self")));
    }

    @Test
    public void handlesEmptyFile() {
        ParsedFile pf = new PythonSymbolExtractor().extract("x.py", "");
        assertNotNull(pf);
        assertEquals(LanguageId.PYTHON, pf.language);
        assertTrue(pf.references.isEmpty());
    }
}
