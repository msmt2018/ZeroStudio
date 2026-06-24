package com.zerostudio.language.service;

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

public class DocumentSymbolsServiceTest {

    private ProjectIndex idx;
    private DocumentSymbolsService svc;

    @Before
    public void setUp() {
        idx = new ProjectIndex();
        ParsedFile a = new ParsedFile("A.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("Foo", new SourceRange(new SourcePosition("A.java", 1, 1), new SourcePosition("A.java", 1, 4)),
                                Reference.ReferenceKind.CLASS, "com.x", "A.java", LanguageId.JAVA),
                        new Reference("foo", new SourceRange(new SourcePosition("A.java", 2, 5), new SourcePosition("A.java", 2, 8)),
                                Reference.ReferenceKind.METHOD, "com.x", "A.java", LanguageId.JAVA),
                        new Reference("x", new SourceRange(new SourcePosition("A.java", 3, 5), new SourcePosition("A.java", 3, 6)),
                                Reference.ReferenceKind.FIELD, "com.x", "A.java", LanguageId.JAVA)
                ), "");
        idx.index(a);
        svc = new DocumentSymbolsService(idx);
    }

    @Test
    public void listsTopLevelSymbols() {
        List<DocumentSymbolsService.Symbol> syms = svc.listSymbols("A.java");
        assertEquals(1, syms.size());
        assertEquals(DocumentSymbolsService.Kind.FILE, syms.get(0).kind);
        assertTrue("expected 3 children, got " + syms.get(0).children.size(),
                syms.get(0).children.size() >= 3);
    }

    @Test
    public void classKindIsClass() {
        List<DocumentSymbolsService.Symbol> syms = svc.listSymbols("A.java");
        boolean hasClass = syms.get(0).children.stream()
                .anyMatch(s -> s.kind == DocumentSymbolsService.Kind.CLASS && s.name.equals("Foo"));
        assertTrue(hasClass);
    }

    @Test
    public void methodKindIsMethod() {
        List<DocumentSymbolsService.Symbol> syms = svc.listSymbols("A.java");
        boolean hasMethod = syms.get(0).children.stream()
                .anyMatch(s -> s.kind == DocumentSymbolsService.Kind.METHOD && s.name.equals("foo"));
        assertTrue(hasMethod);
    }

    @Test
    public void unknownFileReturnsEmpty() {
        assertTrue(svc.listSymbols("Unknown.java").isEmpty());
    }

    @Test
    public void nullIndexReturnsEmpty() {
        assertTrue(new DocumentSymbolsService(null).listSymbols("A.java").isEmpty());
    }
}
