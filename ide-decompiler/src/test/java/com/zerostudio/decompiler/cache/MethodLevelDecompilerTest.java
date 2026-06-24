package com.zerostudio.decompiler.cache;

import com.zerostudio.decompiler.api.DecompileRequest;
import com.zerostudio.decompiler.api.DecompileResult;
import com.zerostudio.decompiler.api.Decompiler;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class MethodLevelDecompilerTest {

    private static class MockDecompiler implements Decompiler {
        int callCount = 0;
        private final Map<String, DecompileResult> map = new HashMap<>();
        void put(String cls, String src) {
            map.put(cls, DecompileResult.ok(cls, src, new HashMap<>()));
        }
        @Override public String name() { return "mock"; }
        @Override public String version() { return "1.0"; }
        @Override public DecompileResult decompile(DecompileRequest request) {
            callCount++;
            return map.getOrDefault(request.className, DecompileResult.fail(request.className, "not found"));
        }
    }

    @Test
    public void splitsMethodsFromSource() {
        MockDecompiler mock = new MockDecompiler();
        mock.put("A", "public class A {\n" +
                "    public void foo() {\n" +
                "        int x = 1;\n" +
                "    }\n" +
                "    public int bar(int n) {\n" +
                "        return n + 1;\n" +
                "    }\n" +
                "}\n");
        MethodLevelDecompiler mld = new MethodLevelDecompiler(mock);
        DecompileResult r = mld.decompile(DecompileRequest.builder().className("A").classBytes(new byte[0]).build());
        assertTrue(r.isOk());
        List<String> methods = mld.listMethods("A");
        assertTrue("expected foo in " + methods, methods.contains("foo"));
        assertTrue("expected bar in " + methods, methods.contains("bar"));
    }

    @Test
    public void getMethodReturnsSignatureAndBody() {
        MockDecompiler mock = new MockDecompiler();
        mock.put("B", "class B {\n" +
                "    public void hello() {\n" +
                "        System.out.println(\"hi\");\n" +
                "    }\n" +
                "}\n");
        MethodLevelDecompiler mld = new MethodLevelDecompiler(mock);
        mld.decompile(DecompileRequest.builder().className("B").classBytes(new byte[0]).build());
        String body = mld.getMethod("B", "hello");
        assertNotNull(body);
        assertTrue(body.contains("hello()"));
        assertTrue(body.contains("println"));
    }

    @Test
    public void getMethodNonExistent() {
        MockDecompiler mock = new MockDecompiler();
        MethodLevelDecompiler mld = new MethodLevelDecompiler(mock);
        assertNull(mld.getMethod("Missing", "foo"));
    }

    @Test
    public void listMethodsForUnknownClass() {
        MethodLevelDecompiler mld = new MethodLevelDecompiler(new MockDecompiler());
        assertTrue(mld.listMethods("Missing").isEmpty());
    }

    @Test
    public void decompileFailureDoesNotCache() {
        MockDecompiler mock = new MockDecompiler();
        MethodLevelDecompiler mld = new MethodLevelDecompiler(mock);
        DecompileResult r = mld.decompile(DecompileRequest.builder().className("X").classBytes(new byte[0]).build());
        assertFalse(r.isOk());
        assertEquals(0, mld.cachedClassCount());
    }

    @Test
    public void secondCallDoesNotHitUnderlying() {
        MockDecompiler mock = new MockDecompiler();
        mock.put("C", "class C {\n" +
                "    void m() {}\n" +
                "}\n");
        MethodLevelDecompiler mld = new MethodLevelDecompiler(mock);
        DecompileRequest req = DecompileRequest.builder().className("C").classBytes(new byte[0]).build();
        mld.decompile(req);
        // 第二次：缓存命中，mock 不应再次被调用
        mld.decompile(req);
        assertEquals(1, mock.callCount);
    }

    @Test
    public void clearResetsCache() {
        MockDecompiler mock = new MockDecompiler();
        mock.put("D", "class D {\nvoid m(){}\n}\n");
        MethodLevelDecompiler mld = new MethodLevelDecompiler(mock);
        mld.decompile(DecompileRequest.builder().className("D").classBytes(new byte[0]).build());
        mld.clear();
        assertEquals(0, mld.cachedClassCount());
    }

    @Test
    public void nameAndVersion() {
        MethodLevelDecompiler mld = new MethodLevelDecompiler(new MockDecompiler());
        assertTrue(mld.name().contains("mock"));
        assertEquals("1.0", mld.version());
    }

    @Test
    public void handlesEmptySource() {
        MockDecompiler mock = new MockDecompiler();
        mock.put("E", "");
        MethodLevelDecompiler mld = new MethodLevelDecompiler(mock);
        mld.decompile(DecompileRequest.builder().className("E").classBytes(new byte[0]).build());
        // 空源码不会报错，可能有或没有方法
        assertNotNull(mld.listMethods("E"));
    }

    @Test
    public void lruEviction() {
        MockDecompiler mock = new MockDecompiler();
        for (int i = 0; i < 5; i++) {
            mock.put("C" + i, "class C" + i + " { void m() {} }\n");
        }
        MethodLevelDecompiler mld = new MethodLevelDecompiler(mock, 3);
        for (int i = 0; i < 5; i++) {
            mld.decompile(DecompileRequest.builder().className("C" + i).classBytes(new byte[0]).build());
        }
        assertTrue("cache should be capped at 3, got " + mld.cachedClassCount(), mld.cachedClassCount() <= 3);
    }
}
