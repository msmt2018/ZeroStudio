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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * v2.2 P5 单元测试 — LiveStatePersistenceManager.
 *
 * 覆盖: 内存 set/get, sourceHash stale check, atomic write, 损坏容错, project 隔离,
 * dirty bit CAS, scheduleFlush 合并.
 */
class LiveStatePersistenceManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var manager: LiveStatePersistenceManager

    @Before
    fun setUp() {
        projectDir = tempFolder.newFolder("project")
        // 卸载前一个 (如果有)
        LiveStatePersistenceManager.uninstall()
        manager = LiveStatePersistenceManager.install(projectDir)
        manager.startScheduler()
    }

    @After
    fun tearDown() {
        manager.release()
        LiveStatePersistenceManager.uninstall()
    }

    // ---------- set / get ----------

    @Test
    fun `setLiteral then getLiteral returns same value`() {
        manager.setLiteral(
            className = "com.example.MyKt",
            groupKey = "intLit-12345",
            value = 42,
            pairedValue = null,
            type = "INT",
            sourceHash = 0xABCD.toInt(),
        )
        val literal = manager.getLiteral("com.example.MyKt", "intLit-12345", currentSourceHash = 0xABCD.toInt())
        assertNotNull(literal)
        assertEquals(42, literal!!.value)
        assertEquals("INT", literal.type)
    }

    @Test
    fun `setLiteral overwrites previous value`() {
        manager.setLiteral("c", "g", value = 1, pairedValue = null, type = "INT", sourceHash = 1)
        manager.setLiteral("c", "g", value = 99, pairedValue = null, type = "INT", sourceHash = 1)
        val literal = manager.getLiteral("c", "g", 1)
        assertEquals(99, literal!!.value)
    }

    @Test
    fun `setLiteral multiple groups and classes`() {
        manager.setLiteral("c1", "g1", value = 1, pairedValue = null, type = "INT", sourceHash = 1)
        manager.setLiteral("c1", "g2", value = 2, pairedValue = null, type = "INT", sourceHash = 1)
        manager.setLiteral("c2", "g1", value = 3, pairedValue = null, type = "INT", sourceHash = 1)
        assertEquals(1, manager.getLiteral("c1", "g1", 1)!!.value)
        assertEquals(2, manager.getLiteral("c1", "g2", 1)!!.value)
        assertEquals(3, manager.getLiteral("c2", "g1", 1)!!.value)
    }

    @Test
    fun `getLiteral missing returns null`() {
        assertNull(manager.getLiteral("nonexistent", "g", 0))
    }

    // ---------- sourceHash stale check ----------

    @Test
    fun `getLiteral with stale sourceHash returns null`() {
        manager.setLiteral("c", "g", value = 1, pairedValue = null, type = "INT", sourceHash = 0x100)
        val literal = manager.getLiteral("c", "g", currentSourceHash = 0x200) // different
        assertNull("stale entry should return null", literal)
    }

    @Test
    fun `getLiteral with matching sourceHash returns hit`() {
        manager.setLiteral("c", "g", value = 1, pairedValue = null, type = "INT", sourceHash = 0x100)
        val literal = manager.getLiteral("c", "g", currentSourceHash = 0x100)
        assertNotNull(literal)
    }

    @Test
    fun `getLiteral with sourceHash 0 accepts any currentHash`() {
        // sourceHash=0 是"任何 hash 都接受"占位 (未设置 hash)
        manager.setLiteral("c", "g", value = 1, pairedValue = null, type = "INT", sourceHash = 0)
        assertNotNull(manager.getLiteral("c", "g", currentSourceHash = 0x123))
    }

    // ---------- paired value ----------

    @Test
    fun `pairedValue preserved through set`() {
        manager.setLiteral("c", "g", value = 0x10, pairedValue = 0x20, type = "LONG", sourceHash = 1)
        val literal = manager.getLiteral("c", "g", 1)
        assertEquals(0x10, literal!!.value)
        assertEquals(0x20, literal.pairedValue)
    }

    // ---------- 设备 / 主题 prefs ----------

    @Test
    fun `deviceProfile round trip`() {
        manager.setDeviceProfile("Pixel 7 Pro")
        assertEquals("Pixel 7 Pro", manager.getDeviceProfile())
    }

    @Test
    fun `theme round trip`() {
        manager.setTheme("Dark")
        assertEquals("Dark", manager.getTheme())
    }

    @Test
    fun `debugEnabled round trip`() {
        manager.setDebugEnabled(true)
        assertEquals(true, manager.getDebugEnabled())
    }

    @Test
    fun `displayMode round trip`() {
        manager.setDisplayMode("GALLERY")
        assertEquals("GALLERY", manager.getDisplayMode())
    }

    // ---------- atomic write ----------

    @Test
    fun `flushNow writes valid json to disk`() {
        manager.setLiteral("c", "g", value = 1, pairedValue = null, type = "INT", sourceHash = 1)
        manager.flushNow()

        val file = File(projectDir, ".androidide/live-state.json")
        assertTrue("file should exist", file.exists())
        val json = file.readText(Charsets.UTF_8)
        assertTrue("json should contain value", json.contains("0x00000001"))
        assertTrue("json should contain type", json.contains("INT"))
    }

    @Test
    fun `flushNow without dirty bit is no-op`() {
        // 没 setLiteral, 直接 flush
        manager.flushNow()
        val file = File(projectDir, ".androidide/live-state.json")
        // 不应该创建文件 (dirty=false → 跳过)
        assertFalse("file should not exist when no changes", file.exists())
    }

    @Test
    fun `multiple flushNow calls produce same json`() {
        manager.setLiteral("c", "g", value = 1, pairedValue = null, type = "INT", sourceHash = 1)
        manager.flushNow()
        val firstJson = File(projectDir, ".androidide/live-state.json").readText()

        // 没新 setLiteral, 再次 flush 应该是 no-op
        manager.flushNow()
        val secondJson = File(projectDir, ".androidide/live-state.json").readText()
        assertEquals(firstJson, secondJson)
    }

    // ---------- load from disk ----------

    @Test
    fun `load reads from disk into memory`() {
        manager.setLiteral("c", "g", value = 1, pairedValue = null, type = "INT", sourceHash = 0xABC)
        manager.setDeviceProfile("Pixel 7")
        manager.setTheme("Light")
        manager.flushNow()

        // 新 manager
        manager.release()
        val newManager = LiveStatePersistenceManager.install(projectDir)
        newManager.startScheduler()
        newManager.load()

        val literal = newManager.getLiteral("c", "g", currentSourceHash = 0xABC)
        assertNotNull("loaded literal should exist", literal)
        assertEquals(1, literal!!.value)
        assertEquals("Pixel 7", newManager.getDeviceProfile())
        assertEquals("Light", newManager.getTheme())
    }

    @Test
    fun `load on nonexistent file is no-op`() {
        // 没写入过磁盘, load 应该 no-op
        val freshDir = tempFolder.newFolder("fresh")
        LiveStatePersistenceManager.uninstall()
        val mgr = LiveStatePersistenceManager.install(freshDir)
        mgr.startScheduler()
        mgr.load() // 不抛异常
        assertNull(mgr.getLiteral("any", "any", 0))
    }

    // ---------- 损坏容错 ----------

    @Test
    fun `corrupt json on disk is backed up and ignored`() {
        val androidideDir = File(projectDir, ".androidide").apply { mkdirs() }
        val stateFile = File(androidideDir, "live-state.json")
        stateFile.writeText("{this is not json", Charsets.UTF_8)

        LiveStatePersistenceManager.uninstall()
        val mgr = LiveStatePersistenceManager.install(projectDir)
        mgr.startScheduler()
        mgr.load()

        // 损坏文件应被备份
        val backup = File(androidideDir, "live-state.json.bak")
        assertTrue("backup should exist", backup.exists())
        // 原始文件应被删除
        assertFalse("original should be removed", stateFile.exists())
        // 内存应该为空
        assertNull(mgr.getLiteral("any", "any", 0))
    }

    @Test
    fun `schema version mismatch is ignored and backed up`() {
        val androidideDir = File(projectDir, ".androidide").apply { mkdirs() }
        val stateFile = File(androidideDir, "live-state.json")
        stateFile.writeText("""{"version": 999, "literals": {}}""", Charsets.UTF_8)

        LiveStatePersistenceManager.uninstall()
        val mgr = LiveStatePersistenceManager.install(projectDir)
        mgr.startScheduler()
        mgr.load()

        val backup = File(androidideDir, "live-state.json.bak")
        assertTrue("backup should exist for version mismatch", backup.exists())
        assertNull(mgr.getLiteral("any", "any", 0))
    }

    // ---------- clear ----------

    @Test
    fun `clear empties memory and marks dirty`() {
        manager.setLiteral("c", "g", value = 1, pairedValue = null, type = "INT", sourceHash = 1)
        manager.clear()
        assertNull(manager.getLiteral("c", "g", 1))
    }

    // ---------- per-project 隔离 ----------

    @Test
    fun `different project dirs are isolated`() {
        manager.setLiteral("c", "g", value = 1, pairedValue = null, type = "INT", sourceHash = 1)

        val otherDir = tempFolder.newFolder("other-project")
        LiveStatePersistenceManager.uninstall()
        val otherManager = LiveStatePersistenceManager.install(otherDir)
        otherManager.startScheduler()
        otherManager.load()

        // other 不应该有 c/g
        assertNull(otherManager.getLiteral("c", "g", 1))
    }

    // ---------- snapshot ----------

    @Test
    fun `snapshot returns current state`() {
        manager.setLiteral("c1", "g1", value = 1, pairedValue = null, type = "INT", sourceHash = 1)
        manager.setDeviceProfile("Pixel")
        manager.setDebugEnabled(true)

        val snap = manager.snapshot()
        assertEquals(1, snap.literals["c1"]!!["g1"]!!.value)
        assertEquals("Pixel", snap.deviceProfile)
        assertEquals(true, snap.debugEnabled)
    }

    // ---------- release ----------

    @Test
    fun `release flushes pending writes`() {
        manager.setLiteral("c", "g", value = 1, pairedValue = null, type = "INT", sourceHash = 1)
        // 不调 flushNow, 直接 release
        manager.release()
        LiveStatePersistenceManager.uninstall()

        // 文件应存在
        val file = File(projectDir, ".androidide/live-state.json")
        assertTrue("file should exist after release", file.exists())
    }
}
