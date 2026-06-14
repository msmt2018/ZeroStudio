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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.5 P0 P3-FE-03: TimingRegistry 单元测试.
 */
class TimingRegistryTest {

    @After
    fun tearDown() {
        TimingRegistry.reset()
    }

    @Test
    fun `record accumulates samples per phase`() {
        TimingRegistry.record(TimingRegistry.Phase.COMPILE, 100L)
        TimingRegistry.record(TimingRegistry.Phase.COMPILE, 200L)
        TimingRegistry.record(TimingRegistry.Phase.RENDER, 50L)

        val compile = TimingRegistry.statsFor(TimingRegistry.Phase.COMPILE)
        assertEquals(2, compile.count)
        assertEquals(150.0, compile.avgMs, 0.01)
        assertEquals(200L, compile.maxMs)
        assertEquals(300L, compile.totalMs)

        val render = TimingRegistry.statsFor(TimingRegistry.Phase.RENDER)
        assertEquals(1, render.count)
        assertEquals(50.0, render.avgMs, 0.01)
    }

    @Test
    fun `time wrapper measures and records elapsed`() {
        val result = TimingRegistry.time(TimingRegistry.Phase.DEX) {
            Thread.sleep(5)
            "done"
        }
        assertEquals("done", result)
        val stats = TimingRegistry.statsFor(TimingRegistry.Phase.DEX)
        assertEquals(1, stats.count)
        assertTrue("expected >= 5ms, got ${stats.avgMs}", stats.avgMs >= 4.0)
    }

    @Test
    fun `rolling window drops oldest beyond capacity`() {
        // Default capacity = 64
        repeat(100) { i -> TimingRegistry.record(TimingRegistry.Phase.RENDER, (i + 1).toLong()) }
        val stats = TimingRegistry.statsFor(TimingRegistry.Phase.RENDER)
        assertEquals("rolling window should cap at 64", 64, stats.count)
        // The last 64 samples are values 37..100, so max=100, min=37
        assertEquals(100L, stats.maxMs)
        assertTrue("avg should be > 37 (dropped oldest)", stats.avgMs >= 50.0)
    }

    @Test
    fun `snapshot includes all phases`() {
        TimingRegistry.record(TimingRegistry.Phase.COMPILE, 10L)
        TimingRegistry.record(TimingRegistry.Phase.DEX, 20L)
        TimingRegistry.record(TimingRegistry.Phase.CLASSLOAD, 30L)
        TimingRegistry.record(TimingRegistry.Phase.RENDER, 40L)
        TimingRegistry.record(TimingRegistry.Phase.SERIALIZE, 5L)

        val snap = TimingRegistry.snapshot()
        assertEquals(5, snap.phases.size)
        assertEquals(1L, snap.phases[TimingRegistry.Phase.COMPILE]?.count)
        assertEquals(5L, TimingRegistry.totalRecordCount())
    }

    @Test
    fun `reset clears all windows`() {
        TimingRegistry.record(TimingRegistry.Phase.COMPILE, 100L)
        TimingRegistry.reset()
        assertEquals(0L, TimingRegistry.totalRecordCount())
        assertEquals(0L, TimingRegistry.statsFor(TimingRegistry.Phase.COMPILE).count)
    }

    @Test
    fun `negative elapsed is ignored`() {
        TimingRegistry.record(TimingRegistry.Phase.COMPILE, -1L)
        assertEquals(0L, TimingRegistry.statsFor(TimingRegistry.Phase.COMPILE).count)
    }
}
