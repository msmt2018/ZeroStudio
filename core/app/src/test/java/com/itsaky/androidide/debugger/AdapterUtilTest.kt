/*
 *  ZeroStudio IDE - Adapter 单元测试
 *
 *  PR-E1: 覆盖 VariablesAdapter.humanType 和 CallStackAdapter.shortName
 *  这两个纯函数,验证签名解析的正确性.
 */

package com.itsaky.androidide.debugger

import com.itsaky.androidide.debugger.adapter.CallStackAdapter
import com.itsaky.androidide.debugger.adapter.VariablesAdapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AdapterUtilTest {

    @Test
    fun `humanType handles primitive type signatures`() {
        assertEquals("void", VariablesAdapter.humanType("V"))
        assertEquals("boolean", VariablesAdapter.humanType("Z"))
        assertEquals("byte", VariablesAdapter.humanType("B"))
        assertEquals("char", VariablesAdapter.humanType("C"))
        assertEquals("short", VariablesAdapter.humanType("S"))
        assertEquals("int", VariablesAdapter.humanType("I"))
        assertEquals("long", VariablesAdapter.humanType("J"))
        assertEquals("float", VariablesAdapter.humanType("F"))
        assertEquals("double", VariablesAdapter.humanType("D"))
    }

    @Test
    fun `humanType strips L prefix and semicolon for class types`() {
        assertEquals("String", VariablesAdapter.humanType("Ljava/lang/String;"))
        assertEquals("Integer", VariablesAdapter.humanType("Ljava/lang/Integer;"))
    }

    @Test
    fun `humanType appends array marker`() {
        // Arrays keep the full signature so user can see element type.
        assertEquals("[Ljava/lang/String; (array)", VariablesAdapter.humanType("[Ljava/lang/String;"))
    }

    @Test
    fun `humanType returns question for null or empty`() {
        assertEquals("?", VariablesAdapter.humanType(null))
        assertEquals("?", VariablesAdapter.humanType(""))
    }

    @Test
    fun `shortName strips directory prefixes`() {
        assertEquals("Main.java", CallStackAdapter.shortName("com/example/Main.java"))
        assertEquals("Main.java", CallStackAdapter.shortName("com\\example\\Main.java"))
        assertEquals("Main.java", CallStackAdapter.shortName("Main.java"))
    }

    @Test
    fun `shortName returns question for null or empty`() {
        assertEquals("?", CallStackAdapter.shortName(null))
        assertEquals("?", CallStackAdapter.shortName(""))
    }
}
