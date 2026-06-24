package com.zerostudio.language.service;

import com.zerostudio.language.index.ProjectIndex;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class CrossLanguageResolverTest {

    private ProjectIndex idx;
    private CrossLanguageResolver res;

    private Reference mkRef(String name, Reference.ReferenceKind kind, String pkg, String path, LanguageId lang) {
        return new Reference(name,
                new SourceRange(new SourcePosition(path, 1, 1), new SourcePosition(path, 1, 1)),
                kind, pkg, path, lang);
    }

    @Before
    public void setUp() {
        idx = new ProjectIndex();
        // Java file
        ParsedFile javaFile = new ParsedFile("com/x/JavaClass.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        mkRef("JavaClass", Reference.ReferenceKind.CLASS, "com.x", "com/x/JavaClass.java", LanguageId.JAVA),
                        mkRef("javaMethod", Reference.ReferenceKind.METHOD, "com.x", "com/x/JavaClass.java", LanguageId.JAVA)
                ), "public class JavaClass { public void javaMethod() {} }");
        idx.index(javaFile);
        // Kotlin file
        ParsedFile ktFile = new ParsedFile("com/x/KotlinClass.kt", LanguageId.KOTLIN, "com.x",
                Arrays.asList(
                        mkRef("KotlinClass", Reference.ReferenceKind.CLASS, "com.x", "com/x/KotlinClass.kt", LanguageId.KOTLIN),
                        mkRef("kotlinMethod", Reference.ReferenceKind.METHOD, "com.x", "com/x/KotlinClass.kt", LanguageId.KOTLIN)
                ), "class KotlinClass { fun kotlinMethod() {} }");
        idx.index(ktFile);
        // Python file
        ParsedFile pyFile = new ParsedFile("module.py", LanguageId.PYTHON, "",
                Arrays.asList(
                        mkRef("PyClass", Reference.ReferenceKind.CLASS, "", "module.py", LanguageId.PYTHON),
                        mkRef("py_method", Reference.ReferenceKind.METHOD, "", "module.py", LanguageId.PYTHON)
                ), "class PyClass: def py_method(): pass");
        idx.index(pyFile);

        res = new CrossLanguageResolver(idx);
    }

    @Test
    public void resolvesDirectClass() {
        ParsedFile pf = new ParsedFile("Caller.java", LanguageId.JAVA, "com.x",
                Arrays.asList(mkRef("JavaClass", Reference.ReferenceKind.IMPORT, "com.x", "Caller.java", LanguageId.JAVA)),
                "");
        CrossLanguageResolver.Resolution r = res.resolveImport(pf, pf.references.get(0));
        assertNotNull(r);
        assertEquals("JavaClass", r.resolved.path.substring(r.resolved.path.lastIndexOf('/') + 1, r.resolved.path.lastIndexOf('.')));
    }

    @Test
    public void findsImplementationsAcrossLanguages() {
        List<ParsedFile> impls = res.findImplementations("javaMethod");
        assertTrue(impls.stream().anyMatch(p -> p.language == LanguageId.JAVA));
    }

    @Test
    public void findsClassesByName() {
        List<ParsedFile> classes = res.findClasses("KotlinClass");
        assertTrue(classes.size() >= 1);
        assertEquals(LanguageId.KOTLIN, classes.get(0).language);
    }

    @Test
    public void findClassesByFQN() {
        List<ParsedFile> classes = res.findClasses("com.x.KotlinClass");
        assertTrue(classes.size() >= 1);
    }

    @Test
    public void findClassesByShortNameCrossLang() {
        List<ParsedFile> classes = res.findClasses("PyClass");
        assertTrue(classes.stream().anyMatch(p -> p.language == LanguageId.PYTHON));
    }

    @Test
    public void adapterLookup() {
        res.addAdapter(name -> {
            if (name.contains("JavaClass")) return idx.fileFor("com.x.JavaClass");
            return null;
        });
        ParsedFile pf = new ParsedFile("caller.kt", LanguageId.KOTLIN, "com.x",
                Arrays.asList(mkRef("com.x.JavaClass", Reference.ReferenceKind.IMPORT, "com.x", "caller.kt", LanguageId.KOTLIN)),
                "");
        CrossLanguageResolver.Resolution r = res.resolveImport(pf, pf.references.get(0));
        assertNotNull(r);
    }

    @Test
    public void resolveMissingReturnsList() {
        ParsedFile pf = new ParsedFile("caller.kt", LanguageId.KOTLIN, "com.x",
                Arrays.asList(
                        mkRef("KotlinClass", Reference.ReferenceKind.IMPORT, "com.x", "caller.kt", LanguageId.KOTLIN),
                        mkRef("Missing", Reference.ReferenceKind.IMPORT, "com.x", "caller.kt", LanguageId.KOTLIN)
                ),
                "");
        List<CrossLanguageResolver.Resolution> rs = res.resolveMissing(pf);
        // KotlinClass 能解析，Missing 解析不到
        assertTrue(rs.size() >= 1);
        assertTrue(rs.stream().anyMatch(r -> r.resolved != null && r.resolved.path.contains("KotlinClass")));
    }

    @Test
    public void emptyIndex() {
        ProjectIndex empty = new ProjectIndex();
        CrossLanguageResolver r = new CrossLanguageResolver(empty);
        ParsedFile pf = new ParsedFile("a.java", LanguageId.JAVA, "x",
                Arrays.asList(mkRef("X", Reference.ReferenceKind.IMPORT, "x", "a.java", LanguageId.JAVA)), "");
        // 添加一个 adapter 应能解析
        r.addAdapter(name -> "X".equals(name) ? pf : null);
        assertNotNull(r.resolveImport(pf, pf.references.get(0)));
    }

    @Test
    public void nestedClassMatch() {
        ParsedFile nested = new ParsedFile("Outer.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        mkRef("Outer", Reference.ReferenceKind.CLASS, "com.x", "Outer.java", LanguageId.JAVA),
                        mkRef("Inner", Reference.ReferenceKind.CLASS, "com.x", "Outer.java", LanguageId.JAVA)
                ), "class Outer { class Inner {} }");
        idx.index(nested);
        List<ParsedFile> results = res.findClasses("com.x.Outer.Inner");
        assertTrue(results.stream().anyMatch(p -> p.path.equals("Outer.java")));
    }

    @Test
    public void nullFileReturnsEmpty() {
        assertTrue(res.resolveMissing(null).isEmpty());
    }
}
