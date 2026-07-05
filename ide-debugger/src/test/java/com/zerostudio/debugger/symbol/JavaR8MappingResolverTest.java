/*
 *  ZeroStudio IDE - ide-debugger
 *  Phase 20: JavaR8MappingResolver 单元测试
 *
 *  覆盖:
 *    - 4 列 mapping.txt 解析 (类 + 字段 + 方法)
 *    - 类名 JNI 格式 ("Lcom/example/A;") 归一化
 *    - 字段 / 方法反混淆查询
 *    - 未命中 / 空流 / 损坏流的回退
 */

package com.zerostudio.debugger.symbol;

import com.zerostudio.debugger.api.MappedSourceLocation;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class JavaR8MappingResolverTest {

    @Test
    public void parse_classMapping_basic() {
        String mapping = ""
                + "com.example.A -> com.example.OriginalA:\n"
                + "    java.lang.String name -> name\n"
                + "    void doIt() -> doSomething\n"
                + "com.example.B -> com.example.B:\n";
        JavaR8MappingResolver r = new JavaR8MappingResolver();
        assertTrue(r.load(toStream(mapping)));
        assertTrue(r.isLoaded());
        assertEquals(2, r.classCount());

        MappedSourceLocation loc = r.mapJava(
                "com.example.A", "doIt", null, null);
        assertNotNull(loc);
        assertEquals("com.example.OriginalA", loc.originalClassName);
        assertEquals("doSomething", loc.originalMethodName);
        assertTrue(loc.remapped);
    }

    @Test
    public void parse_jniClassName_normalized() {
        String mapping = "com.example.A -> com.example.OriginalA:\n";
        JavaR8MappingResolver r = new JavaR8MappingResolver();
        assertTrue(r.load(toStream(mapping)));
        MappedSourceLocation loc = r.mapJava(
                "Lcom/example/A;", null, "name", null);
        assertNotNull(loc);
        assertEquals("com.example.OriginalA", loc.originalClassName);
        assertEquals("name", loc.originalFieldName);
    }

    @Test
    public void unmappedClass_returnsNull_thenSourceNameMapperRawFallback() {
        JavaR8MappingResolver r = new JavaR8MappingResolver();
        r.load(toStream("com.a.A -> com.a.A:\n"));
        // 不在 mapping 里的类
        MappedSourceLocation loc = r.mapJava("unknown.X", null, null, null);
        // 单独 r 返回 null(没认领);SourceNameMapper 会回 RAW
        assertNull(loc);
    }

    @Test
    public void emptyStream_returnsFalse() {
        JavaR8MappingResolver r = new JavaR8MappingResolver();
        assertFalse(r.load(toStream("")));
    }

    @Test
    public void malformedArrowLine_skipped() {
        String mapping = "no-arrow-here\ncom.a.A -> com.a.B:\n";
        JavaR8MappingResolver r = new JavaR8MappingResolver();
        assertTrue(r.load(toStream(mapping)));
        assertEquals(1, r.classCount());
    }

    @Test
    public void methodSignature_withDescriptor() {
        String mapping = ""
                + "com.x.Foo -> com.x.RealFoo:\n"
                + "    int add(int, int) -> sum\n";
        JavaR8MappingResolver r = new JavaR8MappingResolver();
        assertTrue(r.load(toStream(mapping)));
        assertEquals(1, r.methodCount());
    }

    @Test
    public void clear_resetsState() {
        JavaR8MappingResolver r = new JavaR8MappingResolver();
        r.load(toStream("a.b.C -> a.b.Real:\n"));
        assertTrue(r.isLoaded());
        r.clear();
        assertFalse(r.isLoaded());
        assertEquals(0, r.classCount());
    }

    @Test
    public void sourceNameMapper_fallsBackToRaw() {
        SourceNameMapper m = SourceNameMapper.getInstance();
        m.clearAll();
        MappedSourceLocation loc = m.mapJava("com.foo.Bar", "baz", null, null);
        assertNotNull(loc);
        assertEquals("com.foo.Bar", loc.originalClassName);
        assertEquals("baz", loc.originalMethodName);
        assertFalse(loc.remapped);
    }

    @Test
    public void sourceNameMapper_r8Hit_returnsRemapped() {
        SourceNameMapper m = SourceNameMapper.getInstance();
        m.clearAll();
        m.loadR8Mapping(toStream("com.foo.A -> com.foo.RealA:\n    void x() -> y\n"));
        MappedSourceLocation loc = m.mapJava("com.foo.A", "x", null, null);
        assertNotNull(loc);
        assertEquals("com.foo.RealA", loc.originalClassName);
        assertEquals("y", loc.originalMethodName);
        assertTrue(loc.remapped);
    }

    private static InputStream toStream(@org.jetbrains.annotations.NotNull String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
