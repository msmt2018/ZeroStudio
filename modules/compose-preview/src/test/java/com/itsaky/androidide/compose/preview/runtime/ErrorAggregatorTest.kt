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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * v2.2 P7 单元测试.
 *
 * 覆盖:
 * - add / fold / count (10 case)
 * - clear / snapshot 排序
 * - summaryByCategory
 * - category 嗅探
 * - 并发 add (10 thread × 100 add)
 * - per-key 隔离
 */
class ErrorAggregatorTest {

    @Test
    fun `01 add then snapshot returns single error with count 1`() {
        val agg = ErrorAggregator()
        agg.add(info(category = ErrorCategory.K2_COMPILE, msg = "type mismatch", sourceHash = 0x100))
        val snap = agg.snapshot()
        assertEquals(1, snap.size)
        assertEquals(1, snap[0].count)
        assertEquals("type mismatch", snap[0].message)
        assertEquals(0x100, snap[0].sourceHash)
    }

    @Test
    fun `02 same (cat, file, line) folds count`() {
        val agg = ErrorAggregator()
        val info = info(category = ErrorCategory.K2_COMPILE, file = "/p/A.kt", line = 42,
            msg = "type mismatch", sourceHash = 0x100)
        repeat(5) { agg.add(info) }
        val snap = agg.snapshot()
        assertEquals(1, snap.size)
        assertEquals(5, snap[0].count)
    }

    @Test
    fun `03 different line does not fold`() {
        val agg = ErrorAggregator()
        agg.add(info(category = ErrorCategory.K2_COMPILE, file = "/p/A.kt", line = 42, msg = "a"))
        agg.add(info(category = ErrorCategory.K2_COMPILE, file = "/p/A.kt", line = 43, msg = "b"))
        assertEquals(2, agg.snapshot().size)
    }

    @Test
    fun `04 different file does not fold`() {
        val agg = ErrorAggregator()
        agg.add(info(category = ErrorCategory.K2_COMPILE, file = "/p/A.kt", line = 42, msg = "a"))
        agg.add(info(category = ErrorCategory.K2_COMPILE, file = "/p/B.kt", line = 42, msg = "b"))
        assertEquals(2, agg.snapshot().size)
    }

    @Test
    fun `05 different category does not fold`() {
        val agg = ErrorAggregator()
        agg.add(info(category = ErrorCategory.K2_COMPILE, file = "/p/A.kt", line = 42, msg = "a"))
        agg.add(info(category = ErrorCategory.D8_DEX, file = "/p/A.kt", line = 42, msg = "b"))
        assertEquals(2, agg.snapshot().size)
    }

    @Test
    fun `06 null file folds together`() {
        val agg = ErrorAggregator()
        // swap 失败时通常没有 file:line
        agg.add(info(category = ErrorCategory.CLASSLOADER_SWAP, file = null, line = null, msg = "swap fail"))
        agg.add(info(category = ErrorCategory.CLASSLOADER_SWAP, file = null, line = null, msg = "swap fail"))
        val snap = agg.snapshot()
        assertEquals(1, snap.size)
        assertEquals(2, snap[0].count)
        assertNull(snap[0].file)
        assertNull(snap[0].line)
    }

    @Test
    fun `07 clear empties snapshot and resets counters`() {
        val agg = ErrorAggregator()
        agg.add(info(category = ErrorCategory.K2_COMPILE, msg = "a"))
        agg.add(info(category = ErrorCategory.D8_DEX, msg = "b"))
        assertEquals(2, agg.snapshot().size)
        assertEquals(2L, agg.totalAdds())
        agg.clear()
        assertEquals(0, agg.snapshot().size)
        assertEquals(0L, agg.totalAdds())
        assertEquals(0L, agg.totalErrors())
    }

    @Test
    fun `08 snapshot sorts by category asc then lastTs desc`() {
        val agg = ErrorAggregator()
        // 顺序添加, 同 category 内 lastTs 单调增 → 反向
        agg.add(info(category = ErrorCategory.K2_COMPILE, msg = "k2-1"))
        Thread.sleep(2)
        agg.add(info(category = ErrorCategory.K2_COMPILE, msg = "k2-2"))
        Thread.sleep(2)
        agg.add(info(category = ErrorCategory.D8_DEX, msg = "d8-1"))
        Thread.sleep(2)
        agg.add(info(category = ErrorCategory.D8_DEX, msg = "d8-2"))
        val snap = agg.snapshot()
        // 期望: D8_DEX (2条, d8-2 在前) → K2_COMPILE (2条, k2-2 在前)
        assertEquals(4, snap.size)
        assertEquals(ErrorCategory.D8_DEX, snap[0].category)
        assertEquals("d8-2", snap[0].message)
        assertEquals(ErrorCategory.D8_DEX, snap[1].category)
        assertEquals("d8-1", snap[1].message)
        assertEquals(ErrorCategory.K2_COMPILE, snap[2].category)
        assertEquals("k2-2", snap[2].message)
        assertEquals(ErrorCategory.K2_COMPILE, snap[3].category)
        assertEquals("k2-1", snap[3].message)
    }

