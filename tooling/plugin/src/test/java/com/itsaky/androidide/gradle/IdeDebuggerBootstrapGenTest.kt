/*
 *  ZeroStudio IDE - tooling/plugin
 *
 *  子项目 10: 注入器生成器单元测试。
 *
 *  覆盖: escapeKtStringLiteral + parsePreheatBreakpoints + renderIdeDebuggerBootstrapKt
 *  三个纯函数 (不依赖 Gradle / Project, 易测)。
 */

package com.itsaky.androidide.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4.class)
class IdeDebuggerBootstrapGenTest {

    // ---- escapeKtStringLiteral ----

    @Test fun `escapeKtStringLiteral plain string unchanged`() {
        assertEquals("hello", escapeKtStringLiteral("hello"))
    }

    @Test fun `escapeKtStringLiteral empty string`() {
        assertEquals("", escapeKtStringLiteral(""))
    }

    @Test fun `escapeKtStringLiteral backslash`() {
        assertEquals("Foo\\\\Bar", escapeKtStringLiteral("Foo\\Bar"))
    }

    @Test fun `escapeKtStringLiteral double quote`() {
        assertEquals("Foo \\\"Bar\\\"", escapeKtStringLiteral("Foo \"Bar\""))
    }

    @Test fun `escapeKtStringLiteral newline`() {
        assertEquals("Foo\\nBar", escapeKtStringLiteral("Foo\nBar"))
    }

    @Test fun `escapeKtStringLiteral carriage return`() {
        assertEquals("Foo\\rBar", escapeKtStringLiteral("Foo\rBar"))
    }

    @Test fun `escapeKtStringLiteral tab`() {
        assertEquals("Foo\\tBar", escapeKtStringLiteral("Foo\tBar"))
    }

    @Test fun `escapeKtStringLiteral backspace`() {
        assertEquals("Foo\\bBar", escapeKtStringLiteral("Foo\bBar"))
    }

    @Test fun `escapeKtStringLiteral form feed`() {
        assertEquals("Foo\\fBar", escapeKtStringLiteral("Foo\u000cBar"))
    }

    @Test fun `escapeKtStringLiteral dollar sign`() {
        assertEquals("Foo\\\$bar", escapeKtStringLiteral("Foo\$bar"))
    }

    @Test fun `escapeKtStringLiteral ascii 0 to unicode escape`() {
        assertEquals("Foo\\u0000Bar", escapeKtStringLiteral("Foo\u0000Bar"))
    }

    @Test fun `escapeKtStringLiteral ascii control chars to unicode escape`() {
        // 0x01-0x1F + 0x7F 全部走 \u00xx
        assertEquals("\\u0001\\u001f\\u007f", escapeKtStringLiteral("\u0001\u001f\u007f"))
    }

    @Test fun `escapeKtStringLiteral utf8 preserved`() {
        // 中文字符保持原样
        assertEquals("中文", escapeKtStringLiteral("中文"))
    }

    // ---- parsePreheatBreakpoints ----

    @Test fun `parsePreheatBreakpoints null returns empty`() {
        assertEquals(emptyList<RenderedBreakpoint>(), parsePreheatBreakpoints(null))
    }

    @Test fun `parsePreheatBreakpoints empty string returns empty`() {
        assertEquals(emptyList<RenderedBreakpoint>(), parsePreheatBreakpoints(""))
    }

    @Test fun `parsePreheatBreakpoints whitespace only returns empty`() {
        assertEquals(emptyList<RenderedBreakpoint>(), parsePreheatBreakpoints("   "))
    }

    @Test fun `parsePreheatBreakpoints single entry`() {
        val result = parsePreheatBreakpoints("src=MainActivity.kt:10:5")
        assertEquals(1, result.size)
        assertEquals(RenderedBreakpoint("MainActivity.kt", 10, 5), result[0])
    }

    @Test fun `parsePreheatBreakpoints multiple entries`() {
        val result = parsePreheatBreakpoints("src=A.kt:1:0;src=B.kt:2:3;src=C.kt:3:0")
        assertEquals(3, result.size)
        assertEquals(RenderedBreakpoint("A.kt", 1, 0), result[0])
        assertEquals(RenderedBreakpoint("B.kt", 2, 3), result[1])
        assertEquals(RenderedBreakpoint("C.kt", 3, 0), result[2])
    }

    @Test fun `parsePreheatBreakpoints column 0 is default-like`() {
        val result = parsePreheatBreakpoints("src=A.kt:10:0")
        assertEquals(0, result[0].column)
    }

    @Test fun `parsePreheatBreakpoints trailing semicolon skipped`() {
        val result = parsePreheatBreakpoints("src=A.kt:1:0;")
        assertEquals(1, result.size)
    }

    @Test fun `parsePreheatBreakpoints leading semicolon skipped`() {
        val result = parsePreheatBreakpoints(";src=A.kt:1:0")
        assertEquals(1, result.size)
    }

    @Test fun `parsePreheatBreakpoints entry with spaces trimmed`() {
        val result = parsePreheatBreakpoints("  src=A.kt:1:0 ;  src=B.kt:2:0  ")
        assertEquals(2, result.size)
    }

