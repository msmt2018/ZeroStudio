package com.zerostudio.language.kotlin;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import org.junit.Test;
import static org.junit.Assert.*;

public class KotlinSymbolExtractorTest {
    @Test
    public void extractsPackage() {
        KotlinSymbolExtractor ext = new KotlinSymbolExtractor();
        ParsedFile pf = ext.extract("M.kt", "package com.example\n");
        assertEquals("com.example", pf.packageName);
        assertEquals(LanguageId.KOTLIN, pf.language);
    }

    @Test
    public void extractsImports() {
        KotlinSymbolExtractor ext = new KotlinSymbolExtractor();
        String src = "package com.example\n" +
                "import android.os.Bundle\n" +
                "class MainActivity\n" +
                "fun onCreate() {}\n";
        ParsedFile pf = ext.extract("M.kt", src);
        assertTrue(pf.references.stream().anyMatch(r ->
                r.kind == Reference.ReferenceKind.IMPORT && r.name.equals("android.os.Bundle")));
        assertTrue(pf.references.stream().anyMatch(r ->
                r.kind == Reference.ReferenceKind.CLASS && r.name.equals("MainActivity")));
        assertTrue(pf.references.stream().anyMatch(r ->
                r.kind == Reference.ReferenceKind.METHOD && r.name.equals("onCreate")));
    }

    @Test
    public void handlesEmptyFile() {
        ParsedFile pf = new KotlinSymbolExtractor().extract("X.kt", "");
        assertNotNull(pf);
        assertEquals(LanguageId.KOTLIN, pf.language);
        assertTrue(pf.references.isEmpty());
    }

    @Test
    public void handlesCommentsAndBlankLines() {
        String src = "// 注释行\n" +
                "\n" +
                "/* 块注释 */\n" +
                "package com.x // 尾部注释\n" +
                "\n" +
                "class Foo // 另一行\n";
        ParsedFile pf = new KotlinSymbolExtractor().extract("X.kt", src);
        assertEquals("com.x", pf.packageName);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("Foo")));
    }

    @Test
    public void handlesObjectKeyword() {
        String src = "package com.x\nobject Singleton { fun run() {} }\n";
        ParsedFile pf = new KotlinSymbolExtractor().extract("X.kt", src);
        assertTrue(pf.references.stream().anyMatch(r ->
                r.kind == Reference.ReferenceKind.CLASS && r.name.equals("Singleton")));
    }

    @Test
    public void handlesUnicodeInCode() {
        String src = "package com.x\nclass 用户 { fun 登录() {} }\n";
        ParsedFile pf = new KotlinSymbolExtractor().extract("X.kt", src);
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("用户")));
        assertTrue(pf.references.stream().anyMatch(r -> r.name.equals("登录")));
    }
}
