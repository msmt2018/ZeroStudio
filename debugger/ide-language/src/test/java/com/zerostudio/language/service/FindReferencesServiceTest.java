package com.zerostudio.language.service;

import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.index.DefaultProjectIndex;
import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.LanguageId;
import org.junit.Before;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class FindReferencesServiceTest {

    private ProjectIndex idx;
    private FindReferencesService svc;

    @Before
    public void setUp() {
        idx = new DefaultProjectIndex();
        // 1. A.java
        ParsedFile a = new ParsedFile("A.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        mkRef("com.x.Foo", 1, 1, 1, 4, Reference.ReferenceKind.IMPORT, "com.x"),
                        mkRef("Foo", 3, 1, 3, 4, Reference.ReferenceKind.CLASS, "com.x"),
                        mkRef("foo", 5, 5, 5, 8, Reference.ReferenceKind.METHOD, "com.x"),
                        mkRef("bar", 7, 5, 7, 8, Reference.ReferenceKind.METHOD, "com.x"),
                        mkRef("Foo", 9, 9, 9, 12, Reference.ReferenceKind.METHOD, "com.x")  // 使用类
                ), "");
        idx.index(a);
        // 2. B.java - 使用 Foo
        ParsedFile b = new ParsedFile("B.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        mkRef("com.x.Foo", 1, 1, 1, 4, Reference.ReferenceKind.IMPORT, "com.x"),
                        mkRef("Foo", 3, 5, 3, 8, Reference.ReferenceKind.METHOD, "com.x"), // 实例化
                        mkRef("foo", 4, 5, 4, 8, Reference.ReferenceKind.METHOD, "com.x")
                ), "");
        idx.index(b);
        // 3. C.kt - 同包，simple 名调用
        ParsedFile c = new ParsedFile("C.kt", LanguageId.KOTLIN, "com.x",
                Arrays.asList(
                        mkRef("Foo", 2, 5, 2, 8, Reference.ReferenceKind.METHOD, "com.x")
                ), "");
        idx.index(c);
        svc = new FindReferencesService(idx);
    }

    private Reference mkRef(String name, int line, int col, int endLine, int endCol, Reference.ReferenceKind kind, String pkg) {
        return new Reference(name,
                new SourceRange(new SourcePosition("?", line, col), new SourcePosition("?", endLine, endCol)),
                kind, pkg, "?", LanguageId.JAVA);
    }

    @Test
    public void findBySimpleNameIncludesAllOccurrences() {
        List<FindReferencesService.Match> refs = svc.findByName("Foo", true);
        // A.java: CLASS Foo, METHOD Foo
        // B.java: METHOD Foo
        // C.kt: METHOD Foo
        assertTrue("expected >= 4, got " + refs.size(), refs.size() >= 4);
    }

    @Test
    public void findAtClassDeclarationIncludesUsages() {
        // 3,1 是 A.java 中 class Foo 声明处
        List<FindReferencesService.Match> refs = svc.findReferences("A.java", 3, 1, false);
        assertTrue("expected usages, got " + refs.size(), refs.size() >= 1);
    }

    @Test
    public void findByNameWithIncludeFalseExcludesDeclarations() {
        List<FindReferencesService.Match> refs = svc.findByName("Foo", false);
        // 不算 CLASS 声明
        for (FindReferencesService.Match m : refs) {
            assertNotEquals(Reference.ReferenceKind.CLASS, m.reference.kind);
        }
    }

    @Test
    public void countReturnsSize() {
        assertEquals(svc.findByName("foo", true).size(), svc.count("A.java", 5, 5));
    }

    @Test
    public void findsByFqn() {
        List<FindReferencesService.Match> refs = svc.findByName("com.x.Foo", true);
        assertTrue(refs.size() >= 1);
    }

    @Test
    public void emptyIndexReturnsEmpty() {
        FindReferencesService empty = new FindReferencesService(new ProjectIndex());
        assertTrue(empty.findByName("Foo", true).isEmpty());
    }

    @Test
    public void nullNameReturnsEmpty() {
        assertTrue(svc.findByName(null, true).isEmpty());
        assertTrue(svc.findByName("", true).isEmpty());
    }

    @Test
    public void nonExistentFileReturnsEmpty() {
        assertTrue(svc.findReferences("Unknown.java", 1, 1, true).isEmpty());
    }

    @Test
    public void deduplicatesByLocation() {
        List<FindReferencesService.Match> refs = svc.findByName("Foo", true);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (FindReferencesService.Match m : refs) {
            String key = m.file + ":" + m.line() + ":" + m.column();
            assertTrue("duplicate: " + key, seen.add(key));
        }
    }
}
