/*
 *  ZeroStudio IDE - ide-debugger
 *  Phase 20: SourceNameMapper 综合测试
 *
 *  测试:
 *    - 默认注册了 3 个解析器
 *    - 注册 / 卸载流程
 *    - Java 透传 + 反混淆 + 多解析器优先级
 *    - Native 无 DWARF 时回退到 NATIVE_UNKNOWN
 */

package com.zerostudio.debugger.symbol;

import com.zerostudio.debugger.api.MappedSourceLocation;
import com.zerostudio.debugger.api.NativeAddress;
import com.zerostudio.debugger.api.SourceLocation;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceNameMapperTest {

    @Test
    public void defaultResolversRegistered() {
        SourceNameMapper m = SourceNameMapper.getInstance();
        m.clearAll(); // 重置但不卸载
        // 重新初始化
        m.register(new JavaR8MappingResolver());
        m.register(new DwarfSymbolResolver());
        assertTrue(m.resolverCount() >= 2);
    }

    @Test
    public void mapJava_remappedFromR8() {
        SourceNameMapper m = SourceNameMapper.getInstance();
        m.clearAll();
        m.register(new JavaR8MappingResolver());
        m.loadR8Mapping(new java.io.ByteArrayInputStream(
                "com.x.A -> com.x.OriginalA:\n    void m() -> renamed\n"
                        .getBytes()));
        MappedSourceLocation loc = m.mapJava("com.x.A", "m", null, null);
        assertEquals("com.x.OriginalA", loc.originalClassName);
        assertEquals("renamed", loc.originalMethodName);
    }

    @Test
    public void mapJava_rawFallback() {
        SourceNameMapper m = SourceNameMapper.getInstance();
        m.clearAll();
        m.register(new JavaR8MappingResolver());
        MappedSourceLocation loc = m.mapJava("com.unknown.X", "foo", null,
                new SourceLocation(0, 0, 0, 12, "X.java"));
        assertEquals("com.unknown.X", loc.originalClassName);
        assertEquals("X.java", loc.sourceFile);
        assertEquals(12, loc.sourceLine);
    }

    @Test
    public void mapNative_unknownWhenNoDwarf() {
        SourceNameMapper m = SourceNameMapper.getInstance();
        m.clearAll();
        m.register(new DwarfSymbolResolver());
        MappedSourceLocation loc = m.mapNative(
                new NativeAddress("libghost.so", 0x1000L, 0x1000L, null));
        assertEquals(MappedSourceLocation.Kind.NATIVE_UNKNOWN, loc.kind);
        assertEquals("libghost.so", loc.nativeModule);
    }

    @Test
    public void registerAndUnregister() {
        SourceNameMapper m = SourceNameMapper.getInstance();
        m.clearAll();
        SourceNameMapper.SymbolResolver dummy = new SourceNameMapper.SymbolResolver() {
            @Override public boolean supportsJava() { return true; }
            @Override
            public MappedSourceLocation mapJava(String c, String m, String f, SourceLocation s) {
                return new MappedSourceLocation(c, m, f, "DUMMY", m, f,
                        null, 0, 0L, null, MappedSourceLocation.Kind.JAVA);
            }
            @Override public MappedSourceLocation mapNative(NativeAddress a) { return null; }
        };
        int before = m.resolverCount();
        m.register(dummy);
        assertEquals(before + 1, m.resolverCount());
        m.unregister(dummy);
        assertEquals(before, m.resolverCount());
    }
}
