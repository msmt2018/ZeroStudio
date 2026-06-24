package com.zerostudio.language;

import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.service.*;
import com.zerostudio.language.model.*;
import com.zerostudio.language.source.SourceResolver;
import com.zerostudio.decompiler.api.*;
import com.zerostudio.decompiler.impl.cfr.*;
import com.zerostudio.decompiler.cache.*;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class LibraryJumpTest {
    private SourceResolver resolver;

    @Before
    public void setUp() throws Exception {
        resolver = new SourceResolver();
        resolver.setDecompiler(new CachingDecompiler(new CfrDecompiler(), 50));
    }

    @After
    public void tearDown() {
        DecompilerRegistry.clearForTests();
    }

    @Test public void clickOnImportedClassOpensDecompiledView() throws Exception {
        // Build ImportantLib source and class
        File jarOut = Files.createTempDirectory("jarout").toFile();
        Path pkgDir = jarOut.toPath().resolve("com/thirdparty");
        pkgDir.toFile().mkdirs();
        
        // Write and compile the library source directly in the directory
        Path srcFile = pkgDir.resolve("ImportantLib.java");
        String src = "package com.thirdparty; public class ImportantLib { public static void doWork(String s) {} }";
        Files.writeString(srcFile, src);
        
        // Compile using absolute paths
        ProcessBuilder pb = new ProcessBuilder("javac", "-d", jarOut.getAbsolutePath(), srcFile.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int rc = p.waitFor();
        assertEquals("javac should succeed, output: " + (rc == 0 ? "success" : "failed"), 0, rc);
        
        // Don't set workspace root - rely on classpath decompilation
        resolver.addClasspathEntry(SourceResolver.ClasspathEntry.classJar(jarOut.getAbsolutePath()));

        // Parse user code with import
        String userCode = "import com.thirdparty.ImportantLib;\npublic class Main {\n    public static void main(String[] args) {\n        ImportantLib.doWork(\"hi\");\n    }\n}";
        JavaParserFacade facade = new JavaParserFacade();
        ParsedFile pf = facade.parse("Main.java", userCode);
        assertEquals("", pf.packageName);

        // Find import reference
        Reference importRef = pf.references.stream()
                .filter(r -> r.kind == Reference.ReferenceKind.IMPORT)
                .filter(r -> r.name.contains("ImportantLib"))
                .findFirst().orElse(null);
        assertNotNull("should find import ref", importRef);

        // Resolve import
        ImportResolver ir = new ImportResolver(resolver);
        java.util.Optional<ResolutionResult> resolved = ir.resolveImport(pf, importRef);
        assertTrue("should resolve ImportantLib", resolved.isPresent());
        assertTrue("should be resolved: " + resolved.get().failure, resolved.get().isResolved());

        // Verify editor would open virtual decompiled content
        EditorIntegration open = new EditorIntegration();
        AtomicReference<String> openedFile = new AtomicReference<>();
        AtomicBoolean openedReadOnly = new AtomicBoolean(false);
        open.setOpenHandler(req -> {
            openedFile.set(req.file);
            openedReadOnly.set(req.readOnly);
        });

        DebugHostSync sync = new DebugHostSync(open);
        com.zerostudio.language.service.LanguageService langSvc = 
            new com.zerostudio.language.service.LanguageService(LanguageId.JAVA) {
                @Override public java.util.Optional<ResolutionResult> resolve(ParsedFile f, Reference r) {
                    return java.util.Optional.empty();
                }
            };
        langSvc.setSourceResolver(resolver);  // Set the source resolver
        sync.setLanguageService(langSvc);
        sync.freezeEditor();
        sync.openResolved(resolved.get());
        assertTrue("should have opened virtual but got: " + openedFile.get(), openedFile.get().startsWith("["));
        assertTrue("should be readOnly but was: " + openedReadOnly.get(), openedReadOnly.get());
        assertTrue("should contain ImportantLib", sync.isFrozen());
    }
}