package com.zerostudio.language.index;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.ParsedFile;
import com.zerostudio.language.model.Reference;
import com.zerostudio.language.model.SourcePosition;
import com.zerostudio.language.model.SourceRange;
import org.junit.*;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ProjectIndexTest {
    @Test
    public void indexesImportsAndClasses() {
        ProjectIndex idx = new DefaultProjectIndex();
        ParsedFile f = new ParsedFile("F.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("com.x.Imported",
                                new SourceRange(new SourcePosition("F.java", 1, 1),
                                        new SourcePosition("F.java", 1, 5)),
                                Reference.ReferenceKind.IMPORT, "com.x", "F.java", LanguageId.JAVA),
                        new Reference("Foo",
                                new SourceRange(new SourcePosition("F.java", 2, 1),
                                        new SourcePosition("F.java", 2, 4)),
                                Reference.ReferenceKind.CLASS, "com.x", "F.java", LanguageId.JAVA)
                ), "package com.x; class Foo {}");
        idx.index(f);
        assertEquals(2, idx.totalClasses());
        assertNotNull(idx.fileFor("com.x.Imported"));
    }

    @Test
    public void fuzzySearch() {
        ProjectIndex idx = new DefaultProjectIndex();
        ParsedFile f = new ParsedFile("F.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        new Reference("com.x.MyHelper",
                                new SourceRange(new SourcePosition("F.java", 1, 1),
                                        new SourcePosition("F.java", 1, 10)),
                                Reference.ReferenceKind.IMPORT, "com.x", "F.java", LanguageId.JAVA)
                ), "");
        idx.index(f);
        assertFalse(idx.fuzzySearch("help", 10).isEmpty());
    }

    @Test
    public void wildcardMatch() {
        ProjectIndex idx = new DefaultProjectIndex();
        ParsedFile f = new ParsedFile("F.java", LanguageId.JAVA, "androidx.appcompat",
                Arrays.asList(
                        new Reference("androidx.appcompat.AppCompatActivity",
                                new SourceRange(new SourcePosition("F.java", 1, 1),
                                        new SourcePosition("F.java", 1, 1)),
                                Reference.ReferenceKind.IMPORT, "androidx.appcompat", "F.java", LanguageId.JAVA)
                ), "");
        idx.index(f);
        assertFalse(idx.matchWildcard("androidx.appcompat.*").isEmpty());
    }
}
