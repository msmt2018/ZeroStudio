package com.zerostudio.language;

import com.zerostudio.decompiler.api.Decompiler;
import com.zerostudio.decompiler.cache.CachingDecompiler;
import com.zerostudio.decompiler.impl.cfr.CfrDecompiler;
import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.ResolutionResult;
import com.zerostudio.language.model.SourceLocation;
import com.zerostudio.language.service.DebugHostSync;
import com.zerostudio.language.service.EditorIntegration;
import com.zerostudio.language.service.LanguageService;
import com.zerostudio.language.source.SourceResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end test for "step into library code" with CFR decompilation.
 *
 * <p>The scenario: the user has a project that imports a class
 * {@code com.thirdparty.ImportantLib} which is provided as a class
 * jar (no source). They pause at a breakpoint in their own code
 * and click on {@code ImportantLib.doWork(...)}. The editor must:
 * <ol>
 *   <li>Follow the import chain to the imported class.</li>
 *   <li>Fail to find the class in the workspace.</li>
 *   <li>Fail to find a source jar entry.</li>
 *   <li>Fall through to the class jar + CFR decompiler.</li>
 *   <li>Open the decompiled source as a virtual read-only buffer
 *       with a banner like {@code [decompiled from important.jar]}.</li>
 * </ol>
 */
public class LibraryJumpTest {

    private File workDir;
    private LanguageService language;
    private DebugHostSyncTest.FakeHost host;
    private DebugHostSyncTest.FakeOpenHandler open;
    private DebugHostSync sync;

    @Before
    public void setUp() throws Exception {
        workDir = new File("/tmp/library-jump-test");
        if (workDir.exists()) deleteRec(workDir);
        workDir.mkdirs();
        host = new DebugHostSyncTest.FakeHost();
        open = new DebugHostSyncTest.FakeOpenHandler();
        language = new LanguageService();
        Decompiler decompiler = new CachingDecompiler(new CfrDecompiler(), 32);
        // Build a SourceResolver with the test class jar only.
        File classJar = buildThirdPartyJar();
        SourceResolver resolver = new SourceResolver(
                Arrays.asList(
                        SourceResolver.ClasspathEntry.classJar(
                                classJar.getAbsolutePath())),
                path -> null,
                decompiler);
        language.setSourceResolver(resolver);
        sync = new DebugHostSync(language, host, open);
    }

    @After
    public void tearDown() {
        if (workDir != null && workDir.exists()) deleteRec(workDir);
    }

    @Test
    public void clickOnImportedClassOpensDecompiledView()
            throws Exception {
        String userSrc =
                "package com.example;\n" +
                "import com.thirdparty.ImportantLib;\n" +
                "public class Caller {\n" +
                "    void onClick() {\n" +
                "        ImportantLib.doWork(\"hi\");\n" +
                "    }\n" +
                "}\n";
        ParsedFile caller = language.parseText(
                "com/example/Caller.java", userSrc, LanguageId.JAVA);
        // Position the user on the `ImportantLib` identifier (line 4).
        SourceLocation where = new SourceLocation(
                caller.path,
                new com.zerostudio.language.model.SourcePosition(4, 9));
        // First, simulate the debugger hitting this exact line so the
        // host is frozen.
        sync.onBreakpointHit(caller.path, 4, 9).get(2, TimeUnit.SECONDS);
        assertTrue("host should be frozen on breakpoint hit", host.frozen.get());
        // Now invoke Go-to-Definition.
        ResolutionResult r = sync.goToDefinition(where)
                .get(2, TimeUnit.SECONDS);
        assertNotNull("resolution must not be null", r);
        assertTrue("expected resolved (imported class), got: " + r
                + " importResolver=" + language.importResolver(),
                r.isResolved());
        assertEquals("ImportantLib", r.targetSymbol.name);
        assertEquals("com.thirdparty.ImportantLib", r.targetSymbol.fqn);
        // The target file should be the decompiled display path.
        assertNotNull(r.targetFile);
        assertTrue("expected 'decompiled' in target path, got: "
                + r.targetFile, r.targetFile.contains("decompiled"));
        // The editor must have been told to open the decompiled buffer.
        EditorIntegration.OpenRequest req = open.last.get();
        assertNotNull("editor should have been told to open the decompiled buffer",
                req);
        assertTrue("expected decompiled marker in editor path, got: "
                + req.file, req.file.contains("decompiled"));
        assertNotNull("decompiled buffer should have pre-loaded content",
                req.bufferContent);
        assertTrue("decompiled content should mention the class, got: "
                + req.bufferContent,
                req.bufferContent.contains("ImportantLib"));
        assertTrue("decompiled content should mention doWork",
                req.bufferContent.contains("doWork"));
        assertTrue("buffer should be marked read-only", req.readOnly);
        // The host must STILL be frozen after navigation.
        assertTrue("host must remain frozen during library jump",
                host.frozen.get());
    }

    private File buildThirdPartyJar() throws Exception {
        // Compile a small third-party class.
        File srcDir = new File(workDir, "thirdparty-src");
        srcDir.mkdirs();
        File srcFile = new File(srcDir, "ImportantLib.java");
        try (java.io.FileWriter w = new java.io.FileWriter(srcFile)) {
            w.write("package com.thirdparty;\n" +
                    "public class ImportantLib {\n" +
                    "    public static String doWork(String in) { return in; }\n" +
                    "}\n");
        }
        File outDir = new File(workDir, "thirdparty-classes");
        outDir.mkdirs();
        Process javac = new ProcessBuilder("javac", "-d", outDir.getAbsolutePath(),
                srcFile.getAbsolutePath())
                .redirectErrorStream(true)
                .start();
        javac.getOutputStream().close();
        int rc = javac.waitFor();
        assertEquals("javac should succeed", 0, rc);

        File jar = new File(workDir, "important.jar");
        try (JarOutputStream jos = new JarOutputStream(
                new FileOutputStream(jar))) {
            File classFile = new File(outDir, "com/thirdparty/ImportantLib.class");
            jos.putNextEntry(new JarEntry("com/thirdparty/ImportantLib.class"));
            java.nio.file.Files.copy(classFile.toPath(), jos);
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
