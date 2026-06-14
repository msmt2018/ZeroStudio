/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.runtime

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.3 P1 单元测试.
 *
 * - register / getEntry
 * - setIndex 边界 (越界 clamp)
 * - 多 provider 同 function
 * - clear / install / uninstall
 * - register 失败 (class 不存在 / 不是 PreviewParameterProvider)
 */
class PreviewParameterRegistryTest {

    @After
    fun tearDown() {
        PreviewParameterRegistry.uninstall()
    }

    // --- helper providers ---

    class ColorProvider : PreviewParameterProvider<String> {
        override val values: Sequence<String> = sequenceOf("red", "green", "blue")
    }

    class IntProvider : PreviewParameterProvider<Int> {
        override val values: Sequence<Int> = sequenceOf(10, 20, 30, 40)
    }

    class EmptyProvider : PreviewParameterProvider<String> {
        override val values: Sequence<String> = emptySequence()
    }

    class ThrowingProvider : PreviewParameterProvider<String> {
        override val values: Sequence<String> = throw RuntimeException("boom")
    }

    class NotAProvider  // 没有实现 PreviewParameterProvider

    @Test
    fun `01 register loads values eagerly`() {
        val reg = PreviewParameterRegistry.get()
        val ok = reg.register("MyPreview", ColorProvider::class.java.name)
        assertTrue(ok)
        val entry = reg.getEntry("MyPreview", ColorProvider::class.java.name)
        assertNotNull(entry)
        assertEquals(3, entry!!.size)
        assertEquals("red", entry.currentValue())
    }

    @Test
    fun `02 setIndex changes current value`() {
        val reg = PreviewParameterRegistry.get()
        reg.register("MyPreview", ColorProvider::class.java.name)
        val updated = reg.setIndex("MyPreview", ColorProvider::class.java.name, 2)
        assertEquals("blue", updated?.currentValue())
    }

    @Test
    fun `03 setIndex out of bounds clamps to 0`() {
        val reg = PreviewParameterRegistry.get()
        reg.register("MyPreview", ColorProvider::class.java.name)
        val updated = reg.setIndex("MyPreview", ColorProvider::class.java.name, 99)
        assertEquals(0, updated?.currentIndex)
    }

    @Test
    fun `04 setIndex negative clamps to 0`() {
        val reg = PreviewParameterRegistry.get()
        reg.register("MyPreview", ColorProvider::class.java.name)
        val updated = reg.setIndex("MyPreview", ColorProvider::class.java.name, -1)
        assertEquals(0, updated?.currentIndex)
    }

    @Test
    fun `05 registerAll loads multiple providers`() {
        val reg = PreviewParameterRegistry.get()
        val ok = reg.registerAll("MultiPreview", listOf(
            ColorProvider::class.java.name,
            IntProvider::class.java.name,
        ))
        assertEquals(2, ok)
        val entries = reg.get("MultiPreview")
        assertEquals(2, entries.size)
        assertEquals(3, entries[ColorProvider::class.java.name]?.size)
        assertEquals(4, entries[IntProvider::class.java.name]?.size)
    }

    @Test
    fun `06 independent setIndex per provider`() {
        val reg = PreviewParameterRegistry.get()
        reg.registerAll("MultiPreview", listOf(
            ColorProvider::class.java.name,
            IntProvider::class.java.name,
        ))
        reg.setIndex("MultiPreview", ColorProvider::class.java.name, 1)  // green
        reg.setIndex("MultiPreview", IntProvider::class.java.name, 3)    // 40
        val color = reg.getEntry("MultiPreview", ColorProvider::class.java.name)
        val int = reg.getEntry("MultiPreview", IntProvider::class.java.name)
        assertEquals("green", color?.currentValue())
        assertEquals(40, int?.currentValue())
    }

    @Test
    fun `07 register unknown class returns false silently`() {
        val reg = PreviewParameterRegistry.get()
        val ok = reg.register("MyPreview", "com.example.NonExistent")
        assertEquals(false, ok)
        assertEquals(0, reg.functionCount())
    }

    @Test
    fun `08 register non-PreviewParameterProvider class returns false`() {
        val reg = PreviewParameterRegistry.get()
        val ok = reg.register("MyPreview", NotAProvider::class.java.name)
        assertEquals(false, ok)
    }

    @Test
    fun `09 register empty provider loads empty values`() {
        val reg = PreviewParameterRegistry.get()
        val ok = reg.register("MyPreview", EmptyProvider::class.java.name)
        assertTrue(ok)
        val entry = reg.getEntry("MyPreview", EmptyProvider::class.java.name)
        assertEquals(0, entry!!.size)
    }

    @Test
    fun `10 register throwing provider returns false`() {
        val reg = PreviewParameterRegistry.get()
        val ok = reg.register("MyPreview", ThrowingProvider::class.java.name)
        assertEquals(false, ok)
    }

    @Test
    fun `11 getEntry for unknown function returns null`() {
        val reg = PreviewParameterRegistry.get()
        val entry = reg.getEntry("Unknown", ColorProvider::class.java.name)
        assertNull(entry)
    }

    @Test
    fun `12 clear empties store`() {
        val reg = PreviewParameterRegistry.get()
        reg.register("A", ColorProvider::class.java.name)
        reg.register("B", IntProvider::class.java.name)
        assertEquals(2, reg.functionCount())
        reg.clear()
        assertEquals(0, reg.functionCount())
    }

    @Test
    fun `13 install clears existing state`() {
        val reg = PreviewParameterRegistry.get()
        reg.register("A", ColorProvider::class.java.name)
        assertEquals(1, reg.functionCount())
        PreviewParameterRegistry.install()
        assertEquals(0, reg.functionCount())
    }

    @Test
    fun `14 setIndex on empty entry (size=0) clamps to 0`() {
        val reg = PreviewParameterRegistry.get()
        reg.register("MyPreview", EmptyProvider::class.java.name)
        val updated = reg.setIndex("MyPreview", EmptyProvider::class.java.name, 5)
        assertEquals(0, updated?.currentIndex)
    }

    @Test
    fun `15 totalEntryCount counts across functions`() {
        val reg = PreviewParameterRegistry.get()
        reg.register("A", ColorProvider::class.java.name)  // 1 entry
        reg.registerAll("B", listOf(                       // 2 entries
            ColorProvider::class.java.name,
            IntProvider::class.java.name,
        ))
        assertEquals(2, reg.functionCount())
        assertEquals(3, reg.totalEntryCount())
    }
}
