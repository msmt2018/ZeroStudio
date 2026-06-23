package com.zerostudio.language.jni;

import com.zerostudio.language.model.LanguageId;
import com.zerostudio.language.model.Reference;
import org.junit.Before;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TreeSitterBridgeTest {

    @Before
    public void warmup() {
        TreeSitterBridge.ensureLoaded();
    }

    @Test
    public void parsesCpp() {
        TreeSitterBridge.ParseResult r = TreeSitterBridge.parse(LanguageId.CPP, "F.cpp",
                "#include <iostream>\nclass Foo {};\nint bar() { return 0; }\n");
        assertNotNull(r);
        assertTrue(r.references.stream().anyMatch(ref ->
                ref.kind == Reference.ReferenceKind.CLASS && ref.name.equals("Foo")));
    }

    @Test
    public void parsesKotlin() {
        TreeSitterBridge.ParseResult r = TreeSitterBridge.parse(LanguageId.KOTLIN, "F.kt",
                "package com.example\nclass MainActivity\n");
        assertNotNull(r);
        assertEquals("com.example", r.references.stream()
                .filter(ref -> ref.kind == Reference.ReferenceKind.IMPORT)
                .map(ref -> ref.name).findFirst().orElse(""));
    }

    @Test
    public void parsesJava() {
        TreeSitterBridge.ParseResult r = TreeSitterBridge.parse(LanguageId.JAVA, "F.java",
                "package com.x; class A {}");
        assertNotNull(r);
        assertTrue(r.references.stream().anyMatch(ref -> ref.name.equals("A")));
    }

    @Test
    public void unknownLanguageReturnsEmpty() {
        TreeSitterBridge.ParseResult r = TreeSitterBridge.parse(LanguageId.UNKNOWN, "x", "");
        assertNotNull(r);
        assertTrue(r.references.isEmpty());
    }

    @Test
    public void stateIsEitherNativeOrFallback() {
        // 在没有 native lib 的环境下，可能处于 FALLBACK（loadLibrary 抛错前）、
        // NATIVE_LOADED（有 native lib）或 NATIVE_FAILED（loadLibrary 抛错后）。
        TreeSitterBridge.State s = TreeSitterBridge.state(LanguageId.CPP);
        assertTrue("expected FALLBACK / NATIVE_LOADED / NATIVE_FAILED, got: " + s,
                s == TreeSitterBridge.State.FALLBACK
                || s == TreeSitterBridge.State.NATIVE_LOADED
                || s == TreeSitterBridge.State.NATIVE_FAILED);
    }

    @Test
    public void ensureLoadedIsIdempotent() {
        TreeSitterBridge.ensureLoaded();
        TreeSitterBridge.ensureLoaded();
        // 没抛异常即可
    }

    @Test
    public void rootNodeTypeMatchesLanguage() {
        TreeSitterBridge.ParseResult cpp = TreeSitterBridge.parse(LanguageId.CPP, "a.cpp", "int x;");
        TreeSitterBridge.ParseResult kt = TreeSitterBridge.parse(LanguageId.KOTLIN, "a.kt", "val x = 1");
        assertEquals("translation_unit", cpp.rootNodeType);
        assertEquals("kotlin_file", kt.rootNodeType);
    }
}
