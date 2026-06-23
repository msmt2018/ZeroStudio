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

public class CallHierarchyServiceTest {

    private ProjectIndex idx;
    private CallHierarchyService svc;

    private Reference mkRef(String name, int line, int col, int endLine, int endCol, Reference.ReferenceKind kind, String pkg) {
        return new Reference(name,
                new SourceRange(new SourcePosition("?", line, col), new SourcePosition("?", endLine, endCol)),
                kind, pkg, "?", LanguageId.JAVA);
    }

    @Before
    public void setUp() {
        idx = new ProjectIndex();
        // A.java
        String aText = "package com.x;\n" +
                "public class A {\n" +
                "    public void alpha() {\n" +
                "        beta();\n" +
                "        gamma();\n" +
                "    }\n" +
                "    public void beta() {\n" +
                "        delta();\n" +
                "    }\n" +
                "    public void gamma() {\n" +
                "        delta();\n" +
                "    }\n" +
                "    public void delta() {}\n" +
                "}\n";
        ParsedFile a = new ParsedFile("A.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        mkRef("A", 2, 14, 2, 15, Reference.ReferenceKind.CLASS, "com.x"),
                        mkRef("alpha", 3, 17, 3, 22, Reference.ReferenceKind.METHOD, "com.x"),
                        mkRef("beta", 6, 17, 6, 21, Reference.ReferenceKind.METHOD, "com.x"),
                        mkRef("gamma", 9, 17, 9, 22, Reference.ReferenceKind.METHOD, "com.x"),
                        mkRef("delta", 12, 17, 12, 22, Reference.ReferenceKind.METHOD, "com.x")
                ), aText);
        idx.index(a);
        // B.java
        String bText = "package com.x;\n" +
                "public class B {\n" +
                "    public void run() {\n" +
                "        new A().alpha();\n" +
                "    }\n" +
                "}\n";
        ParsedFile b = new ParsedFile("B.java", LanguageId.JAVA, "com.x",
                Arrays.asList(
                        mkRef("B", 2, 14, 2, 15, Reference.ReferenceKind.CLASS, "com.x"),
                        mkRef("run", 3, 17, 3, 20, Reference.ReferenceKind.METHOD, "com.x")
                ), bText);
        idx.index(b);
        svc = new CallHierarchyService(idx);
    }

    @Test
    public void callersOfDelta() {
        List<CallHierarchyService.CallSite> callers = svc.callersOf("delta");
        // delta 被 beta 调用（A.java:7），被 gamma 调用（A.java:10）
        assertEquals(2, callers.size());
    }

    @Test
    public void callersOfBeta() {
        List<CallHierarchyService.CallSite> callers = svc.callersOf("beta");
        // beta 被 alpha 调用（A.java:4）
        assertEquals(1, callers.size());
        assertEquals("com.x", callers.get(0).containingClass);
    }

    @Test
    public void calleesOfAlpha() {
        List<CallHierarchyService.CallSite> callees = svc.calleesOf("com.x.A", "alpha");
        // alpha 调用 beta 和 gamma
        assertEquals(2, callees.size());
    }

    @Test
    public void calleesOfEmptyFile() {
        List<CallHierarchyService.CallSite> callees = svc.calleesOf("com.x.A", "delta");
        // delta 是空方法，无 callees
        assertEquals(0, callees.size());
    }

    @Test
    public void callersOfNonExistentReturnsEmpty() {
        assertEquals(0, svc.callersOf("nonexistent").size());
    }

    @Test
    public void emptyMethodNameReturnsEmpty() {
        assertEquals(0, svc.callersOf("").size());
        assertEquals(0, svc.callersOf(null).size());
    }

    @Test
    public void calleesNonExistentClassReturnsEmpty() {
        assertEquals(0, svc.calleesOf("com.x.NotExist", "foo").size());
    }

    @Test
    public void callSiteHasCorrectLocation() {
        List<CallHierarchyService.CallSite> callers = svc.callersOf("delta");
        for (CallHierarchyService.CallSite cs : callers) {
            assertNotNull(cs.file);
            assertTrue(cs.line > 0);
            assertTrue(cs.column > 0);
        }
    }
}
