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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v2.5 P3: 模拟 Fragment 视图生命周期的 Evictor 测试.
 *
 * ComposePreviewFragment.onViewCreated → evictor.start()
 * ComposePreviewFragment.onDestroyView → evictor.stop()
 *
 * 这里用 [LifecycleHost] 模拟, 验证生命周期集成行为.
 */
class EvictorLifecycleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun tearDown() {
        DexMmapPoolRegistry.reset()
    }

    @Test
    fun `lifecycle host start then stop mirrors fragment onViewCreated to onDestroyView`() = runTest {
        val pool = DexMmapPool()
        val host = LifecycleHost(pool)
        // 模拟 onViewCreated
        host.onViewCreated()
        assertTrue("evictor should be running after onViewCreated", host.evictor?.isRunning() == true)
        // 模拟 onDestroyView
        host.onDestroyView()
        assertNull("evictor ref should be null after onDestroyView", host.evictor)
    }

    @Test
    fun `lifecycle host onViewCreated twice is idempotent`() {
        val pool = DexMmapPool()
        val host = LifecycleHost(pool)
        host.onViewCreated()
        val first = host.evictor
        host.onViewCreated()  // 第二次应该不覆盖
        assertTrue("second onViewCreated should not replace existing evictor", host.evictor === first)
        host.onDestroyView()
    }

    @Test
    fun `lifecycle host onDestroyView before onViewCreated is noop`() {
        val pool = DexMmapPool()
        val host = LifecycleHost(pool)
        // 没 start 就 stop, 不抛
        host.onDestroyView()
        assertNull(host.evictor)
    }

    @Test
    fun `lifecycle host evictor actually evicts while running`() = runTest {
        val pool = DexMmapPool()
        val host = LifecycleHost(pool)
        // 准备 stale entry
        val dex = tmp.newFile("stale.dex")
        dex.writeBytes(ByteArray(64) { 0x00 })
        val entry = pool.acquire(dex)!!
        pool.release(entry)
        // 启动 (短 interval + 0 ms 阈值, 立刻 evict)
        host.onViewCreated()
        delay(150L)
        host.onDestroyView()
        // 至少跑过 1 次 evict
        assertTrue(
            "evictor should run at least once, got ${host.evictor?.evictRunCount()}",
            (host.totalEvictedEntries()) >= 1L,
        )
    }

    /**
     * 模拟 ComposePreviewFragment 持有 Evictor 的方式.
     */
    private class LifecycleHost(private val pool: DexMmapPool) {
        var evictor: DexMmapPoolEvictor? = null
            private set

        fun onViewCreated() {
            if (evictor == null) {
                evictor = DexMmapPoolEvictor(
                    pool = pool,
                    intervalMs = 50L,
                    maxAgeMs = 0L,
                ).apply { start() }
            }
        }

        fun onDestroyView() {
            evictor?.stop()
            evictor = null
        }

        fun totalEvictedEntries(): Long = evictor?.evictedEntryCount() ?: 0L
    }
}
