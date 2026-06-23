package com.zerostudio.decompiler;

import com.zerostudio.decompiler.api.DecompileRequest;
import com.zerostudio.decompiler.api.DecompileResult;
import com.zerostudio.decompiler.api.DecompilerRegistry;
import com.zerostudio.decompiler.cache.CachingDecompiler;
import com.zerostudio.decompiler.impl.cfr.CfrDecompiler;

import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Verifies the CFR-backed decompiler produces a sensible
 * round-trip for a small Java class.
 */
public class CfrDecompilerTest {

    private static final String CLASS_FILE =
            "/tmp/decompile-test/classes/com/example/decompile/SimpleGreeter.class";
    private static final String CLASS_NAME =
            "com.example.decompile.SimpleGreeter";

    private CfrDecompiler cfr;

    @Before
    public void setUp() {
        cfr = new CfrDecompiler();
    }

    @Test
    public void decompilesSimpleClassFromBytes() throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(CLASS_FILE));
        DecompileResult r = cfr.decompile(
                DecompileRequest.builder(CLASS_NAME)
                        .classBytes(bytes)
                        .build());
        assertTrue("decompilation should succeed, got failure=" + r.failure,
                r.isOk());
        assertNotNull(r.source);
        assertTrue("expected 'class SimpleGreeter' in output, got: "
                + r.source, r.source.contains("SimpleGreeter"));
        assertTrue("expected 'greet' in output",
                r.source.contains("greet"));
        assertTrue("expected 'greeting' in output",
                r.source.contains("greeting"));
        assertEquals(CLASS_NAME, r.className);
    }

    @Test
    public void decompilesFromClasspathEntry() throws IOException {
        DecompileResult r = cfr.decompile(
                DecompileRequest.builder(CLASS_NAME)
                        .classpathEntry("/tmp/decompile-test/classes")
                        .build());
        assertTrue("decompilation should succeed via classpath, got failure="
                + r.failure, r.isOk());
        assertTrue(r.source.contains("SimpleGreeter"));
    }

    @Test
    public void failureWhenNoBytesOrClasspath() {
        DecompileResult r = cfr.decompile(
                DecompileRequest.builder(CLASS_NAME).build());
        assertFalse(r.isOk());
        assertNotNull(r.failure);
    }

    @Test
    public void cachingDecompilerCachesByClassName() throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(CLASS_FILE));
        DecompileRequest req = DecompileRequest.builder(CLASS_NAME)
                .classBytes(bytes).build();
        CachingDecompiler c = new CachingDecompiler(cfr, 8);
        DecompileResult first = c.decompile(req);
        assertTrue(first.isOk());
        // Calling again should hit the cache (we can't easily observe
        // a hit, but the size should not double).
        DecompileResult second = c.decompile(req);
        assertTrue(second.isOk());
        assertEquals(first.source, second.source);
        assertEquals(1, c.size());
    }

    @Test
    public void cachingDecompilerEvictsOldEntries() throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(CLASS_FILE));
        CachingDecompiler c = new CachingDecompiler(cfr, 2);
        c.decompile(DecompileRequest.builder("a.A").classBytes(bytes).build());
        c.decompile(DecompileRequest.builder("b.B").classBytes(bytes).build());
        c.decompile(DecompileRequest.builder("c.C").classBytes(bytes).build());
        // LRU should have evicted "a.A" but kept b and c.
        assertTrue("expected cache to evict, size=" + c.size(), c.size() <= 2);
    }

    @Test
    public void registryExposesCfr() {
        DecompilerRegistry.clearForTests();
        DecompilerRegistry.register(cfr);
        assertNotNull(DecompilerRegistry.get("cfr"));
        assertNotNull(DecompilerRegistry.firstOrNull());
    }
}
