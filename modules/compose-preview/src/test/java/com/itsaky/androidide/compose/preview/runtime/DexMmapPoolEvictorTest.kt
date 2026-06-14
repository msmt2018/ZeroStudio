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

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * v2.5 P2: DexMmapPoolEvictor 单元测试.
 */
class DexMmapPoolEvictorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var pool: DexMmapPool

    @After
    fun tearDown() {
        DexMmapPoolRegistry.reset()
    }

    @Test
    fun `start launches background coroutine and isRunning becomes true`() = runTest {
        pool = DexMmapPool()
        val evictor = DexMmapPoolEvictor(
            pool = pool,
            intervalMs = 50L,
            maxAgeMs = 0L,
        )
        assertFalse(evictor.isRunning())
        evictor.start()
        assertTrue(evictor.isRunning())
        evictor.stop()
        assertFalse(evictor.isRunning())
    }

    @Test
    fun `start is idempotent`() {
        pool = DexMmapPool()
        val evictor = DexMmapPoolEvictor(pool, intervalMs = 100_000L, maxAgeMs = 100_000L)
        evictor.start()
        evictor.start()  // 二次启动应该 noop
        evictor.stop()
    }

    @Test
    fun `stop without start is noop`() {
        pool = DexMmapPool()
        val evictor = DexMmapPoolEvictor(pool)
        evictor.stop()  // 不抛
    }

    @Test
    fun `evictor runs evictStale at interval`() = runTest {
        pool = DexMmapPool()
        // 准备一个 stale dex
        val dex = tmp.newFile("stale.dex")
        dex.writeBytes(ByteArray(64) { 0x00 })
        val entry = pool.acquire(dex)
        assertNotNull(entry)
        pool.release(entry!!)
        // refCount=0, 5 分钟前
        val evictor = DexMmapPoolEvictor(
            pool = pool,
            intervalMs = 50L,
            maxAgeMs = 0L,  // 任何 refCount=0 立即 evict
        )
        evictor.start()
        // 等几次 interval
        delay(200L)
        evictor.stop()
        // 至少跑过 1 次 evict (可能多次)
        assertTrue("evictor should run at least once, got ${evictor.evictRunCount()}", evictor.evictRunCount() >= 1L)
        // 至少释放 1 个 entry
        assertTrue("should evict at least 1 entry, got ${evictor.evictedEntryCount()}", evictor.evictedEntryCount() >= 1L)
    }

    @Test
    fun `evictor stats start at 0`() {
        pool = DexMmapPool()
        val evictor = DexMmapPoolEvictor(pool, intervalMs = 100_000L, maxAgeMs = 100_000L)
        assertEquals(0L, evictor.evictRunCount())
        assertEquals(0L, evictor.evictedEntryCount())
    }

    private fun assertNotNull(o: Any?) {
        org.junit.Assert.assertNotNull(o)
    }
}
