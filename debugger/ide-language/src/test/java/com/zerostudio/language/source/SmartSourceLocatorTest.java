package com.zerostudio.language.source;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;

public class SmartSourceLocatorTest {

    private SourceLocator.LocatedSource mkSource(String path, SourceLocator.Kind kind) {
        return SourceLocator.LocatedSource.of(kind, "com.x.A", path, "// source", path);
    }

    @Test
    public void returnsNullForEmpty() {
        SmartSourceLocator s = new SmartSourceLocator();
        assertNull(s.select(null));
        assertNull(s.select(new ArrayList<>()));
    }

    @Test
    public void returnsSingleAsIs() {
        SmartSourceLocator s = new SmartSourceLocator();
        SourceLocator.LocatedSource single = mkSource("a.jar", SourceLocator.Kind.SOURCE_JAR);
        assertSame(single, s.select(java.util.Collections.singletonList(single)));
    }

    @Test
    public void prefersShadedJar() {
        SmartSourceLocator s = new SmartSourceLocator();
        s.setStrategy(SmartSourceLocator.SelectionStrategy.PREFER_SHADED);
        SourceLocator.LocatedSource normal = mkSource("lib-1.0.jar", SourceLocator.Kind.SOURCE_JAR);
        SourceLocator.LocatedSource shaded = mkSource("lib-1.0-shaded.jar", SourceLocator.Kind.SOURCE_JAR);
        List<SourceLocator.LocatedSource> candidates = new ArrayList<>();
        candidates.add(normal);
        candidates.add(shaded);
        assertEquals("lib-1.0-shaded.jar", s.select(candidates).originPath);
    }

    @Test
    public void preferVersionTakesPriority() {
        SmartSourceLocator s = new SmartSourceLocator();
        s.setPreferVersion("2.0");
        s.setStrategy(SmartSourceLocator.SelectionStrategy.PREFER_SHADED);
        SourceLocator.LocatedSource v1 = mkSource("lib-1.0-shaded.jar", SourceLocator.Kind.SOURCE_JAR);
        SourceLocator.LocatedSource v2 = mkSource("lib-2.0.jar", SourceLocator.Kind.SOURCE_JAR);
        List<SourceLocator.LocatedSource> candidates = new ArrayList<>();
        candidates.add(v1);
        candidates.add(v2);
        assertEquals("lib-2.0.jar", s.select(candidates).originPath);
    }

    @Test
    public void scoreAllReturnsAll() {
        SmartSourceLocator s = new SmartSourceLocator();
        List<SourceLocator.LocatedSource> candidates = new ArrayList<>();
        candidates.add(mkSource("a.jar", SourceLocator.Kind.SOURCE_JAR));
        candidates.add(mkSource("b.jar", SourceLocator.Kind.DECOMPILED));
        assertEquals(2, s.scoreAll(candidates).size());
    }

    @Test
    public void decompiledGetsSizeScore() {
        SmartSourceLocator s = new SmartSourceLocator();
        s.setStrategy(SmartSourceLocator.SelectionStrategy.PREFER_LARGEST);
        // 大文件测试 - 但 originPath 指向不存在的文件，size=0
        List<SourceLocator.LocatedSource> c = new ArrayList<>();
        c.add(mkSource("/nonexistent/a.class", SourceLocator.Kind.DECOMPILED));
        // 不抛异常
        assertNotNull(s.select(c));
    }

    @Test
    public void handlesNullSourceInList() {
        SmartSourceLocator s = new SmartSourceLocator();
        List<SourceLocator.LocatedSource> c = new ArrayList<>();
        c.add(null);
        c.add(mkSource("a.jar", SourceLocator.Kind.SOURCE_JAR));
        assertNotNull(s.select(c));
    }

    @Test
    public void selectionStrategySetter() {
        SmartSourceLocator s = new SmartSourceLocator();
        s.setStrategy(SmartSourceLocator.SelectionStrategy.PREFER_LATEST);
        s.setPreferVersion("3.0");
        // 状态变更不会抛异常
        assertNotNull(s);
    }
}
