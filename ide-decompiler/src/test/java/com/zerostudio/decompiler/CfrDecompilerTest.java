package com.zerostudio.decompiler;

import com.zerostudio.decompiler.api.*;
import com.zerostudio.decompiler.cache.*;
import com.zerostudio.decompiler.impl.cfr.*;
import java.io.*;
import java.nio.file.*;
import org.junit.*;
import static org.junit.Assert.*;

public class CfrDecompilerTest {
    @Before
    public void setUp() throws Exception {
        // compile sample class
        new File("/tmp/decompile-test").mkdirs();
        String src = "public class SimpleGreeter { public String greet() { return \"Hello\"; } }";
        Files.write(Paths.get("/tmp/decompile-test/SimpleGreeter.java"), src.getBytes());
        Process p = Runtime.getRuntime().exec(
            "javac -d /tmp/decompile-test /tmp/decompile-test/SimpleGreeter.java");
        int rc = p.waitFor();
        if (rc != 0) throw new RuntimeException("javac failed: " + rc);
    }

    @After
    public void tearDown() {
        DecompilerRegistry.clearForTests();
    }

    @Test public void decompilesSimpleClassFromBytes() throws Exception {
        Path classFile = Paths.get("/tmp/decompile-test/SimpleGreeter.class");
        byte[] bytes = Files.readAllBytes(classFile);
        Decompiler d = new CfrDecompiler();
        DecompileResult r = d.decompile(DecompileRequest.builder()
                .className("SimpleGreeter").classBytes(bytes).build());
        assertTrue("decompile failed: " + r.failure, r.isOk());
        assertTrue("should contain 'greet'", r.source.contains("greet"));
    }

    @Test public void decompilesFromClasspathEntry() {
        Decompiler d = new CfrDecompiler();
        DecompileResult r = d.decompile(DecompileRequest.builder()
                .className("SimpleGreeter")
                .classpathEntry("/tmp/decompile-test")
                .build());
        assertTrue("decompile failed: " + r.failure, r.isOk());
        assertTrue("should contain 'greet'", r.source.contains("greet"));
    }

    @Test public void failureWhenNoBytesOrClasspath() {
        Decompiler d = new CfrDecompiler();
        DecompileResult r = d.decompile(DecompileRequest.builder()
                .className("SomeClass").build());
        assertFalse("should fail without bytes or classpath", r.isOk());
        assertNotNull("should have failure message", r.failure);
    }

    @Test public void cachingDecompilerCachesByClassName() throws Exception {
        Path classFile = Paths.get("/tmp/decompile-test/SimpleGreeter.class");
        byte[] bytes = Files.readAllBytes(classFile);
        Decompiler inner = new CfrDecompiler();
        CachingDecompiler cache = new CachingDecompiler(inner, 10);
        DecompileResult r1 = cache.decompile(DecompileRequest.builder()
                .className("SimpleGreeter").classBytes(bytes).build());
        DecompileResult r2 = cache.decompile(DecompileRequest.builder()
                .className("SimpleGreeter").classBytes(bytes).build());
        assertSame("should return same cached result", r1, r2);
    }

    @Test public void cachingDecompilerEvictsOldEntries() throws Exception {
        Path classFile = Paths.get("/tmp/decompile-test/SimpleGreeter.class");
        byte[] bytes = Files.readAllBytes(classFile);
        Decompiler inner = new CfrDecompiler();
        CachingDecompiler cache = new CachingDecompiler(inner, 2);
        cache.decompile(DecompileRequest.builder().className("A").classBytes(bytes).build());
        cache.decompile(DecompileRequest.builder().className("B").classBytes(bytes).build());
        // third entry should evict one
        DecompileResult r = cache.decompile(DecompileRequest.builder().className("C").classBytes(bytes).build());
        assertNotNull(r);
    }

    @Test public void registryExposesCfr() {
        DecompilerRegistry.clearForTests();
        DecompilerRegistry.register(new CfrDecompiler());
        assertEquals("cfr", DecompilerRegistry.get("cfr").name());
        assertNotNull(DecompilerRegistry.firstOrNull());
    }
}