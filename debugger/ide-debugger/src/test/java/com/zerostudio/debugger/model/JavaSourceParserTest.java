/*
 *  ZeroStudio IDE - JavaSourceParser 单元测试 (Phase G1)
 *
 *  覆盖 JavaSourceParser 对以下场景的解析:
 *    - 带 package 声明的 .java 文件
 *    - 无 package 声明的 .java 文件
 *    - 内部类 (内部类签名带 $)
 *    - 方法行号提取
 *    - 不存在的文件返回 null
 *    - 非法内容返回 null
 *    - .kt 文件回退到 basename
 */

package com.zerostudio.debugger.model;

import static org.junit.Assert.assertEquals;
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
public class JavaSourceParserTest {

    private final JavaSourceParser parser = new JavaSourceParser();

    // ----------------------------------------------------------------
    // 文件解析
    // ----------------------------------------------------------------

    @Test
    public void parse_withPackage_extractsCorrectSignature() throws Exception {
        Path tmp = Files.createTempFile("TestService", ".java");
        Files.writeString(tmp,
            "package com.example.service;\n" +
            "public class TestService {\n" +
            "    public void doIt() {}\n" +
            "}\n");
        try {
            ParsedSource result = parser.parse(tmp.toFile());
            assertNotNull(result);
            assertEquals("com.example.service", result.packageName);
            assertEquals("Lcom/example/service/TestService;", result.topLevelSignature());
            assertEquals(1, result.classes.size());
            assertTrue(result.classes.get(0).isTopLevel);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void parse_withoutPackage_extractsSimpleSignature() throws Exception {
        Path tmp = Files.createTempFile("Hello", ".java");
        Files.writeString(tmp,
            "public class Hello {\n" +
            "    public static void main(String[] args) {}\n" +
            "}\n");
        try {
            ParsedSource result = parser.parse(tmp.toFile());
            assertNotNull(result);
            assertEquals("", result.packageName);
            assertEquals("LHello;", result.topLevelSignature());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void parse_withInnerClass_includesInnerSignature() throws Exception {
        Path tmp = Files.createTempFile("Outer", ".java");
        Files.writeString(tmp,
            "package com.example;\n" +
            "public class Outer {\n" +
            "    public class Inner {\n" +
            "    }\n" +
            "    private static class StaticInner {\n" +
            "    }\n" +
            "}\n");
        try {
            ParsedSource result = parser.parse(tmp.toFile());
            assertNotNull(result);
            assertEquals(3, result.classes.size());
            // Top-level
            assertEquals("Lcom/example/Outer;", result.classes.get(0).signature);
            assertTrue(result.classes.get(0).isTopLevel);
            // Inner classes (non-static)
            assertEquals("Lcom/example/Outer$Inner;", result.classes.get(1).signature);
            // Static inner
            assertEquals("Lcom/example/Outer$StaticInner;", result.classes.get(2).signature);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void parse_withMethods_extractsMethodDeclarations() throws Exception {
        Path tmp = Files.createTempFile("Methods", ".java");
        Files.writeString(tmp,
            "package com.example;\n" +
            "public class Methods {\n" +
            "    public void first() {}\n" +
            "    public int second(String s) { return 0; }\n" +
            "    private static void third() {}\n" +
            "}\n");
        try {
            ParsedSource result = parser.parse(tmp.toFile());
            assertNotNull(result);
            assertEquals(1, result.classes.size());
            SourceClass cls = result.classes.get(0);
            assertEquals(3, cls.methods.size());

            SourceMethod m0 = cls.methods.get(0);
            assertEquals("first", m0.name);
            assertTrue(m0.signature.contains("()"));  // void first()

            SourceMethod m1 = cls.methods.get(1);
            assertEquals("second", m1.name);

            SourceMethod m2 = cls.methods.get(2);
            assertEquals("third", m2.name);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void parse_nonexistentFile_returnsNull() {
        ParsedSource result = parser.parse(new File("/nonexistent/path/Foo.java"));
        assertNull(result);
    }

    @Test
    public void parse_nonJavaFile_returnsNull() throws Exception {
        Path tmp = Files.createTempFile("Test", ".txt");
        Files.writeString(tmp, "not java code {");
        try {
            ParsedSource result = parser.parse(tmp.toFile());
            // JavaParser should fail on non-Java content
            assertNull(result);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ----------------------------------------------------------------
    // 内容解析 (parseContent)
    // ----------------------------------------------------------------

    @Test
    public void parseContent_extractsSignature() {
        String source =
            "package android.app;\n" +
            "public class Activity {\n" +
            "    protected void onCreate() {}\n" +
            "}\n";
        ParsedSource result = parser.parseContent(source);
        assertNotNull(result);
        assertEquals("android.app", result.packageName);
        assertEquals("Landroid/app/Activity;", result.topLevelSignature());
    }

    @Test
    public void parseContent_withEmptyContent_returnsNull() {
        ParsedSource result = parser.parseContent("");
        assertNull(result);
    }

    @Test
    public void parseContent_withGarbage_returnsNull() {
        ParsedSource result = parser.parseContent("{{{{ invalid");
        assertNull(result);
    }

    // ----------------------------------------------------------------
    // 路径字符串解析
    // ----------------------------------------------------------------

    @Test
    public void parse_withKotlinExtension_fallsBackToBasename() throws Exception {
        Path tmp = Files.createTempFile("KotlinFile", ".kt");
        Files.writeString(tmp, "class KotlinFile");
        try {
            ParsedSource result = parser.parse(tmp.toFile());
            // JavaParser doesn't handle Kotlin; basename fallback used
            assertNotNull(result);
            assertEquals("LKotlinFile;", result.topLevelSignature());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ----------------------------------------------------------------
    // 内部类处理
    // ----------------------------------------------------------------

    @Test
    public void parse_deepNestedClass_signatureContainsMultipleDollarSigns() throws Exception {
        Path tmp = Files.createTempFile("A", ".java");
        Files.writeString(tmp,
            "package com.test;\n" +
            "public class A {\n" +
            "    public class B {\n" +
            "        public class C {\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
        try {
            ParsedSource result = parser.parse(tmp.toFile());
            assertNotNull(result);
            assertEquals("Lcom/test/A;", result.classes.get(0).signature);
            assertEquals("Lcom/test/A$B;", result.classes.get(1).signature);
            assertEquals("Lcom/test/A$B$C;", result.classes.get(2).signature);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ----------------------------------------------------------------
    // findMethodAtLine
    // ----------------------------------------------------------------

    @Test
    public void findMethodAtLine_returnsMethodForMatchingLine() throws Exception {
        Path tmp = Files.createTempFile("Foo", ".java");
        Files.writeString(tmp,
            "package p;\n" +
            "public class Foo {\n" +                          // line 2
            "    public void m1() {}\n" +                      // line 3
            "    public void m2() {}\n" +                      // line 4
            "    public void m3() {}\n" +                      // line 5
            "}\n");
        try {
            ParsedSource result = parser.parse(tmp.toFile());
            assertNotNull(result);

            SourceMethod m3 = result.findMethodAtLine(4);
            assertNotNull(m3);
            assertEquals("m2", m3.name);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    public void findMethodAtLine_returnsNullForEmptyFile() {
        ParsedSource result = parser.parseContent("package p;");
        assertNotNull(result);
        assertNull(result.findMethodAtLine(1));
    }

    // ----------------------------------------------------------------
    // 多顶层类 (每个 .java 只能有 1 个,JavaParser 返回第一个)
    // ----------------------------------------------------------------

    @Test
    public void parse_onlyOneTopLevelClass() throws Exception {
        Path tmp = Files.createTempFile("Multi", ".java");
        // Java 允许字段在 class 声明之外,但不允许多个 class 声明在同一个 CompilationUnit 中
        Files.writeString(tmp,
            "package com.example;\n" +
            "class First {}\n" +
            "class Second {}\n");
        try {
            ParsedSource result = parser.parse(tmp.toFile());
            assertNotNull(result);
            // JavaParser 只解析第一个顶层类
            assertEquals("Lcom/example/First;", result.topLevelSignature());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
