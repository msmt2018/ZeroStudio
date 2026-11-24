package com.zerostudio.language.service;

import com.zerostudio.language.index.DefaultProjectIndex;
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

public class DiagnosticsServiceTest {

    private ProjectIndex idx;
    private DiagnosticsService svc;

    @Before
    public void setUp() {
        idx = new DefaultProjectIndex();
        svc = new DiagnosticsService(idx);
    }

    @Test
    public void detectsBraceMismatch() {
        ParsedFile a = new ParsedFile("A.java", LanguageId.JAVA, "com.x",
                java.util.Collections.emptyList(),
                "package com.x;\nclass A { void foo() { if (true) { }\n");
        idx.index(a);
        List<DiagnosticsService.Diagnostic> d = svc.check("A.java");
        assertTrue("expected brace mismatch, got: " + d, d.stream()
                .anyMatch(x -> "BRACE_MISMATCH".equals(x.code)));
    }

    @Test
    public void detectsParenMismatch() {
        ParsedFile a = new ParsedFile("B.java", LanguageId.JAVA, "com.x",
                java.util.Collections.emptyList(),
                "package com.x;\nclass B { void foo( { } }\n");
        idx.index(a);
        List<DiagnosticsService.Diagnostic> d = svc.check("B.java");
        assertTrue(d.stream().anyMatch(x -> "PAREN_MISMATCH".equals(x.code)));
    }

    @Test
    public void noErrorForValidFile() {
        ParsedFile a = new ParsedFile("C.java", LanguageId.JAVA, "com.x",
                java.util.Collections.emptyList(),
                "package com.x;\nclass C { void foo() { if (true) { return; } } }\n");
        idx.index(a);
        List<DiagnosticsService.Diagnostic> d = svc.check("C.java");
        // 应当没有 ERROR 级别
        for (DiagnosticsService.Diagnostic x : d) {
            assertNotEquals(DiagnosticsService.Diagnostic.Severity.ERROR, x.severity);
        }
    }

    @Test
    public void detectsDuplicateClass() {
        ParsedFile a = new ParsedFile("D.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("Dup", new SourceRange(new SourcePosition("D.java", 1, 1), new SourcePosition("D.java", 1, 4)),
                                Reference.ReferenceKind.CLASS, "com.x", "D.java", LanguageId.JAVA),
                        new Reference("Dup", new SourceRange(new SourcePosition("D.java", 2, 1), new SourcePosition("D.java", 2, 4)),
                                Reference.ReferenceKind.CLASS, "com.x", "D.java", LanguageId.JAVA)
                ), "class Dup {} class Dup {}");
        idx.index(a);
        List<DiagnosticsService.Diagnostic> d = svc.check("D.java");
        assertTrue("expected DUPLICATE_CLASS, got: " + d, d.stream()
                .anyMatch(x -> "DUPLICATE_CLASS".equals(x.code)));
    }

    @Test
    public void detectsLowercaseClassName() {
        ParsedFile a = new ParsedFile("E.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("lowerClass", new SourceRange(new SourcePosition("E.java", 1, 1), new SourcePosition("E.java", 1, 11)),
                                Reference.ReferenceKind.CLASS, "com.x", "E.java", LanguageId.JAVA)
                ), "class lowerClass {}");
        idx.index(a);
        List<DiagnosticsService.Diagnostic> d = svc.check("E.java");
        assertTrue(d.stream().anyMatch(x -> "NAMING_CLASS".equals(x.code)));
    }

    @Test
    public void detectsUnresolvedImport() {
        ParsedFile a = new ParsedFile("F.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("com.nonexistent.Missing",
                                new SourceRange(new SourcePosition("F.java", 1, 1), new SourcePosition("F.java", 1, 30)),
                                Reference.ReferenceKind.IMPORT, "com.x", "F.java", LanguageId.JAVA)
                ), "import com.nonexistent.Missing;");
        idx.index(a);
        List<DiagnosticsService.Diagnostic> d = svc.check("F.java");
        assertTrue("expected UNRESOLVED_IMPORT, got: " + d, d.stream()
                .anyMatch(x -> "UNRESOLVED_IMPORT".equals(x.code)));
    }

    @Test
    public void checkAllReturnsAcrossFiles() {
        ParsedFile a = new ParsedFile("A.java", LanguageId.JAVA, "com.x",
                java.util.Collections.emptyList(), "package com.x; class A { void foo( }");
        ParsedFile b = new ParsedFile("B.java", LanguageId.JAVA, "com.y",
                java.util.Collections.emptyList(), "package com.y; class B {");
        idx.index(a);
        idx.index(b);
        List<DiagnosticsService.Diagnostic> d = svc.checkAll();
        assertTrue("expected at least 2 diagnostics, got: " + d.size(), d.size() >= 2);
    }

    @Test
    public void unknownFileReturnsEmpty() {
        assertTrue(svc.check("Unknown.java").isEmpty());
    }
}
