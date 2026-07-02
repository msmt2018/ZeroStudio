/*
 *  ZeroStudio IDE - 断点注入代码生成器 单元测试
 *
 *  重点验证:
 *    1) 生成代码不抛异常
 *    2) 输出包含期望的常量 / 入口 / 数据类
 *    3) 多个断点都能正确生成
 *    4) 包含 sourceFile / line 的字符串字面量能转义 (防 Kotlin 字符串语法错)
 */

package com.itsaky.androidide.debugger.connection.host

import com.itsaky.androidide.debugger.connection.DebugTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BreakpointInjectionCodeGenTest {

    private val target = DebugTarget("com.example.app", "com.example.app.MainActivity")

    @Test
    fun `generated code is non-empty and contains package + class markers`() {
        val gen = BreakpointInjectionCodeGen()
        val src = gen.generate(target = target, breakpoints = emptyList())
        assertTrue("output should not be empty", src.isNotEmpty())
        assertTrue("should contain generated package", src.contains("package " + BreakpointInjectionCodeGen.GENERATED_PACKAGE))
        assertTrue("should contain @file:JvmName", src.contains("@file:JvmName(\"${BreakpointInjectionCodeGen.GENERATED_CLASS}\")"))
        assertTrue("should contain handshake routine", src.contains("handshake()"))
        assertTrue("should contain jdwp path", src.contains("abstract:jdwp"))
    }

    @Test
    fun `generated code includes each breakpoint as a GeneratedBreakpoint line`() {
        val gen = BreakpointInjectionCodeGen()
        val bps = listOf(
            BreakpointRequest("bp1", "com/example/Foo.kt", 42),
            BreakpointRequest("bp2", "com/example/Bar.kt", 100),
        )
        val src = gen.generate(target = target, breakpoints = bps)
        assertTrue("should include bp1", src.contains("\"bp1\""))
        assertTrue("should include bp2", src.contains("\"bp2\""))
        assertTrue("should include line 42", src.contains(", 42,"))
        assertTrue("should include line 100", src.contains(", 100,"))
    }

    @Test
    fun `source file paths with quotes are escaped`() {
        val gen = BreakpointInjectionCodeGen()
        val bps = listOf(BreakpointRequest("bp1", "weird\"name.kt", 10))
        val src = gen.generate(target = target, breakpoints = bps)
        // 双引号转义为 \"
        assertTrue("escaped quote should be in source", src.contains("\\\""))
    }

    @Test
    fun `condition is rendered as a string literal when provided`() {
        val gen = BreakpointInjectionCodeGen()
        val bps = listOf(BreakpointRequest("bp1", "Foo.kt", 10, condition = "x > 5"))
        val src = gen.generate(target = target, breakpoints = bps)
        assertTrue("should contain condition string", src.contains("\"x > 5\""))
    }

    @Test
    fun `condition null is rendered as null literal`() {
        val gen = BreakpointInjectionCodeGen()
        val bps = listOf(BreakpointRequest("bp1", "Foo.kt", 10, condition = null))
        val src = gen.generate(target = target, breakpoints = bps)
        assertTrue("null condition should be present", src.contains(", null)"))
    }

    @Test
    fun `custom jdwp path is reflected in header comment`() {
        val gen = BreakpointInjectionCodeGen()
        val src = gen.generate(target = target, breakpoints = emptyList(), jdwpBindAddr = "127.0.0.1:65535")
        assertTrue("header should contain custom jdwp path", src.contains("127.0.0.1:65535"))
    }

    @Test
    fun `target package and activity are reflected in header comment`() {
        val gen = BreakpointInjectionCodeGen()
        val t = DebugTarget("com.example.app", "com.example.app.MainActivity")
        val src = gen.generate(target = t, breakpoints = emptyList())
        assertTrue("header should contain package", src.contains("com.example.app"))
        assertTrue("header should contain activity", src.contains("com.example.app.MainActivity"))
    }

    @Test
    fun `generated code mentions all major components`() {
        val gen = BreakpointInjectionCodeGen()
        val src = gen.generate(target = target, breakpoints = emptyList())
        // 关键 class / method 标识
        listOf("JdwpClient", "BreakpointResolver", "BreakpointListener",
            "GeneratedBreakpoint", "main(", "openJdwp").forEach { token ->
            assertTrue("missing token in generated source: $token", src.contains(token))
        }
    }

    @Test
    fun `id of breakpoint is preserved exactly`() {
        val gen = BreakpointInjectionCodeGen()
        val bp = BreakpointRequest("my-unique-bp-12345", "Foo.kt", 7)
        val src = gen.generate(target = target, breakpoints = listOf(bp))
        assertTrue("id should be present unchanged", src.contains("\"my-unique-bp-12345\""))
    }
}