    @Test fun `parsePreheatBreakpoints throws on missing src prefix`() {
        try {
            parsePreheatBreakpoints("Foo.kt:1:0")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("src="))
        }
    }

    @Test fun `parsePreheatBreakpoints throws on uppercase SRC`() {
        try {
            parsePreheatBreakpoints("SRC=Foo.kt:1:0")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // OK
        }
    }

    @Test fun `parsePreheatBreakpoints throws on file containing colon`() {
        try {
            parsePreheatBreakpoints("src=Foo:Bar.kt:1:0")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("':"))
        }
    }

    @Test fun `parsePreheatBreakpoints throws on file containing semicolon`() {
        try {
            parsePreheatBreakpoints("src=Foo;Bar.kt:1:0")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("';'"))
        }
    }

    @Test fun `parsePreheatBreakpoints throws on non-integer line`() {
        try {
            parsePreheatBreakpoints("src=Foo.kt:abc:0")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("line"))
        }
    }

    @Test fun `parsePreheatBreakpoints throws on non-integer column`() {
        try {
            parsePreheatBreakpoints("src=Foo.kt:1:xyz")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("column"))
        }
    }

    @Test fun `parsePreheatBreakpoints throws on negative line`() {
        try {
            parsePreheatBreakpoints("src=Foo.kt:-1:0")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("line"))
        }
    }

    @Test fun `parsePreheatBreakpoints throws on empty file`() {
        try {
            parsePreheatBreakpoints("src=:1:0")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("file"))
        }
    }

    @Test fun `parsePreheatBreakpoints throws on wrong number of parts`() {
        try {
            parsePreheatBreakpoints("src=Foo.kt:1")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // OK
        }
    }

    // ---- renderIdeDebuggerBootstrapKt ----

    @Test fun `renderIdeDebuggerBootstrapKt contains all 4 sections`() {
        val out = renderIdeDebuggerBootstrapKt(
            ideVersion = "1.0.0",
            localServerName = "ide-debug-bridge-test",
            extras = "sdk=33",
            buildTimestampMs = 1700000000000L,
            preheatBreakpoints = emptyList(),
        )
        // 1) 常量段
        assertTrue("missing IDE_DEBUGGER_VERSION: $out", out.contains("const val IDE_DEBUGGER_VERSION: String = \"1.0.0\""))
        assertTrue("missing LOCAL_SERVER_NAME: $out", out.contains("const val LOCAL_SERVER_NAME: String = \"ide-debug-bridge-test\""))
        assertTrue("missing HELLO_PROTOCOL_EXTRA_FIELDS: $out", out.contains("const val HELLO_PROTOCOL_EXTRA_FIELDS: String = \"sdk=33\""))
        assertTrue("missing BUILD_TIMESTAMP_MS: $out", out.contains("const val BUILD_TIMESTAMP_MS: Long = 1700000000000"))
        // 2) data class
        assertTrue("missing BreakpointLocation: $out", out.contains("data class BreakpointLocation(\n            val sourceFile: String,\n            val line: Int,\n            val column: Int = 0,\n        )"))
        // 3) PREHEAT_BREAKPOINTS
        assertTrue("missing PREHEAT_BREAKPOINTS: $out", out.contains("val PREHEAT_BREAKPOINTS: List<BreakpointLocation>"))
        // 4) init API
        assertTrue("missing init(application): $out", out.contains("fun init(application: Application)"))
        assertTrue("missing startReverseConnectThread: $out", out.contains("HostAttachAgentBootstrap.startReverseConnectThread(application, LOCAL_SERVER_NAME)"))
    }

    @Test fun `renderIdeDebuggerBootstrapKt package is correct`() {
        val out = renderIdeDebuggerBootstrapKt("1.0.0", "x", "x", 0L, emptyList())
        assertTrue("missing package: $out", out.contains("package com.itsaky.androidide.zerostudio.ide.debugger.host.generated"))
    }

    @Test fun `renderIdeDebuggerBootstrapKt auto-generated header`() {
        val out = renderIdeDebuggerBootstrapKt("1.0.0", "x", "x", 0L, emptyList())
        assertTrue("missing AUTO-GENERATED header: $out", out.contains("AUTO-GENERATED by IdeDebuggerInitScriptPlugin"))
    }

    @Test fun `renderIdeDebuggerBootstrapKt empty preheat`() {
        val out = renderIdeDebuggerBootstrapKt("1.0.0", "x", "x", 0L, emptyList())
        assertTrue("PREHEAT_BREAKPOINTS should be emptyList(): $out", out.contains("val PREHEAT_BREAKPOINTS: List<BreakpointLocation> = emptyList()"))
    }

    @Test fun `renderIdeDebuggerBootstrapKt one preheat bp`() {
        val bps = listOf(RenderedBreakpoint("MainActivity.kt", 10, 5))
        val out = renderIdeDebuggerBootstrapKt("1.0.0", "x", "x", 0L, bps)
        assertTrue("PREHEAT_BREAKPOINTS should contain MainActivity.kt: $out",
                out.contains("BreakpointLocation(\n            sourceFile = \"MainActivity.kt\""))
        assertTrue("PREHEAT_BREAKPOINTS should contain line=10: $out", out.contains("line = 10"))
        assertTrue("PREHEAT_BREAKPOINTS should contain column=5: $out", out.contains("column = 5"))
    }

    @Test fun `renderIdeDebuggerBootstrapKt multiple preheat bps`() {
        val bps = listOf(
            RenderedBreakpoint("A.kt", 1, 0),
            RenderedBreakpoint("B.kt", 2, 3),
        )
        val out = renderIdeDebuggerBootstrapKt("1.0.0", "x", "x", 0L, bps)
        assertTrue("missing A.kt: $out", out.contains("sourceFile = \"A.kt\""))
        assertTrue("missing B.kt: $out", out.contains("sourceFile = \"B.kt\""))
        assertTrue("missing line=1: $out", out.contains("line = 1"))
        assertTrue("missing line=2: $out", out.contains("line = 2"))
    }

    @Test fun `renderIdeDebuggerBootstrapKt escapes ideVersion with quote`() {
        val out = renderIdeDebuggerBootstrapKt(
            ideVersion = "1.0.0\"beta\"",
            localServerName = "x",
            extras = "x",
            buildTimestampMs = 0L,
            preheatBreakpoints = emptyList(),
        )
        assertTrue("ideVersion should be escaped: $out", out.contains("\"1.0.0\\\"beta\\\"\""))
    }

    @Test fun `renderIdeDebuggerBootstrapKt escapes localServerName with backslash`() {
        val out = renderIdeDebuggerBootstrapKt(
            ideVersion = "1.0",
            localServerName = "name\\with\\backslash",
            extras = "x",
            buildTimestampMs = 0L,
            preheatBreakpoints = emptyList(),
        )
        assertTrue("backslash should be escaped: $out", out.contains("\"name\\\\with\\\\backslash\""))
    }

    @Test fun `renderIdeDebuggerBootstrapKt escapes sourceFile with quote`() {
        val bps = listOf(RenderedBreakpoint("Foo \"Bar\".kt", 1, 0))
        val out = renderIdeDebuggerBootstrapKt("1.0", "x", "x", 0L, bps)
        assertTrue("sourceFile quote should be escaped: $out", out.contains("sourceFile = \"Foo \\\"Bar\\\".kt\""))
    }

    @Test fun `renderIdeDebuggerBootstrapKt escapes sourceFile with dollar`() {
        val bps = listOf(RenderedBreakpoint("Foo\$bar.kt", 1, 0))
        val out = renderIdeDebuggerBootstrapKt("1.0", "x", "x", 0L, bps)
        assertTrue("dollar should be escaped: $out", out.contains("sourceFile = \"Foo\\\$bar.kt\""))
    }

    @Test fun `renderIdeDebuggerBootstrapKt buildTimestampMs 0 is valid`() {
        val out = renderIdeDebuggerBootstrapKt("1.0", "x", "x", 0L, emptyList())
        assertTrue(out.contains("const val BUILD_TIMESTAMP_MS: Long = 0"))
    }

    @Test fun `renderIdeDebuggerBootstrapKt output is valid Kotlin with package and object`() {
        val out = renderIdeDebuggerBootstrapKt("1.0", "x", "x", 0L, emptyList())
        // 简单健全性: 有 package + object
        assertTrue("should have package: $out", out.contains("package com.itsaky.androidide.zerostudio.ide.debugger.host.generated"))
        assertTrue("should have object IdeDebuggerBootstrap: $out", out.contains("object IdeDebuggerBootstrap"))
        // init 应该用 ${'$'}IDE_DEBUGGER_VERSION 而不是 ${'$'}IDE_DEBUGGER_VERSION (避免字符串插值)
        assertTrue("init should use escaped dollar: $out", out.contains("\${'$'}IDE_DEBUGGER_VERSION"))
    }

    @Test fun `renderIdeDebuggerBootstrapKt output contains 4 import statements`() {
        val out = renderIdeDebuggerBootstrapKt("1.0", "x", "x", 0L, emptyList())
        assertTrue("should import Application: $out", out.contains("import android.app.Application"))
        assertTrue("should import Log: $out", out.contains("import android.util.Log"))
        assertTrue("should import HostAttachAgentBootstrap: $out", out.contains("import com.itsaky.androidide.zerostudio.ide.debugger.host.HostAttachAgentBootstrap"))
        assertTrue("should import AtomicBoolean: $out", out.contains("import java.util.concurrent.atomic.AtomicBoolean"))
    }

    @Test fun `renderIdeDebuggerBootstrapKt init has JvmStatic and Synchronized annotations`() {
        val out = renderIdeDebuggerBootstrapKt("1.0", "x", "x", 0L, emptyList())
        assertTrue("init should have @JvmStatic: $out", out.contains("@JvmStatic"))
        assertTrue("init should have @Synchronized: $out", out.contains("@Synchronized"))
    }
}
