package com.zerostudio.language;
import com.zerostudio.language.parser.JavaParserFacade;
import com.zerostudio.language.model.*;
import org.junit.Test;
import java.util.Optional;
import static org.junit.Assert.*;

public class JavaParserFacadeTest {
    @Test public void parsesPackageAndImports() {
        String code = "package com.example;\nimport java.util.List;\npublic class Foo {}\n";
        JavaParserFacade facade = new JavaParserFacade();
        ParsedFile pf = facade.parse("Foo.java", code);
        assertEquals(LanguageId.JAVA, pf.language);
        assertEquals("com.example", pf.packageName);
        assertTrue("should have import", pf.references.stream()
                .anyMatch(r -> r.kind == Reference.ReferenceKind.IMPORT && r.name.contains("java.util")));
    }
    @Test public void parsesClassAndMethod() {
        String code = "public class Bar { public void run() {} }";
        JavaParserFacade facade = new JavaParserFacade();
        ParsedFile pf = facade.parse("Bar.java", code);
        assertEquals(LanguageId.JAVA, pf.language);
        assertTrue("should have class ref", pf.references.stream()
                .anyMatch(r -> r.kind == Reference.ReferenceKind.CLASS));
    }
}