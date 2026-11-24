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
import static org.junit.Assert.*;

public class HoverServiceTest {

    private ProjectIndex idx;
    private HoverService svc;

    @Before
    public void setUp() {
        idx = new DefaultProjectIndex();
        ParsedFile a = new ParsedFile("A.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("Foo", new SourceRange(new SourcePosition("A.java", 1, 1), new SourcePosition("A.java", 1, 4)),
                                Reference.ReferenceKind.CLASS, "com.x", "A.java", LanguageId.JAVA),
                        new Reference("foo", new SourceRange(new SourcePosition("A.java", 2, 1), new SourcePosition("A.java", 2, 4)),
                                Reference.ReferenceKind.METHOD, "com.x", "A.java", LanguageId.JAVA),
                        new Reference("x", new SourceRange(new SourcePosition("A.java", 3, 1), new SourcePosition("A.java", 3, 2)),
                                Reference.ReferenceKind.FIELD, "com.x", "A.java", LanguageId.JAVA)
                ), "");
        idx.index(a);
        svc = new HoverService(idx);
    }

    @Test
    public void hoverClass() {
        HoverService.HoverInfo h = svc.hover("A.java", 1, 2);
        assertNotNull(h);
        assertEquals("Foo", h.title);
        assertTrue(h.subtitle.contains("class"));
    }

    @Test
    public void hoverMethod() {
        HoverService.HoverInfo h = svc.hover("A.java", 2, 2);
        assertNotNull(h);
        assertEquals("foo()", h.title);
    }

    @Test
    public void hoverField() {
        HoverService.HoverInfo h = svc.hover("A.java", 3, 1);
        assertNotNull(h);
        assertEquals("x", h.title);
        assertTrue(h.subtitle.contains("field"));
    }

    @Test
    public void hoverUnknownPositionReturnsNull() {
        assertNull(svc.hover("A.java", 99, 1));
        assertNull(svc.hover("Unknown.java", 1, 1));
    }

    @Test
    public void nullIndexReturnsNull() {
        assertNull(new HoverService(null).hover("A.java", 1, 1));
    }
}
