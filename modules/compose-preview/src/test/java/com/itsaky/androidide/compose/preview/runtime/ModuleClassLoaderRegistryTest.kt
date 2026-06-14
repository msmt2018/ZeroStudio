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

import com.itsaky.androidide.compose.preview.data.source.ModuleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * v2.3 P0 单元测试.
 *
 * [ModuleClassLoaderRegistry] 在 v2.3 P0 重构后接受 `File` 作为优化目录, 不再依赖 Android [android.content.Context].
 * 测试用 [TemporaryFolder] 提供临时目录, 纯 JUnit 即可跑.
 */
class ModuleClassLoaderRegistryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newRegistry() = ModuleClassLoaderRegistry(
        optimizedRoot = tempFolder.newFolder("optimized"),
        fallbackParent = ClassLoader.getSystemClassLoader(),
    )

    @Test
    fun `01 ModuleInfo data class equality`() {
        val a = ModuleInfo(":app", "app", setOf(":core"), listOf(File("/tmp/a.dex")), listOf(File("/tmp/cp.jar")))
        val b = ModuleInfo(":app", "app", setOf(":core"), listOf(File("/tmp/a.dex")), listOf(File("/tmp/cp.jar")))
        assertEquals(a, b)
        val c = a.copy(name = "app2")
        assertFalse(a == c)
    }

    @Test
    fun `02 install with single module creates one loader`() {
        val reg = newRegistry()
        val main = ModuleInfo(":app", "app", emptySet(), emptyList(), emptyList())
        reg.install(listOf(main))
        assertEquals(1, reg.activeLoaderCount)
        assertNotNull(reg.mainLoader)
        reg.release()
        assertEquals(0, reg.activeLoaderCount)
    }

    @Test
    fun `03 install with 3 modules creates 3 loaders`() {
        val reg = newRegistry()
        val main = ModuleInfo(":app", "app", setOf(":feature:foo"), emptyList(), emptyList())
        val foo = ModuleInfo(":feature:foo", "feature_foo", setOf(":core:bar"), emptyList(), emptyList())
        val bar = ModuleInfo(":core:bar", "core_bar", emptySet(), emptyList(), emptyList())
        reg.install(listOf(main, foo, bar))
        assertEquals(3, reg.activeLoaderCount)
        reg.release()
    }

    @Test
    fun `04 install empty list throws`() {
        val reg = newRegistry()
        try {
            reg.install(emptyList())
            assert(false) { "should have thrown" }
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `05 loadClass returns null for non-existent class without throwing`() {
        val reg = newRegistry()
        val main = ModuleInfo(":app", "app", emptySet(), emptyList(), emptyList())
        reg.install(listOf(main))
        // 没真实 dex, 找不到是预期的 → 返 null
        val result = reg.loadClass("com.example.NonExistent")
        assertNull(result)
        // 计数器已增加 (即使失败)
        assertEquals(1L, reg.loadCount)
        reg.release()
    }

    @Test
    fun `06 loadClassFromModule requires gradlePath exists`() {
        val reg = newRegistry()
        val main = ModuleInfo(":app", "app", emptySet(), emptyList(), emptyList())
        reg.install(listOf(main))
        val result = reg.loadClassFromModule(":nonexistent", "Foo")
        assertNull(result)
        reg.release()
    }

    @Test
    fun `07 second install releases old loaders first`() {
        val reg = newRegistry()
        reg.install(listOf(ModuleInfo(":app", "app", emptySet(), emptyList(), emptyList())))
        assertEquals(1, reg.activeLoaderCount)
        reg.install(listOf(ModuleInfo(":lib", "lib", emptySet(), emptyList(), emptyList())))
        // 应该只看到新的 :lib, 旧的 :app 已 release
        assertEquals(1, reg.activeLoaderCount)
        assertNotNull(reg.mainLoader)
        reg.release()
    }

    @Test
    fun `08 cache hit on second loadClass call`() {
        val reg = newRegistry()
        reg.install(listOf(ModuleInfo(":app", "app", emptySet(), emptyList(), emptyList())))
        reg.loadClass("com.foo.Bar")  // miss, +1 loadCount
        reg.loadClass("com.foo.Bar")  // cache hit, +1 cacheHitCount
        assertEquals(2L, reg.loadCount)
        assertEquals(1L, reg.cacheHitCount)
        reg.release()
    }

    @Test
    fun `09 setRuntimeDex triggers invalidateAll`() {
        val reg = newRegistry()
        reg.install(listOf(ModuleInfo(":app", "app", emptySet(), emptyList(), emptyList())))
        reg.loadClass("foo")
        assertEquals(1, reg.classCacheSize)
        reg.setRuntimeDex(File("/tmp/fake.dex"))
        // 缓存已清
        assertEquals(0, reg.classCacheSize)
        reg.release()
    }

    @Test
    fun `10 activeLoaderCount is consistent across calls`() {
        val reg = newRegistry()
        assertEquals(0, reg.activeLoaderCount)
        reg.install(listOf(ModuleInfo(":a", "a", emptySet(), emptyList(), emptyList())))
        assertEquals(1, reg.activeLoaderCount)
        reg.install(listOf(
            ModuleInfo(":a", "a", emptySet(), emptyList(), emptyList()),
            ModuleInfo(":b", "b", setOf(":a"), emptyList(), emptyList())
        ))
        assertEquals(2, reg.activeLoaderCount)
        reg.release()
        assertEquals(0, reg.activeLoaderCount)
    }
}
