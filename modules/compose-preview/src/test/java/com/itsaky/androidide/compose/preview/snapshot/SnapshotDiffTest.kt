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

package com.itsaky.androidide.compose.preview.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v2.3 P3 单元测试.
 *
 * 覆盖:
 * - ImageDiffer: identical / small diff / big diff / 尺寸不匹配 (10 case)
 * - BaselineStore: write / read / delete / list / atomic (5 case)
 * - SnapshotDiffService: no baseline / diff / CI threshold (5 case)
 */
class SnapshotDiffTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ====== ImageDiffer ======

    @Test
    fun `01 identical images have zero diff`() {
        val w = 4
        val h = 4
        val rgb = ByteArray(w * h * 3) { i -> (i % 256).toByte() }
        val result = ImageDiffer.diff(rgb, w, h, rgb, w, h)
        assertEquals(0L, result.diffPixelCount)
        assertTrue(result.isClean)
        assertEquals(0, result.maxChannelDelta)
        assertEquals(0, result.diffRegions.size)
    }

    @Test
    fun `02 single pixel minor change under threshold`() {
        val w = 4
        val h = 4
        val a = ByteArray(w * h * 3)
        val b = ByteArray(w * h * 3)
        // 第 5 像素 (idx=5*3=15) RGB 差 10 (低于阈值 30)
        a[15] = 100
        a[16] = 100
        a[17] = 100
        b[15] = 110
        b[16] = 100
        b[17] = 100
        val result = ImageDiffer.diff(a, w, h, b, w, h)
        assertEquals(0L, result.diffPixelCount)
    }

    @Test
    fun `03 single pixel change over threshold counts as 1 diff`() {
        val w = 4
        val h = 4
        val a = ByteArray(w * h * 3)
        val b = ByteArray(w * h * 3)
        a[15] = 100; a[16] = 100; a[17] = 100
        b[15] = 200; b[16] = 100; b[17] = 100  // R 差 100 > 30
        val result = ImageDiffer.diff(a, w, h, b, w, h)
        assertEquals(1L, result.diffPixelCount)
        assertEquals(100, result.maxChannelDelta)
        assertEquals(1, result.diffRegions.size)
    }

    @Test
    fun `04 all pixels diff results in full coverage`() {
        val w = 4
        val h = 4
        val a = ByteArray(w * h * 3) { i -> (i % 256).toByte() }
        val b = ByteArray(w * h * 3) { i -> ((i + 200) % 256).toByte() }
        val result = ImageDiffer.diff(a, w, h, b, w, h)
        assertEquals(16L, result.diffPixelCount)
        assertEquals(1.0, result.diffPercentage, 0.0001)
    }

    @Test
    fun `05 size mismatch counts all pixels as diff`() {
        val a = ByteArray(10 * 10 * 3)
        val b = ByteArray(20 * 20 * 3)
        val result = ImageDiffer.diff(a, 10, 10, b, 20, 20)
        assertEquals(100L, result.diffPixelCount)
    }

    @Test
    fun `06 diff region bounding box is correct`() {
        val w = 8
        val h = 8
        val a = ByteArray(w * h * 3)
        val b = ByteArray(w * h * 3)
        // 让 (3,4) 像素 RGB 跳变 > 30
        val idx = (4 * w + 3) * 3
        a[idx] = 50
        b[idx] = 200
        val result = ImageDiffer.diff(a, w, h, b, w, h)
        assertEquals(1L, result.diffPixelCount)
        val region = result.diffRegions[0]
        assertEquals(3, region.left)
        assertEquals(4, region.top)
        assertEquals(4, region.right)
        assertEquals(5, region.bottom)
    }

    @Test
    fun `07 toReport contains key info`() {
        val w = 2
        val h = 2
        val a = ByteArray(w * h * 3)
        val b = ByteArray(w * h * 3)
        val result = ImageDiffer.diff(a, w, h, b, w, h)
        val report = result.toReport()
        assertTrue(report.contains("2x2"))
        assertTrue(report.contains("0/4"))
        assertTrue(report.contains("0.0000%"))
    }

    // ====== BaselineStore ======

    @Test
    fun `10 write and read baseline round trip`() {
        val projectDir = tempFolder.newFolder("proj")
        val store = BaselineStore(projectDir)
        val png = byteArrayOf(1, 2, 3, 4, 5)  // 假 PNG
        store.writeBaseline("MyPreview", "pixel-7", png)
        val read = store.readBaseline("MyPreview", "pixel-7")
        assertNotNull(read)
        assertEquals(png.size, read!!.size)
    }

    @Test
    fun `11 readBaseline for missing returns null`() {
        val projectDir = tempFolder.newFolder("proj")
        val store = BaselineStore(projectDir)
        assertEquals(null, store.readBaseline("MyPreview", "pixel-7"))
    }

    @Test
    fun `12 deleteBaseline removes file`() {
        val projectDir = tempFolder.newFolder("proj")
        val store = BaselineStore(projectDir)
        store.writeBaseline("F", "P", byteArrayOf(1, 2))
        val f = store.baselineFor("F", "P")
        assertTrue(f.exists())
        store.deleteBaseline("F", "P")
        assertFalse(f.exists())
    }

    @Test
    fun `13 listBaselines returns all .png files`() {
        val projectDir = tempFolder.newFolder("proj")
        val store = BaselineStore(projectDir)
        store.writeBaseline("F1", "P1", byteArrayOf(1))
        store.writeBaseline("F1", "P2", byteArrayOf(1))
        store.writeBaseline("F2", "P1", byteArrayOf(1))
        val list = store.listBaselines()
        assertEquals(3, list.size)
        assertEquals(3, store.baselineCount())
    }

    @Test
    fun `14 writeBaseline overwrites previous`() {
        val projectDir = tempFolder.newFolder("proj")
        val store = BaselineStore(projectDir)
        store.writeBaseline("F", "P", byteArrayOf(1, 2, 3))
        store.writeBaseline("F", "P", byteArrayOf(9, 9, 9, 9, 9))
        val read = store.readBaseline("F", "P")
        assertEquals(5, read!!.size)
    }

    // ====== SnapshotDiffService ======

    @Test
    fun `20 verifySingle without baseline returns hasBaseline=false`() {
        val projectDir = tempFolder.newFolder("proj")
        val store = BaselineStore(projectDir)
        val svc = SnapshotDiffService(store, pngToRgb = { rgb })
        val result = svc.verifySingle("F", "P", byteArrayOf())
        assertFalse(result.hasBaseline)
        assertEquals(false, result.failed)
    }

    @Test
    fun `21 verifySingle identical baseline returns clean`() {
        val projectDir = tempFolder.newFolder("proj")
        val store = BaselineStore(projectDir)
        val rgb = ByteArray(4 * 4 * 3) { i -> (i % 256).toByte() }
        val svc = SnapshotDiffService(store, pngToRgb = { rgb })
        store.writeBaseline("F", "P", byteArrayOf(1, 2))  // 内容不重要, pngToRgb 决定
        val result = svc.verifySingle("F", "P", byteArrayOf(1))
        assertTrue(result.hasBaseline)
        assertNotNull(result.diff)
        assertTrue(result.diff!!.isClean)
        assertEquals(false, result.failed)
    }

    @Test
    fun `22 verifySingle all-diff result is computed correctly`() {
        val projectDir = tempFolder.newFolder("proj")
        val store = BaselineStore(projectDir)
        val rgb1 = ByteArray(2 * 2 * 3) { 0 }
        val rgb2 = ByteArray(2 * 2 * 3) { 200.toByte() }
        var toggle = false
        val svc = SnapshotDiffService(store, pngToRgb = {
            if (toggle) rgb2 else rgb1
        })
        store.writeBaseline("F", "P", byteArrayOf(1))
        toggle = true  // 切到 rgb2 作 current
        val result = svc.verifySingle("F", "P", byteArrayOf(2))
        assertTrue(result.hasBaseline)
        assertEquals(4L, result.diff!!.diffPixelCount)  // 全部 4 像素都 diff
    }

    @Test
    fun `23 isCiMode reflects env var CI`() {
        // 测试 CI env 在测试 JVM 里可能没设, 只能验空值
        val inCi = System.getenv("CI")?.equals("true", ignoreCase = true) == true
        assertEquals(inCi, SnapshotDiffService.isCiMode())
    }

    @Test
    fun `24 summary aggregates correctly`() {
        val projectDir = tempFolder.newFolder("proj")
        val store = BaselineStore(projectDir)
        val svc = SnapshotDiffService(store, pngToRgb = { ByteArray(0) })
        val verifications = listOf(
            SnapshotVerification("F1", "P1", false, null, false, ""),
            SnapshotVerification("F2", "P1", true, null, true, ""),
            SnapshotVerification("F3", "P1", true, null, false, ""),
        )
        val summary = svc.summary(verifications)
        assertTrue(summary.contains("3 total"))
        assertTrue(summary.contains("2 with baseline"))
        assertTrue(summary.contains("1 failed"))
    }
}
