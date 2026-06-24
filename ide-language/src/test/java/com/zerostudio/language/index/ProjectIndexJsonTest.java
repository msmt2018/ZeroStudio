package com.zerostudio.language.index;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ProjectIndexJsonTest {

    @Test
    public void roundTrip() {
        ProjectIndex idx = new ProjectIndex();
        ParsedFile f = new ParsedFile("F.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("com.x.Foo",
                                new SourceRange(new SourcePosition("F.java", 1, 1),
                                        new SourcePosition("F.java", 1, 5)),
                                Reference.ReferenceKind.IMPORT, "com.x", "F.java", LanguageId.JAVA)
                ), "package com.x;");
        idx.index(f);
        String json = ProjectIndexJson.serialize(idx);
        assertTrue(json.contains("com.x.Foo"));
        assertTrue(json.contains("F.java"));

        ProjectIndex restored = new ProjectIndex();
        ProjectIndexJson.deserialize(json, restored);
        // file path should be restored
        assertNotNull(restored.fileForPath("F.java"));
        assertEquals(1, restored.totalFiles());
    }

    @Test
    public void emptyIndexSerializes() {
        String json = ProjectIndexJson.serialize(new ProjectIndex());
        assertTrue(json.contains("\"classes\":"));
        assertTrue(json.contains("\"files\":"));
    }

    @Test
    public void nullIndexHandled() {
        assertEquals("{}", ProjectIndexJson.serialize(null));
        ProjectIndexJson.deserialize(null, new ProjectIndex()); // no throw
        ProjectIndexJson.deserialize("{}", null); // no throw
    }

    @Test
    public void multipleFilesPersisted() {
        ProjectIndex idx = new ProjectIndex();
        idx.index(new ParsedFile("A.java", LanguageId.JAVA, "p1",
                java.util.Collections.emptyList(), ""));
        idx.index(new ParsedFile("B.kt", LanguageId.KOTLIN, "p2",
                java.util.Collections.emptyList(), ""));
        String json = ProjectIndexJson.serialize(idx);
        assertTrue(json.contains("A.java"));
        assertTrue(json.contains("B.kt"));
    }
}
