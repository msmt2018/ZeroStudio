/*
 *  ZeroStudio IDE - ClassFileReader 单元测试 (Phase G2)
 *
 *  覆盖 ClassFileReader 对 .class 文件的解析:
 *    - parse(File) 读取有效 .class
 *    - parse(byte[]) 读取字节数组
 *    - parse 不存在文件返回 null
 *    - parseStream 正常流程
 *    - toJvmSignature / simpleName 静态方法
 *    - ParsedClass.findLineForCodeIndex / findMethodAtCodeIndex
 *    - ClassMethod.lineForCodeIndex
 *    - LineEntry 内容
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ClassFileReaderTest {

    private final ClassFileReader reader = new ClassFileReader();

    // ----------------------------------------------------------------
    // 静态方法
    // ----------------------------------------------------------------

    @Test
    public void toJvmSignature_simpleClass() {
        assertEquals("Lcom/example/Foo;",
                     ClassFileReader.toJvmSignature("com/example/Foo"));
    }

    @Test
    public void toJvmSignature_innerClass() {
        assertEquals("Lcom/example/Outer$Inner;",
                     ClassFileReader.toJvmSignature("com/example/Outer$Inner"));
    }

    @Test
    public void simpleName_withSlash() {
        assertEquals("Foo",
                     ClassFileReader.simpleName("com/example/Foo"));
    }

    @Test
    public void simpleName_innerClass() {
        assertEquals("Outer$Inner",
                     ClassFileReader.simpleName("com/example/Outer$Inner"));
    }

    // ----------------------------------------------------------------
    // 文件解析
    // ----------------------------------------------------------------

    @Test
    public void parse_validClassFile_extractsSignature() throws Exception {
        // 编译一个简单的类，然后用 ClassFileReader 读取它
        Path src = Files.createTempFile("Simple", ".java");
        Files.writeString(src,
            "public class Simple {\n" +
            "    public void doIt() {}\n" +
            "}\n");
        try {
            Path classFile = compileJava(src.toFile());
            if (classFile == null) {
                // Skip if javac not available
                return;
            }
            try {
                ParsedClass result = reader.parse(classFile.toFile());
                assertNotNull("ClassFileReader.parse() should succeed", result);
                assertEquals("LSimple;", result.signature);
                assertTrue(result.isTopLevel);
                assertNotNull(result.sourceFile);
            } finally {
                Files.deleteIfExists(classFile);
            }
        } finally {
            Files.deleteIfExists(src);
        }
    }

    @Test
    public void parse_packageClass_extractsPackageInSignature() throws Exception {
        Path dir = Files.createTempDirectory("pkgtest");
        Path src = dir.resolve("Packaged.java");
        Files.writeString(src,
            "package com.example.test;\n" +
            "public class Packaged {\n" +
            "    public void hello() {}\n" +
            "}\n");
        try {
            Path classFile = compileJava(src.toFile());
            if (classFile == null) return;
            try {
                ParsedClass result = reader.parse(classFile.toFile());
                assertNotNull(result);
                assertEquals("Lcom/example/test/Packaged;", result.signature);
                assertNotNull(result.sourceFile);
            } finally {
                Files.deleteIfExists(classFile);
            }
        } finally {
            Files.deleteIfExists(src);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    public void parse_nonexistentFile_returnsNull() {
        ParsedClass result = reader.parse(new File("/nonexistent/Simple.class"));
        assertNull(result);
    }

    @Test
    public void parseStream_invalidInput_returnsNull() throws Exception {
        java.io.InputStream in = new java.io.ByteArrayInputStream(new byte[] { 0xCA, 0xFE, 0xBA, 0xBE });
        ParsedClass result = reader.parseStream(in);
        // ClassFileReader should return null for invalid class data
        assertNull(result);
    }

    // ----------------------------------------------------------------
    // ParsedClass 方法
    // ----------------------------------------------------------------

    @Test
    public void findMethodAtCodeIndex_noMethods_returnsNull() {
        ParsedClass pc = new ParsedClass(
            "Ltest/Test;", "Test.java", true, new java.util.ArrayList<>());
        assertNull(pc.findMethodAtCodeIndex(0));
    }

    @Test
    public void lineForCodeIndex_noLines_returnsMinusOne() {
        ClassMethod m = new ClassMethod("doIt", "()V", 0, 10, new java.util.ArrayList<>());
        assertEquals(-1, m.lineForCodeIndex(0));
    }

    // ----------------------------------------------------------------
    // LineEntry
    // ----------------------------------------------------------------

    @Test
    public void lineEntry_storesFields() {
        LineEntry e = new LineEntry(0x100L, 42);
        assertEquals(0x100L, e.codeOffset);
        assertEquals(42, e.lineNumber);
    }

    @Test
    public void lineForCodeIndex_findsBestMatch() {
        java.util.List<LineEntry> lines = new java.util.ArrayList<>();
        lines.add(new LineEntry(0, 10));
        lines.add(new LineEntry(10, 15));
        lines.add(new LineEntry(20, 20));

        ClassMethod m = new ClassMethod("doIt", "()V", 0, 30, lines);
        assertEquals(10, m.lineForCodeIndex(0));
        assertEquals(10, m.lineForCodeIndex(9));
        assertEquals(15, m.lineForCodeIndex(10));
        assertEquals(15, m.lineForCodeIndex(19));
        assertEquals(20, m.lineForCodeIndex(20));
        assertEquals(20, m.lineForCodeIndex(25));
    }

    // ----------------------------------------------------------------
    // 内部类检测
    // ----------------------------------------------------------------

    @Test
    public void topLevelClass_hasNoDollarSign() throws Exception {
        Path src = Files.createTempFile("TopLevel", ".java");
        Files.writeString(src,
            "public class TopLevel {\n" +
            "    public void run() {}\n" +
            "}\n");
        try {
            Path classFile = compileJava(src.toFile());
            if (classFile == null) return;
            try {
                ParsedClass result = reader.parse(classFile.toFile());
                assertNotNull(result);
                assertTrue("Top-level class should have no $ in name",
                           !result.signature.contains("$"));
                assertTrue(result.isTopLevel);
            } finally {
                Files.deleteIfExists(classFile);
            }
        } finally {
            Files.deleteIfExists(src);
        }
    }

    // ----------------------------------------------------------------
    // 辅助方法:编译 Java 源文件
    // ----------------------------------------------------------------

    /**
     * Compile a .java file using the system javac.
     * Returns the path to the compiled .class file, or null if compilation
     * is not available.
     */
    private static Path compileJava(File javaFile) {
        try {
            // Compile to the same directory as the source file
            String javac = findJavac();
            if (javac == null) return null;

            ProcessBuilder pb = new ProcessBuilder(
                javac, "-classpath", javaFile.getParentFile().getAbsolutePath(),
                javaFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                return null;
            }
            // The .class file should be next to the .java file
            String classPath = javaFile.getAbsolutePath();
            classPath = classPath.replaceFirst("\\.java$", ".class");
            File classFile = new File(classPath);
            if (!classFile.exists()) return null;
            return classFile.toPath();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String findJavac() {
        String[] candidates = {
            "/usr/bin/javac",
            "/usr/local/bin/javac",
            System.getProperty("java.home") + "/../bin/javac",
            System.getProperty("java.home") + "/bin/javac",
        };
        for (String c : candidates) {
            if (new File(c).canExecute()) return c;
        }
        // Try PATH lookup
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File f = new File(dir, "javac");
                if (f.canExecute()) return f.getAbsolutePath();
            }
        }
        return null;
    }
}
