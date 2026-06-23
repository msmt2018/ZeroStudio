package com.zerostudio.decompiler;

import com.zerostudio.decompiler.api.DecompileRequest;
import com.zerostudio.decompiler.api.DecompileResult;
import com.zerostudio.decompiler.api.Decompiler;
import com.zerostudio.decompiler.api.DecompilerRegistry;
import com.zerostudio.decompiler.impl.cfr.CfrDecompiler;
import org.junit.BeforeClass;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import static org.junit.Assert.*;

public class CfrDecompilerRealClassTest {

    private static final String JUNIT = "/root/.local/share/mise/installs/gradle/8.14.4/gradle-8.14.4/lib/junit-4.13.2.jar";

    @BeforeClass
    public static void setUp() {
        DecompilerRegistry.register(new CfrDecompiler());
    }

    private static byte[] readClassBytes(String jar, String internalName) throws Exception {
        try (JarFile jf = new JarFile(jar)) {
            JarEntry e = jf.getJarEntry(internalName);
            assertNotNull("class not found: " + internalName, e);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (java.io.InputStream is = jf.getInputStream(e)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) >= 0) out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    @Test
    public void decompileJUnitAssertClass() throws Exception {
        Decompiler d = DecompilerRegistry.get("cfr");
        assertNotNull(d);
        byte[] bytes = readClassBytes(JUNIT, "org/junit/Assert.class");
        DecompileRequest req = DecompileRequest.builder()
                .className("org.junit.Assert")
                .classBytes(bytes)
                .build();
        DecompileResult r = d.decompile(req);
        assertTrue("decompile should succeed, got: " + r.failure, r.isOk());
        assertNotNull(r.source);
        assertTrue("expected class declaration, got: " + r.source.substring(0, Math.min(200, r.source.length())),
                r.source.contains("class Assert"));
    }

    @Test
    public void decompileLegacyTestCase() throws Exception {
        Decompiler d = DecompilerRegistry.get("cfr");
        byte[] bytes = readClassBytes(JUNIT, "junit/framework/TestCase.class");
        DecompileRequest req = DecompileRequest.builder()
                .className("junit.framework.TestCase")
                .classBytes(bytes)
                .build();
        DecompileResult r = d.decompile(req);
        assertTrue("decompile should succeed, got: " + r.failure, r.isOk());
        assertTrue(r.source.contains("TestCase"));
    }

    @Test
    public void decompileTypeSafeMatcher() throws Exception {
        Decompiler d = DecompilerRegistry.get("cfr");
        byte[] bytes = readClassBytes(JUNIT, "org/junit/internal/matchers/TypeSafeMatcher.class");
        DecompileRequest req = DecompileRequest.builder()
                .className("org.junit.internal.matchers.TypeSafeMatcher")
                .classBytes(bytes)
                .build();
        DecompileResult r = d.decompile(req);
        assertTrue("decompile should succeed, got: " + r.failure, r.isOk());
        assertTrue(r.source.contains("TypeSafeMatcher"));
    }

    @Test
    public void decompileResultHasLineMapping() throws Exception {
        Decompiler d = DecompilerRegistry.get("cfr");
        byte[] bytes = readClassBytes(JUNIT, "org/junit/Assert.class");
        DecompileRequest req = DecompileRequest.builder()
                .className("org.junit.Assert")
                .classBytes(bytes)
                .build();
        DecompileResult r = d.decompile(req);
        assertNotNull("expected non-null lineMapping", r.lineMapping);
    }

    @Test
    public void decompileNonExistentClassFails() {
        Decompiler d = DecompilerRegistry.get("cfr");
        assertNotNull(d);
        DecompileRequest req = DecompileRequest.builder()
                .className("nonexistent.Foo")
                .classBytes(new byte[]{1, 2, 3})
                .build();
        DecompileResult r = d.decompile(req);
        assertFalse(r.isOk());
        assertNotNull(r.failure);
    }
}
