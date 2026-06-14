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

package com.itsaky.androidide.compose.preview.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * v2.5 P0: RemoteProfileRepository 单元测试.
 */
class RemoteProfileRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `parseJsonList extracts valid profiles`() {
        val repo = RemoteProfileRepository(tmp.newFolder("cache"))
        val json = """
            [
              {"id":"d1","displayName":"D1","formFactor":"PHONE","widthPx":1080,"heightPx":2400,"densityDpi":440},
              {"id":"d2","displayName":"D2","formFactor":"TABLET","widthPx":1600,"heightPx":2560,"densityDpi":320}
            ]
        """.trimIndent()
        val list = repo.parseJsonList(json)
        assertEquals(2, list.size)
        assertEquals("d1", list[0].id)
        assertEquals(1080, list[0].widthPx)
        assertEquals(440, list[0].densityDpi)
    }

    @Test
    fun `parseJsonList returns empty for invalid json`() {
        val repo = RemoteProfileRepository(tmp.newFolder("cache"))
        assertEquals(0, repo.parseJsonList("not json").size)
    }

    @Test
    fun `parseJsonList uses defaults for missing fields`() {
        val repo = RemoteProfileRepository(tmp.newFolder("cache"))
        val json = """[{"id":"d1","displayName":"D1","formFactor":"PHONE"}]"""
        val list = repo.parseJsonList(json)
        assertEquals(1, list.size)
        assertEquals(1080, list[0].widthPx)  // default
        assertEquals(1920, list[0].heightPx) // default
        assertEquals(420, list[0].densityDpi) // default
    }

    @Test
    fun `fetchFromRemote persists and updates cache`() {
        val cacheDir = tmp.newFolder("cache")
        val repo = RemoteProfileRepository(
            cacheDir = cacheDir,
            fetcher = RemoteProfileRepository.ProfileFetcher { _, _ ->
                """[{"id":"a","displayName":"A","formFactor":"PHONE","widthPx":1080,"heightPx":1920,"densityDpi":420}]"""
            },
        )
        val n = repo.fetchFromRemote("http://example.com/profiles.json")
        assertEquals(1, n)
        assertEquals(1, repo.remoteProfiles().size)
        assertNotNull(repo.cacheFile())
        assertTrue(repo.cacheFile().exists())
        assertTrue(repo.lastSyncTimestamp() > 0L)
    }

    @Test
    fun `fetchFromRemote failure keeps previous cache`() {
        val cacheDir = tmp.newFolder("cache")
        val repo = RemoteProfileRepository(
            cacheDir = cacheDir,
            fetcher = RemoteProfileRepository.ProfileFetcher { _, _ ->
                throw java.io.IOException("network down")
            },
        )
        // 首次失败, 无缓存
        assertEquals(0, repo.fetchFromRemote("http://example.com/profiles.json"))
        // 模拟手动 put 一些数据后, 再次失败
        repo.clearRemote()
        assertEquals(0, repo.fetchFromRemote("http://example.com/profiles.json"))
    }

    @Test
    fun `getMerged prefers remote over local on same id`() {
        val cacheDir = tmp.newFolder("cache")
        val repo = RemoteProfileRepository(
            cacheDir = cacheDir,
            fetcher = RemoteProfileRepository.ProfileFetcher { _, _ ->
                """[{"id":"d1","displayName":"Remote D1","formFactor":"PHONE","widthPx":1111,"heightPx":2222,"densityDpi":555}]"""
            },
        )
        repo.fetchFromRemote("http://x")
        val local = listOf(
            mkProfile("d1", "Local D1", 1080, 1920, 420),
            mkProfile("d2", "Local D2", 1600, 2560, 320),
        )
        val merged = repo.getMerged(local)
        val byId = merged.associateBy { it.id }
        assertEquals(2, merged.size)
        assertEquals("Remote D1", byId["d1"]!!.displayName)
        assertEquals(1111, byId["d1"]!!.widthPx)
        assertEquals("Local D2", byId["d2"]!!.displayName)
    }

    @Test
    fun `getMerged returns local unchanged when remote empty`() {
        val cacheDir = tmp.newFolder("cache")
        val repo = RemoteProfileRepository(cacheDir = cacheDir)
        val local = listOf(mkProfile("d1", "L1", 1080, 1920, 420))
        val merged = repo.getMerged(local)
        assertEquals(local, merged)
    }

    @Test
    fun `cache file name is configurable`() {
        val cacheDir = tmp.newFolder("cache")
        val repo = RemoteProfileRepository(cacheDir, cacheFileName = "alt.json")
        assertEquals("alt.json", repo.cacheFile().name)
    }

    private fun mkProfile(id: String, name: String, w: Int, h: Int, dpi: Int) =
        com.itsaky.androidide.compose.preview.ui.DeviceProfile(
            id = id,
            displayName = name,
            widthPx = w,
            heightPx = h,
            densityDpi = dpi,
        )
}
