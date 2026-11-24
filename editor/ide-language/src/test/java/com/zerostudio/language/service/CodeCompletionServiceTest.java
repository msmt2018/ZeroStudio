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

public class CodeCompletionServiceTest {

    private ProjectIndex idx;
    private CodeCompletionService svc;

    @Before
    public void setUp() {
        idx = new DefaultProjectIndex();
        ParsedFile a = new ParsedFile("A.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("Foo", new SourceRange(new SourcePosition("A.java", 1, 1), new SourcePosition("A.java", 1, 4)),
                                Reference.ReferenceKind.CLASS, "com.x", "A.java", LanguageId.JAVA),
                        new Reference("foo", new SourceRange(new SourcePosition("A.java", 2, 1), new SourcePosition("A.java", 2, 4)),
                                Reference.ReferenceKind.METHOD, "com.x", "A.java", LanguageId.JAVA),
                        new Reference("bar", new SourceRange(new SourcePosition("A.java", 3, 1), new SourcePosition("A.java", 3, 4)),
                                Reference.ReferenceKind.METHOD, "com.x", "A.java", LanguageId.JAVA)
                ), "");
        idx.index(a);
        ParsedFile b = new ParsedFile("B.java", LanguageId.JAVA, "com.y",
                Arrays.asList(
                        new Reference("Baz", new SourceRange(new SourcePosition("B.java", 1, 1), new SourcePosition("B.java", 1, 4)),
                                Reference.ReferenceKind.CLASS, "com.y", "B.java", LanguageId.JAVA)
                ), "");
        idx.index(b);
        svc = new CodeCompletionService(idx);
    }

    @Test
    public void emptyPrefixReturnsAll() {
        List<CodeCompletionService.Item> items = svc.complete("A.java", 5, 1, "");
        assertTrue(items.size() >= 3);
    }

    @Test
    public void prefixFiltersResults() {
        List<CodeCompletionService.Item> items = svc.complete("A.java", 5, 1, "fo");
        assertTrue("expected at least foo, got: " + items.size(), items.size() >= 1);
        assertTrue(items.stream().anyMatch(i -> i.label.startsWith("fo")));
    }

    @Test
    public void samePackageClassPrioritized() {
        List<CodeCompletionService.Item> items = svc.complete("A.java", 5, 1, "");
        // Foo (com.x) should be ranked higher than Baz (com.y)
        int fooIdx = -1, bazIdx = -1;
        for (int i = 0; i < items.size(); i++) {
            if ("Foo".equals(items.get(i).label)) fooIdx = i;
            if ("Baz".equals(items.get(i).label)) bazIdx = i;
        }
        assertTrue("Foo should appear before Baz in same-file completion", fooIdx >= 0 && fooIdx < bazIdx);
    }

    @Test
    public void importContextSuggestsImportStatements() {
        List<CodeCompletionService.Item> items = svc.complete("A.java", 1, 1, "import com.");
        boolean hasImport = items.stream().anyMatch(i -> i.label.startsWith("import com."));
        assertTrue("expected import statements, got: " + items.size(), hasImport);
    }

    @Test
    public void keywordsAreIncluded() {
        List<CodeCompletionService.Item> items = svc.complete("A.java", 5, 1, "cl");
        assertTrue(items.stream().anyMatch(i -> "class".equals(i.label)));
    }

    @Test
    public void unknownFileUsesNoPriorBias() {
        List<CodeCompletionService.Item> items = svc.complete("Unknown.java", 1, 1, "fo");
        assertTrue(items.stream().anyMatch(i -> i.label.startsWith("fo")));
    }

    @Test
    public void noDuplicates() {
        List<CodeCompletionService.Item> items = svc.complete("A.java", 5, 1, "");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (CodeCompletionService.Item it : items) {
            assertTrue("duplicate: " + it.label + " (" + it.kind + ")", seen.add(it.label + "|" + it.kind));
        }
    }

    @Test
    public void resultIsSortedByPriority() {
        List<CodeCompletionService.Item> items = svc.complete("A.java", 5, 1, "fo");
        for (int i = 1; i < items.size(); i++) {
            assertTrue("not sorted at " + i, items.get(i - 1).priority >= items.get(i).priority);
        }
    }
}
