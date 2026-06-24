package com.zerostudio.language.source;

import com.zerostudio.decompiler.api.*;
import com.zerostudio.decompiler.cache.CachingDecompiler;
import com.zerostudio.decompiler.impl.cfr.CfrDecompiler;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;

public class SourceLocatorTest {
    private SourceLocator locator;
    private File workspace;

    @Before
    public void setUp() throws Exception {
        DecompilerRegistry.clearForTests();
        DecompilerRegistry.register(new CfrDecompiler());
        locator = new SourceLocator();
        workspace = Files.createTempDirectory("ws-loc").toFile();
        locator.addWorkspaceRoot(workspace);
    }

    @After
    public void tearDown() { DecompilerRegistry.clearForTests(); }

    @Test
    public void locateFromWorkspace() throws Exception {
        Path f = workspace.toPath().resolve("com/x/Y.java");
        f.getParent().toFile().mkdirs();
        Files.writeString(f, "package com.x; public class Y { }");
        SourceLocator.LocatedSource r = locator.locate("com.x.Y");
        assertTrue("should resolve", r.isResolved());
        assertEquals(SourceLocator.Kind.WORKSPACE_SOURCE, r.kind);
    }

    @Test
    public void locateBuiltinForJavaLang() {
        SourceLocator.LocatedSource r = locator.locate("java.lang.String");
        assertEquals(SourceLocator.Kind.BUILTIN, r.kind);
        assertNotNull(r.sourceText);
    }

    @Test
    public void missingForUnknown() {
        SourceLocator.LocatedSource r = locator.locate("does.not.Exist");
        assertEquals(SourceLocator.Kind.MISSING, r.kind);
    }

    @Test
    public void cacheReturnsSameResult() throws Exception {
        Path f = workspace.toPath().resolve("com/x/Z.java");
        f.getParent().toFile().mkdirs();
        Files.writeString(f, "package com.x; public class Z { }");
        SourceLocator.LocatedSource r1 = locator.locate("com.x.Z");
        SourceLocator.LocatedSource r2 = locator.locate("com.x.Z");
        assertSame("cached", r1, r2);
    }

    @Test
    public void decompileFromClassArchive() throws Exception {
        File dir = Files.createTempDirectory("cfr-loc").toFile();
        File src = new File(dir, "Foo.java");
        Files.writeString(src.toPath(), "public class Foo { public int bar() { return 42; } }");
        Process p = Runtime.getRuntime().exec("javac -d " + dir + " " + src);
        p.waitFor();
        locator.addClasspathEntry(SourceLocator.ClasspathEntry.classJar(dir.getAbsolutePath()));
        SourceLocator.LocatedSource r = locator.locate("Foo");
        assertTrue("should resolve: " + r.failure, r.isResolved());
    }
}
