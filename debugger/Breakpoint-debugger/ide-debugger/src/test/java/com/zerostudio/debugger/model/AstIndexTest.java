/*
 *  ZeroStudio IDE - AstIndex 单元测试 (Phase G.4)
 *
 *  覆盖 AstIndex 跨文件符号索引:
 *    - indexSource / peekDefinition / findUsages
 *    - removeSource 失效
 *    - clear
 *    - classKey / methodKey 静态方法
 *    - stats: definitionCount / referenceCount / indexedFileCount
 */
package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AstIndexTest {

    @Test
    public void indexSource_classDefinition() throws Exception {
        AstIndex idx = new AstIndex();
        Path tmp = Files.createTempFile("Foo", ".java");
        Files.writeString(tmp,
            "package com.example;\n" +
            "public class Foo {\n" +
            "    public void doIt() {}\n" +
            "}\n");
        try {
            idx.indexSource(tmp.toString());
            AstIndex.Definition def = idx.peekDefinition("Lcom/example/Foo;");
            assertNotNull(def);
            assertEquals(AstIndex.Definition.Kind.CLASS, def.kind);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void indexSource_methodDefinition() throws Exception {
        AstIndex idx = new AstIndex();
        Path tmp = Files.createTempFile("Bar", ".java");
        Files.writeString(tmp,
            "package com.example;\n" +
            "public class Bar {\n" +
            "    public int compute(int x) { return x + 1; }\n" +
            "}\n");
        try {
            idx.indexSource(tmp.toString());
            AstIndex.Definition def = idx.peekDefinition("Lcom/example/Bar;.compute");
            assertNotNull(def);
            assertEquals(AstIndex.Definition.Kind.METHOD, def.kind);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void removeSource_removesAllSymbols() throws Exception {
        AstIndex idx = new AstIndex();
        Path tmp = Files.createTempFile("Baz", ".java");
        Files.writeString(tmp,
            "package com.example;\n" +
            "public class Baz { public void go() {} }\n");
        try {
            idx.indexSource(tmp.toString());
            assertEquals(1, idx.indexedFileCount());
            idx.removeSource(tmp.toString());
            assertEquals(0, idx.indexedFileCount());
            assertNull(idx.peekDefinition("Lcom/example/Baz;"));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void clear_emptiesEverything() throws Exception {
        AstIndex idx = new AstIndex();
        Path tmp = Files.createTempFile("Qux", ".java");
        Files.writeString(tmp,
            "package com.example;\n" +
            "public class Qux {}\n");
        try {
            idx.indexSource(tmp.toString());
            idx.clear();
            assertEquals(0, idx.definitionCount());
            assertEquals(0, idx.indexedFileCount());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void classKey_andMethodKey() {
        assertEquals("Lcom/example/Foo;", AstIndex.classKey("Lcom/example/Foo;"));
        assertEquals("Lcom/example/Foo;.doIt",
                AstIndex.methodKey("Lcom/example/Foo;", "doIt"));
    }

    @Test
    public void addReference_findUsages() {
        AstIndex idx = new AstIndex();
        idx.addReference(new AstIndex.Reference(
                "Lcom/example/Foo;.bar", "/src/Foo.java", 42, 4));
        assertEquals(1, idx.findUsages("Lcom/example/Foo;.bar").size());
        assertEquals(0, idx.findUsages("Lcom/example/Foo;.nope").size());
    }

    @Test
    public void nonExistent_returnsNull() {
        AstIndex idx = new AstIndex();
        assertNull(idx.peekDefinition("Lcom/missing/Missing;"));
    }
}
