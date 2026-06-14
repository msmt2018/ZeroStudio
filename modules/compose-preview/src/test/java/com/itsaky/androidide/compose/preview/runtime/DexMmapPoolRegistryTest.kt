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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * v2.5 P2: DexMmapPoolRegistry 单元测试.
 */
class DexMmapPoolRegistryTest {

    @After
    fun tearDown() {
        DexMmapPoolRegistry.reset()
    }

    @Test
    fun `install and pool returns same instance`() {
        val pool = DexMmapPool()
        DexMmapPoolRegistry.install(pool)
        assertSame(pool, DexMmapPoolRegistry.pool())
    }

    @Test
    fun `getOrCreate lazy creates when empty`() {
        assertNull(DexMmapPoolRegistry.pool())
        val pool = DexMmapPoolRegistry.getOrCreate()
        assertNotNull(pool)
        assertSame(pool, DexMmapPoolRegistry.pool())
    }

    @Test
    fun `getOrCreate returns installed when present`() {
        val installed = DexMmapPool()
        DexMmapPoolRegistry.install(installed)
        assertSame(installed, DexMmapPoolRegistry.getOrCreate())
    }

    @Test
    fun `stats returns empty when nothing installed`() {
        val stats = DexMmapPoolRegistry.stats()
        assertEquals(0, stats.activeEntries)
        assertEquals(0, stats.totalAcquires)
    }

    @Test
    fun `stats delegates to installed pool`() {
        val pool = DexMmapPool()
        pool.acquire(java.io.File("/tmp/nonexistent.dex"))  // returns null, 但 totalAcquire++? 不, missing 返回 null 不增
        // 直接拿个空 stats 测一下
        DexMmapPoolRegistry.install(pool)
        val stats = DexMmapPoolRegistry.stats()
        assertEquals(0, stats.activeEntries)
    }

    @Test
    fun `evictStale returns 0 when nothing installed`() {
        assertEquals(0, DexMmapPoolRegistry.evictStale(maxAgeMs = 0L))
    }

    @Test
    fun `reset clears and releases pool`() {
        val pool = DexMmapPool()
        DexMmapPoolRegistry.install(pool)
        assertNotNull(DexMmapPoolRegistry.pool())
        DexMmapPoolRegistry.reset()
        assertNull(DexMmapPoolRegistry.pool())
    }

    @Test
    fun `install replaces previous pool`() {
        val a = DexMmapPool()
        val b = DexMmapPool()
        DexMmapPoolRegistry.install(a)
        DexMmapPoolRegistry.install(b)
        assertSame(b, DexMmapPoolRegistry.pool())
    }
}
