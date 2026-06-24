package com.zerostudio.language.source;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class SourceJarIndexTest {

    @Test
    public void indexesSourceContent() {
        SourceJarIndex idx = new SourceJarIndex();
        String content = "package com.x;\n" +
                "public class A {\n" +
                "    public void foo() {}\n" +
                "    public int bar(int n) { return n; }\n" +
                "    public static A create() { return new A(); }\n" +
                "}\n";
        idx.indexSourceFile("com.x.A", "com/x/A.java", content);
        List<SourceJarIndex.Entry> entries = idx.find("com.x.A", "foo");
        assertEquals(1, entries.size());
        assertEquals(3, entries.get(0).line);
    }

    @Test
    public void methodsOfReturnsAll() {
        SourceJarIndex idx = new SourceJarIndex();
        String content = "public class B {\n" +
                "    void m1() {}\n" +
                "    void m2() {}\n" +
                "    void m3() {}\n" +
                "}\n";
        idx.indexSourceFile("com.x.B", "com/x/B.java", content);
        List<SourceJarIndex.Entry> all = idx.methodsOf("com.x.B");
        assertEquals(3, all.size());
    }

    @Test
    public void findNonExistentReturnsEmpty() {
        SourceJarIndex idx = new SourceJarIndex();
        assertTrue(idx.find("com.x.Missing", "foo").isEmpty());
        assertTrue(idx.methodsOf("com.x.Missing").isEmpty());
        assertTrue(idx.findByMethodName("missing").isEmpty());
    }

    @Test
    public void findByMethodNameCrossClass() {
        SourceJarIndex idx = new SourceJarIndex();
        idx.indexSourceFile("com.x.A", "A.java", "class A { void render() {} }");
        idx.indexSourceFile("com.x.B", "B.java", "class B { void render() {} }");
        List<SourceJarIndex.Entry> r = idx.findByMethodName("render");
        assertEquals(2, r.size());
    }

    @Test
    public void entryKey() {
        SourceJarIndex.Entry e = new SourceJarIndex.Entry("com.x.A", "foo", "", "A.java", 1, "/x.jar");
        assertEquals("com.x.A#foo", e.key());
    }

    @Test
    public void entryFullKeyIncludesArgs() {
        SourceJarIndex.Entry e = new SourceJarIndex.Entry("com.x.A", "foo", "int,int", "A.java", 1, "/x.jar");
        assertTrue(e.fullKey().contains("foo"));
        assertTrue(e.fullKey().contains("int"));
    }

    @Test
    public void indexedArchivesRecorded() {
        SourceJarIndex idx = new SourceJarIndex();
        idx.indexArchive("/nonexistent/path.jar");
        assertTrue("nonexistent jar should not be recorded", idx.indexedArchives().isEmpty());
    }

    @Test
    public void entryCountAndClassCount() {
        SourceJarIndex idx = new SourceJarIndex();
        idx.indexSourceFile("com.x.A", "A.java", "class A { void m1() {} void m2() {} }");
        idx.indexSourceFile("com.x.B", "B.java", "class B { void m1() {} }");
        assertEquals(3, idx.entryCount());
        assertEquals(2, idx.classCount());
    }

    @Test
    public void clearResets() {
        SourceJarIndex idx = new SourceJarIndex();
        idx.indexSourceFile("com.x.A", "A.java", "class A { void m() {} }");
        idx.clear();
        assertEquals(0, idx.entryCount());
        assertEquals(0, idx.classCount());
    }

    @Test
    public void skipsCommentLines() {
        SourceJarIndex idx = new SourceJarIndex();
        String content = "// public void fake() {}\n" +
                "class A {\n" +
                "    public void real() {}\n" +
                "}\n";
        idx.indexSourceFile("com.x.A", "A.java", content);
        // 应该只有 real
        assertEquals(1, idx.methodsOf("com.x.A").size());
        assertEquals("real", idx.methodsOf("com.x.A").get(0).methodName);
    }
}
