/*
 *  ZeroStudio IDE - ide-debugger
 *  Phase 20: MappedSourceLocation 单元测试
 */

package com.zerostudio.debugger.api;

import org.junit.Test;
import static org.junit.Assert.*;

public class MappedSourceLocationTest {

    @Test
    public void shortClassName_dropsPackage() {
        MappedSourceLocation l = new MappedSourceLocation(
                "a.b.C", null, null,
                "com.example.Foo", null, null,
                "Foo.java", 42, 0L, null,
                MappedSourceLocation.Kind.JAVA);
        assertEquals("Foo", l.shortClassName());
    }

    @Test
    public void remapped_whenClassDiffers() {
        MappedSourceLocation l = new MappedSourceLocation(
                "a.b.C", null, null,
                "com.example.Foo", null, null,
                null, 0, 0L, null,
                MappedSourceLocation.Kind.JAVA_OBFUSCATED);
        assertTrue(l.remapped);
    }

    @Test
    public void notRemapped_whenAllMatch() {
        MappedSourceLocation l = new MappedSourceLocation(
                "com.example.Foo", "m", null,
                "com.example.Foo", "m", null,
                "Foo.java", 1, 0L, null,
                MappedSourceLocation.Kind.JAVA);
        assertFalse(l.remapped);
    }

    @Test
    public void toString_includesFileLine() {
        MappedSourceLocation l = new MappedSourceLocation(
                "a", "m", null,
                "com.x.Foo", "m", null,
                "Foo.java", 99, 0L, null,
                MappedSourceLocation.Kind.JAVA_OBFUSCATED);
        String s = l.toString();
        assertTrue(s.contains("Foo.java:99"));
        assertTrue(s.contains("[remapped"));
    }

    @Test
    public void nativeC_includesHexAddress() {
        MappedSourceLocation l = new MappedSourceLocation(
                "?", "main", null,
                "?", "main", null,
                "main.c", 12, 0xDEAD_BEEFL, "libfoo.so",
                MappedSourceLocation.Kind.NATIVE_C);
        assertEquals("libfoo.so", l.nativeModule);
        assertEquals(0xDEAD_BEEFL, l.nativeAddress);
        assertTrue(l.toString().contains("libfoo.so"));
    }
}