    @Test
    fun `09 summaryByCategory counts per category`() {
        val agg = ErrorAggregator()
        agg.add(info(category = ErrorCategory.K2_COMPILE, msg = "a"))
        agg.add(info(category = ErrorCategory.K2_COMPILE, msg = "b"))
        agg.add(info(category = ErrorCategory.D8_DEX, msg = "c"))
        agg.add(info(category = ErrorCategory.CLASSLOADER_SWAP, msg = "d"))
        agg.add(info(category = ErrorCategory.CLASSLOADER_SWAP, msg = "e"))
        agg.add(info(category = ErrorCategory.CLASSLOADER_SWAP, msg = "f"))
        val summary = agg.summaryByCategory()
        assertEquals(2, summary[ErrorCategory.K2_COMPILE])
        assertEquals(1, summary[ErrorCategory.D8_DEX])
        assertEquals(3, summary[ErrorCategory.CLASSLOADER_SWAP])
        assertNull(summary[ErrorCategory.OTHER])
    }

    @Test
    fun `10 classifier maps dex message to D8_DEX`() {
        assertEquals(ErrorCategory.D8_DEX,
            ErrorCategory.classifyByMessage("DEX compilation failed"))
        assertEquals(ErrorCategory.D8_DEX,
            ErrorCategory.classifyByMessage("d8: missing class"))
    }

    @Test
    fun `11 classifier maps classnotfound to CLASSLOADER_SWAP`() {
        assertEquals(ErrorCategory.CLASSLOADER_SWAP,
            ErrorCategory.classifyByMessage("java.lang.ClassNotFoundException: Foo"))
        assertEquals(ErrorCategory.CLASSLOADER_SWAP,
            ErrorCategory.classifyByMessage("swap failed"))
    }

    @Test
    fun `12 classifier maps compile to K2_COMPILE`() {
        assertEquals(ErrorCategory.K2_COMPILE,
            ErrorCategory.classifyByMessage("Compilation failed: type mismatch"))
        assertEquals(ErrorCategory.K2_COMPILE,
            ErrorCategory.classifyByMessage("K2JVMCompiler: unresolved reference"))
    }

    @Test
    fun `13 classifier defaults to OTHER for unknown`() {
        assertEquals(ErrorCategory.OTHER,
            ErrorCategory.classifyByMessage("something weird happened"))
        assertEquals(ErrorCategory.OTHER, ErrorCategory.classifyByMessage(null))
    }

    @Test
    fun `14 PreviewErrorInfo_fromMessage sets category from classify`() {
        val info = PreviewErrorInfo.fromMessage("DEX failed", sourceHash = 0x100)
        assertEquals(ErrorCategory.D8_DEX, info.category)
        assertEquals(ErrorSeverity.ERROR, info.severity)
        assertEquals(0x100, info.sourceHash)
    }

    @Test
    fun `15 concurrent add from 10 threads is safe`() {
        val agg = ErrorAggregator()
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(10)
        val totalAdds = AtomicInteger(0)
        repeat(10) { threadIdx ->
            executor.submit {
                try {
                    repeat(100) { i ->
                        agg.add(info(
                            category = ErrorCategory.K2_COMPILE,
                            file = "/p/A.kt",
                            line = i,
                            msg = "thread$threadIdx line$i",
                            sourceHash = threadIdx * 100 + i,
                        ))
                        totalAdds.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        // 10 thread × 100 line = 1000 unique
        assertEquals(1000, agg.snapshot().size)
        assertEquals(1000L, agg.totalAdds())
    }

    // --- helper ---

    private fun info(
        category: ErrorCategory,
        file: String? = "/p/A.kt",
        line: Int? = 42,
        column: Int? = 7,
        msg: String,
        sourceHash: Int = 0x100,
        severity: ErrorSeverity = ErrorSeverity.ERROR,
    ): PreviewErrorInfo = PreviewErrorInfo(
        category = category,
        severity = severity,
        file = file,
        line = line,
        column = column,
        message = msg,
        sourceHash = sourceHash,
    )
}
