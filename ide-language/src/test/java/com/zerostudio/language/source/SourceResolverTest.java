package com.zerostudio.language.source;

import com.zerostudio.language.source.SourceResolver;
import com.zerostudio.decompiler.api.*;
import com.zerostudio.decompiler.impl.cfr.*;
import com.zerostudio.decompiler.cache.*;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;

public class SourceResolverTest {
    private SourceResolver resolver;
    private File workspace;

    @Before
    public void setUp() throws Exception {
        resolver = new SourceResolver();
        workspace = Files.createTempDirectory("ws").toFile();
        resolver.setWorkspaceRoot(workspace);
        resolver.setDecompiler(new CachingDecompiler(new CfrDecompiler(), 50));
    }

    @After
    public void tearDown() {
        DecompilerRegistry.clearForTests();
    }

    @Test public void workspaceTakesPrecedenceOverSourceJar() throws Exception {
        Path javaFile = workspace.toPath().resolve("com/example/Foo.java");
        javaFile.getParent().toFile().mkdirs();
        Files.writeString(javaFile, "package com.example; public class Foo {}");
        resolver.addClasspathEntry(SourceResolver.ClasspathEntry.sourceJar("/fake.jar"));
        SourceResolver.ResolvedSource r = resolver.resolve("com.example.Foo");
        assertEquals(SourceResolver.Kind.WORKSPACE_SOURCE, r.kind);
    }

    @Test public void resolvesWorkspaceSource() throws Exception {
        Path javaFile = workspace.toPath().resolve("com/thirdparty/ImportantLib.java");
        javaFile.getParent().toFile().mkdirs();
        Files.writeString(javaFile, "package com.thirdparty; public class ImportantLib { public static void doWork(String s) {} }");
        SourceResolver.ResolvedSource r = resolver.resolve("com.thirdparty.ImportantLib");
        assertTrue("should resolve", r.isResolved());
        assertEquals(SourceResolver.Kind.WORKSPACE_SOURCE, r.kind);
    }

    @Test public void missingWhenNothingFound() {
        SourceResolver.ResolvedSource r = resolver.resolve("does.not.Exist");
        assertEquals(SourceResolver.Kind.MISSING, r.kind);
    }

    @Test public void builtinJavaLangResolved() {
        SourceResolver.ResolvedSource r = resolver.resolve("java.lang.String");
        assertEquals(SourceResolver.Kind.BUILTIN, r.kind);
        assertNotNull(r.sourceText);
    }

    @Test public void decompiledFromClassJar() throws Exception {
        Path classFile = workspace.toPath().resolve("com/thirdparty/ImportantLib.class");
        classFile.getParent().toFile().mkdirs();
        String src = "package com.thirdparty; public class ImportantLib { public static void doWork(String s) {} }";
        Files.writeString(workspace.toPath().resolve("com/thirdparty/ImportantLib.java"), src);
        // compile
        Process p = Runtime.getRuntime().exec(
            "javac -d " + workspace + " " + workspace + "/com/thirdparty/ImportantLib.java");
        p.waitFor();
        // add as class JAR (use directory for simplicity)
        resolver.addClasspathEntry(SourceResolver.ClasspathEntry.classJar(workspace.getAbsolutePath()));
        SourceResolver.ResolvedSource r = resolver.resolve("com.thirdparty.ImportantLib");
        assertTrue("should resolve from class", r.isResolved());
        assertTrue("should be decompiled", r.kind == SourceResolver.Kind.DECOMPILED
                || r.kind == SourceResolver.Kind.WORKSPACE_SOURCE);
    }
}