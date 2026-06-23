package com.zerostudio.language.source;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class TransitiveSymbolResolverTest {
    @Test
    public void expandCandidatesContainsAllPrefixes() {
        TransitiveSymbolResolver r = new TransitiveSymbolResolver();
        Set<String> c = r.expandCandidates("a.b.c.d");
        assertTrue(c.contains("a.b.c.d"));
        assertTrue(c.contains("a.b.c"));
        assertTrue(c.contains("a.b"));
        assertTrue(c.contains("a"));
    }

    @Test
    public void samePackageDetection() {
        TransitiveSymbolResolver r = new TransitiveSymbolResolver();
        assertTrue(r.isSamePackage("a.b.X", "a.b.Y"));
        assertFalse(r.isSamePackage("a.b.X", "a.c.Y"));
    }

    @Test
    public void followStarImportExpands() {
        TransitiveSymbolResolver r = new TransitiveSymbolResolver();
        Set<String> imps = new HashSet<>(Arrays.asList("com.foo.*"));
        Set<String> out = r.followImports("Bar", imps);
        assertTrue(out.contains("com.foo.Bar"));
    }

    @Test
    public void followSingleImportMatches() {
        TransitiveSymbolResolver r = new TransitiveSymbolResolver();
        Set<String> imps = new HashSet<>(Arrays.asList("com.foo.Bar"));
        Set<String> out = r.followImports("Bar", imps);
        assertTrue(out.contains("com.foo.Bar"));
    }

    @Test
    public void nestedClassExpansion() {
        java.util.Set<String> out = new TransitiveSymbolResolver().expandNestedClass("com.x.Outer", "Inner");
        assertTrue(out.contains("com.x.Outer.Inner"));
        assertTrue(out.contains("com.x.Outer$Inner"));
    }

    @Test
    public void outerOfDetectsDollarAndDot() {
        TransitiveSymbolResolver r = new TransitiveSymbolResolver();
        assertEquals("com.x.Outer", r.outerOf("com.x.Outer.Inner"));
        assertEquals("com.x.Outer", r.outerOf("com.x.Outer$Inner"));
        assertEquals("a.b.C", r.outerOf("a.b.C$D"));
        assertEquals("", r.outerOf("Simple"));
    }
}
