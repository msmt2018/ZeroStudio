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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * v2.5 P0 P3-FE-01: DexMmapPool 单元测试.
 */
class DexMmapPoolTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var pool: DexMmapPool
    private lateinit var dexFile: File

    @Before
    fun setUp() {
        pool = DexMmapPool()
        // 造一个伪 dex 文件: magic 头 + 1KB 0x00
        dexFile = tmp.newFile("test.dex")
        dexFile.writeBytes(ByteArray(1024) { (it and 0xFF).toByte() })
    }

    @After
    fun tearDown() {
        pool.clear()
    }

    @Test
    fun `acquire returns entry with positive refCount`() {
        val entry = pool.acquire(dexFile)
        assertNotNull(entry)
        assertEquals(1, entry!!.refCount.get())
        assertEquals(1024L, entry.size)
        assertEquals(1, pool.activeCount())
    }

    @Test
    fun `acquire same file twice returns same entry`() {
        val a = pool.acquire(dexFile)
        val b = pool.acquire(dexFile)
        assertNotNull(a)
        assertNotNull(b)
        assertSame(a, b)
        assertEquals(2, a!!.refCount.get())
    }

    @Test
    fun `release decrements refCount and removes on zero`() {
        val a = pool.acquire(dexFile)
        pool.acquire(dexFile)
        pool.release(a!!)
        assertEquals(1, a.refCount.get())
        assertEquals(1, pool.activeCount())
        pool.release(a)
        assertEquals(0, pool.activeCount())
    }

    @Test
    fun `acquire non-existent file returns null`() {
        val missing = File(tmp.newFolder("missing"), "ghost.dex")
        assertNull(pool.acquire(missing))
    }

    @Test
    fun `stats report hit rate correctly`() {
        // miss 1
        pool.acquire(dexFile)!!
        // hit 2
        pool.acquire(dexFile)
        pool.acquire(dexFile)
        val stats = pool.stats()
        assertEquals(1, stats.missCount)
        assertEquals(2, stats.hitCount)
        assertEquals(3, stats.totalAcquires)
        assertEquals(0, stats.totalReleases)
        assertTrue(stats.hitRate > 0.6 && stats.hitRate < 0.7)
    }

    @Test
    fun `canonical path is the key for different representations`() {
        val subdir = tmp.newFolder("nested")
        val real = File(subdir, "real.dex")
        real.writeBytes(ByteArray(64) { 0x42 })
        val viaSubdir = File(subdir, "./real.dex")
        val a = pool.acquire(real)
        val b = pool.acquire(viaSubdir)
        assertNotNull(a)
        assertNotNull(b)
        assertSame(a, b)
    }

    @Test
    fun `clear releases all entries`() {
        repeat(3) {
            pool.acquire(dexFile)!!
        }
        assertEquals(1, pool.activeCount())
        pool.clear()
        assertEquals(0, pool.activeCount())
    }
}
