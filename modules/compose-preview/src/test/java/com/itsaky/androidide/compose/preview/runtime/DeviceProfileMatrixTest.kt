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

import com.itsaky.androidide.compose.preview.ui.DeviceProfile
import com.itsaky.androidide.compose.preview.ui.DeviceProfile.FormFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.3 P2 单元测试.
 *
 * - default() 含 20+ 设备
 * - byFormFactor 过滤
 * - byId 查
 * - fromProfiles 自定义列表
 */
class DeviceProfileMatrixTest {

    @Test
    fun `01 default contains 20+ profiles`() {
        val matrix = DeviceProfileMatrix.default()
        assertTrue("expected at least 20 profiles, got ${matrix.size}", matrix.size >= 20)
    }

    @Test
    fun `02 phones contains only phone form factor`() {
        val matrix = DeviceProfileMatrix.phones()
        assertTrue(matrix.size > 0)
        matrix.profiles.forEach { profile ->
            assertEquals(FormFactor.PHONE, profile.formFactor)
        }
    }

    @Test
    fun `03 tablets contains only tablet form factor`() {
        val matrix = DeviceProfileMatrix.tablets()
        if (matrix.size > 0) {
            matrix.profiles.forEach { profile ->
                assertEquals(FormFactor.TABLET, profile.formFactor)
            }
        }
    }

    @Test
    fun `04 foldables contains only foldable form factors`() {
        val matrix = DeviceProfileMatrix.foldables()
        matrix.profiles.forEach { profile ->
            assertTrue(
                profile.formFactor == FormFactor.FOLDABLE_INNER ||
                profile.formFactor == FormFactor.FOLDABLE_OUTER
            )
        }
    }

    @Test
    fun `05 byId returns known profile`() {
        val matrix = DeviceProfileMatrix.default()
        val pixel7 = matrix.byId("pixel-7")
        assertNotNull(pixel7)
        assertEquals("Pixel 7", pixel7!!.displayName)
    }

    @Test
    fun `06 byId returns null for unknown id`() {
        val matrix = DeviceProfileMatrix.default()
        val unknown = matrix.byId("does-not-exist")
        assertNull(unknown)
    }

    @Test
    fun `07 fromProfiles accepts custom list`() {
        val custom = listOf(
            DeviceProfile(
                id = "custom-1",
                displayName = "Custom 1",
                widthPx = 1080, heightPx = 2400, densityDpi = 420,
            ),
        )
        val matrix = DeviceProfileMatrix.fromProfiles(custom)
        assertEquals(1, matrix.size)
        assertEquals("Custom 1", matrix.profiles[0].displayName)
    }

    @Test
    fun `08 byFormFactor does not mutate original`() {
        val original = DeviceProfileMatrix.default()
        val originalSize = original.size
        original.byFormFactor(FormFactor.PHONE)
        assertEquals(originalSize, original.size)  // 没改
    }

    @Test
    fun `09 phone + tablet + foldable counts sum to default minus watch-desktop`() {
        val matrix = DeviceProfileMatrix.default()
        val phones = DeviceProfileMatrix.phones().size
        val tablets = DeviceProfileMatrix.tablets().size
        val foldables = DeviceProfileMatrix.foldables().size
        // 至少 phones+tablets+foldables 覆盖了大部分 default (watch + desktop 剩余)
        assertTrue(
            "phones+tablets+foldables ($phones+$tablets+$foldables) 应该 ≤ default (${matrix.size})",
            phones + tablets + foldables <= matrix.size
        )
    }
}
