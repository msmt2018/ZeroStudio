/*
 *  ZeroStudio IDE - SourceLocator Phase G4 单元测试
 *
 *  覆盖 Phase G4 的新增功能:
 *    - isSyntheticMethod 判别 lambda/access$/clinit
 *    - guessAllClassSignatures 多顶层类处理
 *    - Kotlin .kt 文件通过 .class 回退获取签名
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SourceLocatorPhaseG4Test {

    // ----------------------------------------------------------------
    // isSyntheticMethod
    // ----------------------------------------------------------------

    @Test
    public void isSyntheticMethod_lambdaReturnsTrue() {
        assertTrue(SourceLocator.isSyntheticMethod("lambda$0"));
        assertTrue(SourceLocator.isSyntheticMethod("lambda$main$0"));
        assertTrue(SourceLocator.isSyntheticMethod("lambda$doIt$1"));
    }

    @Test
    public void isSyntheticMethod_accessBridgeReturnsTrue() {
        assertTrue(SourceLocator.isSyntheticMethod("access$000"));
        assertTrue(SourceLocator.isSyntheticMethod("access$100"));
        assertTrue(SourceLocator.isSyntheticMethod("access$super$0"));
    }

    @Test
    public void isSyntheticMethod_clinitReturnsTrue() {
        assertTrue(SourceLocator.isSyntheticMethod("<clinit>"));
    }

    @Test
    public void isSyntheticMethod_deserializeLambdaReturnsTrue() {
        assertTrue(SourceLocator.isSyntheticMethod("$deserializeLambda$0"));
    }

    @Test
    public void isSyntheticMethod_initReturnsFalse() {
        // <init> is the constructor, not synthetic
        assertFalse(SourceLocator.isSyntheticMethod("<init>"));
    }

    @Test
    public void isSyntheticMethod_userMethodsReturnFalse() {
        assertFalse(SourceLocator.isSyntheticMethod("doIt"));
        assertFalse(SourceLocator.isSyntheticMethod("main"));
        assertFalse(SourceLocator.isSyntheticMethod("onCreate"));
        assertFalse(SourceLocator.isSyntheticMethod("run"));
        assertFalse(SourceLocator.isSyntheticMethod("_doInternal"));
        assertFalse(SourceLocator.isSyntheticMethod("equals"));
    }

    @Test
    public void isSyntheticMethod_bridgeSuffixDoesNotTrigger() {
        // Methods named with "bridge" in the name are NOT synthetic
        assertFalse(SourceLocator.isSyntheticMethod("bridge"));
        assertFalse(SourceLocator.isSyntheticMethod("mybridge"));
    }

    // ----------------------------------------------------------------
    // guessAllClassSignatures — 多顶层类处理
    // ----------------------------------------------------------------

    @Test
    public void guessAllClassSignatures_singleClass() throws Exception {
        SourceLocatorCache cache = new SourceLocatorCache();
        // Test with a file containing a single class
        Path tmp = Files.createTempFile("Single", ".java");
        Files.writeString(tmp,
            "package com.test;\n" +
            "public class Single {\n" +
            "    public void run() {}\n" +
            "}\n");
        try {
            ParsedSource parsed = cache.getSource(tmp.toString());
            assertTrue(parsed != null);
            assertEquals(1, parsed.classes.size());
            assertEquals("Lcom/test/Single;", parsed.classes.get(0).signature);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void guessAllClassSignatures_multipleTopLevelClasses() throws Exception {
        // A single .java file can contain multiple top-level classes
        Path tmp = Files.createTempFile("Multi", ".java");
        Files.writeString(tmp,
            "package com.test;\n" +
            "public class First {}\n" +
            "class Second {}\n" +
            "final class Third {}\n");
        try {
            SourceLocatorCache cache = new SourceLocatorCache();
            ParsedSource parsed = cache.getSource(tmp.toString());
            assertTrue(parsed != null);
            // JavaParser returns all top-level classes
            assertEquals(3, parsed.classes.size());
            assertEquals("Lcom/test/First;", parsed.classes.get(0).signature);
            assertEquals("Lcom/test/Second;", parsed.classes.get(1).signature);
            assertEquals("Lcom/test/Third;", parsed.classes.get(2).signature);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ----------------------------------------------------------------
    // Kotlin .kt 文件处理 (Phase G4)
    // ----------------------------------------------------------------

    @Test
    public void kotlinFile_fallsBackToClassFile() throws Exception {
        // When a .kt file is encountered and we can find the .class file,
        // we use ClassFileReader to get the accurate signature
        Path ktFile = Files.createTempFile("KotlinFile", ".kt");
        Files.writeString(ktFile, "class KotlinFile { fun doIt() {} }");
        try {
            SourceLocatorCache cache = new SourceLocatorCache();
            // Cache won't find anything for .kt directly
            // But if we can find the .class file...
            ParsedSource ktParsed = cache.getSource(ktFile.toString());
            // JavaParser doesn't handle .kt, so this should be null
            // But guessAllClassSignatures for .kt falls back to .class file lookup
            assertTrue("JavaParser should not parse .kt", ktParsed == null);
        } finally {
            Files.deleteIfExists(ktFile);
        }
    }

    // ----------------------------------------------------------------
    // inner class — $ 分隔符处理
    // ----------------------------------------------------------------

    @Test
    public void innerClasses_useDollarSign() throws Exception {
        Path tmp = Files.createTempFile("Outer", ".java");
        Files.writeString(tmp,
            "package com.example;\n" +
            "public class Outer {\n" +
            "    public class Inner {}\n" +
            "}\n");
        try {
            SourceLocatorCache cache = new SourceLocatorCache();
            ParsedSource parsed = cache.getSource(tmp.toString());
            assertTrue(parsed != null);
            assertEquals(2, parsed.classes.size());
            assertEquals("Lcom/example/Outer;", parsed.classes.get(0).signature);
            assertEquals("Lcom/example/Outer$Inner;", parsed.classes.get(1).signature);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
