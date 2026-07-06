/*
 *  ZeroStudio IDE - ReferenceFinder 单元测试 (Phase G.5)
 *
 *  覆盖 ReferenceFinder:
 *    - addSource / buildIndex
 *    - peekMethodDefinition / peekClassDefinition
 *    - findUsages 找到方法调用
 *    - findClassUsages 找到类型引用
 */
package com.zerostudio.debugger.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ReferenceFinderTest {

    @Test
    public void peekMethodDefinition_findsIt() throws Exception {
        AstIndex idx = new AstIndex();
        ReferenceFinder finder = new ReferenceFinder(idx);
        Path tmp = Files.createTempFile("Greeter", ".java");
        Files.writeString(tmp,
            "package com.example;\n" +
            "public class Greeter {\n" +
            "    public void greet() {}\n" +
            "}\n");
        try {
            finder.addSource(tmp.toString());
            finder.buildIndex();
            AstIndex.Definition def = finder.peekMethodDefinition(
                    "Lcom/example/Greeter;", "greet");
            assertNotNull(def);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void peekClassDefinition_findsIt() throws Exception {
        AstIndex idx = new AstIndex();
        ReferenceFinder finder = new ReferenceFinder(idx);
        Path tmp = Files.createTempFile("Thing", ".java");
        Files.writeString(tmp,
            "package com.example;\n" +
            "public class Thing {}\n");
        try {
            finder.addSource(tmp.toString());
            finder.buildIndex();
            AstIndex.Definition def = finder.peekClassDefinition("Lcom/example/Thing;");
            assertNotNull(def);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void findUsages_methodCall() throws Exception {
        AstIndex idx = new AstIndex();
        ReferenceFinder finder = new ReferenceFinder(idx);
        Path def = Files.createTempFile("Caller", ".java");
        Files.writeString(def,
            "package com.example;\n" +
            "public class Caller {\n" +
            "    public void run() {\n" +
            "        com.example.Greeter g = new com.example.Greeter();\n" +
            "        g.greet();\n" +
            "    }\n" +
            "}\n");
        try {
            finder.addSource(def.toString());
            finder.buildIndex();
            java.util.List<AstIndex.Reference> usages = finder.findUsages(
                    "Lcom/example/Greeter;", "greet");
            assertFalse("expected at least one usage of greet", usages.isEmpty());
        } finally {
            Files.deleteIfExists(def);
        }
    }

    @Test
    public void findClassUsages_includesObjectCreation() throws Exception {
        AstIndex idx = new AstIndex();
        ReferenceFinder finder = new ReferenceFinder(idx);
        Path def = Files.createTempFile("Maker", ".java");
        Files.writeString(def,
            "package com.example;\n" +
            "public class Maker {\n" +
            "    void build() {\n" +
            "        Widget w = new Widget();\n" +
            "    }\n" +
            "}\n");
        try {
            finder.addSource(def.toString());
            finder.buildIndex();
            java.util.List<AstIndex.Reference> usages = finder.findClassUsages(
                    "Lcom/example/Widget;");
            assertTrue("expected at least one usage of Widget", usages.size() >= 1);
        } finally {
            Files.deleteIfExists(def);
        }
    }

    @Test
    public void sourceFileCount() throws Exception {
        AstIndex idx = new AstIndex();
        ReferenceFinder finder = new ReferenceFinder(idx);
        assertTrue(finder.sourceFileCount() == 0);
        Path tmp = Files.createTempFile("X", ".java");
        Files.writeString(tmp, "package com.example;\npublic class X {}\n");
        try {
            finder.addSource(tmp.toString());
            assertTrue(finder.sourceFileCount() == 1);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
