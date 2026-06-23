package com.zerostudio.language.source;

import com.zerostudio.decompiler.api.Decompiler;
import com.zerostudio.decompiler.cache.CachingDecompiler;
import com.zerostudio.decompiler.impl.cfr.CfrDecompiler;
import com.zerostudio.language.model.ParsedFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the three-tier source resolver:
 * workspace -> source-jar -> class-jar + CFR decompile.
 */
public class SourceResolverTest {

    private File workDir;

    @Before
    public void setUp() throws Exception {
        workDir = new File("/tmp/sourceresolver-test");
        if (workDir.exists()) deleteRec(workDir);
        workDir.mkdirs();
    }

    @After
    public void tearDown() {
        if (workDir != null && workDir.exists()) deleteRec(workDir);
    }

    @Test
    public void resolvesWorkspaceFirst() {
        SourceResolver r = new SourceResolver(
                Collections.emptyList(),
                path -> "class Foo {}" // fake workspace source
        );
        SourceResolver.ResolvedSource res = r.resolve("com.example.Foo");
        assertEquals(SourceResolver.Kind.WORKSPACE_SOURCE, res.kind);
        assertEquals("com/example/Foo.java", res.displayPath);
        assertTrue(res.sourceText.contains("class Foo"));
    }

    @Test
    public void resolvesFromSourceJar() throws Exception {
        File srcJar = buildSourceJarWith(
                "com/widget/Toast.java",
                "package com.widget;\n" +
                "public class Toast {\n" +
                "    public static Toast makeText() { return new Toast(); }\n" +
                "}\n");
        SourceResolver r = new SourceResolver(
                java.util.Arrays.asList(
                        SourceResolver.ClasspathEntry.sourceJar(
                                srcJar.getAbsolutePath())),
                path -> null);
        SourceResolver.ResolvedSource res = r.resolve("com.widget.Toast");
        assertEquals(SourceResolver.Kind.SOURCE_JAR, res.kind);
        assertTrue(res.sourceText.contains("makeText"));
        assertTrue(res.originPath.contains("src.jar"));
    }

    @Test
    public void resolvesFromClassJarViaCfr() throws Exception {
        // Build a small class file via a temp dir (CFR needs a file
        // path) and wrap it in a class jar.
        File classDir = new File(workDir, "classes");
        classDir.mkdirs();
        String src =
                "package com.thirdparty;\n" +
                "public class SecretSauce {\n" +
                "    public String flavor() { return \"umami\"; }\n" +
                "}\n";
        File srcFile = new File(classDir, "SecretSauce.java");
        try (java.io.FileWriter w = new java.io.FileWriter(srcFile)) {
            w.write(src);
        }
        // Compile
        Process javac = new ProcessBuilder("javac", "-d", classDir.getAbsolutePath(),
                srcFile.getAbsolutePath())
                .redirectErrorStream(true)
                .start();
        javac.getOutputStream().close();
        int rc = javac.waitFor();
        assertEquals("javac should succeed", 0, rc);

        File classJar = new File(workDir, "thirdparty.jar");
        try (java.util.jar.JarOutputStream jos = new JarOutputStream(
                new java.io.FileOutputStream(classJar))) {
            File classFile = new File(classDir, "com/thirdparty/SecretSauce.class");
            jos.putNextEntry(new JarEntry("com/thirdparty/SecretSauce.class"));
            java.nio.file.Files.copy(classFile.toPath(), jos);
            jos.closeEntry();
        }
        Decompiler decompiler = new CachingDecompiler(new CfrDecompiler(), 32);
        SourceResolver r = new SourceResolver(
                java.util.Arrays.asList(
                        SourceResolver.ClasspathEntry.classJar(
                                classJar.getAbsolutePath())),
                path -> null,
                decompiler);
        SourceResolver.ResolvedSource res = r.resolve("com.thirdparty.SecretSauce");
        assertEquals("expected decompiled, got " + res.kind + " failure=" + res.failure,
                SourceResolver.Kind.DECOMPILED, res.kind);
        assertTrue("expected 'flavor' in decompiled source, got: "
                + res.sourceText, res.sourceText.contains("flavor"));
        assertTrue(res.displayPath.contains("decompiled"));
    }

    @Test
    public void missingWhenNothingFound() {
        SourceResolver r = new SourceResolver(
                Collections.emptyList(), path -> null);
        SourceResolver.ResolvedSource res = r.resolve("com.nope.Missing");
        assertEquals(SourceResolver.Kind.MISSING, res.kind);
        assertFalse(res.isResolved());
    }

    @Test
    public void workspaceTakesPrecedenceOverSourceJar() throws Exception {
        File srcJar = buildSourceJarWith(
                "com/example/A.java",
                "package com.example;\n" +
                "public class A { // from sources jar\n" +
                "}\n");
        SourceResolver r = new SourceResolver(
                java.util.Arrays.asList(
                        SourceResolver.ClasspathEntry.sourceJar(
                                srcJar.getAbsolutePath())),
                path -> "package com.example;\n" +
                        "public class A { // from workspace\n" +
                        "}\n");
        SourceResolver.ResolvedSource res = r.resolve("com.example.A");
        assertEquals(SourceResolver.Kind.WORKSPACE_SOURCE, res.kind);
        assertTrue(res.sourceText.contains("from workspace"));
    }

    private File buildSourceJarWith(String entry, String content) throws Exception {
        File jar = new File(workDir, "src.jar");
        try (JarOutputStream jos = new JarOutputStream(
                new FileOutputStream(jar))) {
            jos.putNextEntry(new JarEntry(entry));
            jos.write(content.getBytes());
            jos.closeEntry();
        }
        return jar;
    }

    private static void deleteRec(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }
}
