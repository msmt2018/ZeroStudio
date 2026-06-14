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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.2 P5 单元测试 — JSON Codec 完整性.
 *
 * 覆盖: encode→decode round-trip, schema 校验, 损坏输入, 各种 type 字段, 特殊字符.
 */
class LiveStateJsonCodecTest {

    // ---------- round-trip ----------

    @Test
    fun `round trip empty snapshot`() {
        val snap = LiveStateSnapshot(lastUpdated = "2026-06-14T10:00:00Z")
        val json = LiveStateJsonCodec.encode(snap)
        val decoded = LiveStateJsonCodec.decode(json)
        assertNotNull(decoded)
        assertEquals(snap.lastUpdated, decoded!!.lastUpdated)
        assertEquals(snap.literals.size, decoded.literals.size)
    }

    @Test
    fun `round trip single int literal`() {
        val snap = LiveStateSnapshot(
            lastUpdated = "2026-06-14T10:00:00Z",
            literals = mapOf(
                "com.example.MyKt" to mapOf(
                    "intLit-12345" to PersistedLiteral(
                        value = 42,
                        type = "INT",
                        sourceHash = 0xABCD1234.toInt(),
                        lastModified = "2026-06-14T10:00:00Z",
                    ),
                ),
            ),
        )
        val decoded = LiveStateJsonCodec.decode(LiveStateJsonCodec.encode(snap))
        assertNotNull(decoded)
        val literal = decoded!!.literals["com.example.MyKt"]!!["intLit-12345"]
        assertNotNull(literal)
        assertEquals(42, literal!!.value)
        assertEquals("INT", literal.type)
        assertEquals(0xABCD1234.toInt(), literal.sourceHash)
    }

    @Test
    fun `round trip paired long literal`() {
        val snap = LiveStateSnapshot(
            literals = mapOf(
                "com.example.MyKt" to mapOf(
                    "longLit-99" to PersistedLiteral(
                        value = 0x12345678,
                        pairedValue = 0x9ABCDEF0.toInt(),
                        type = "LONG",
                        sourceHash = 0x11223344,
                    ),
                ),
            ),
        )
        val decoded = LiveStateJsonCodec.decode(LiveStateJsonCodec.encode(snap))!!
        val literal = decoded.literals["com.example.MyKt"]!!["longLit-99"]!!
        assertEquals(0x12345678, literal.value)
        assertEquals(0x9ABCDEF0.toInt(), literal.pairedValue)
        assertEquals("LONG", literal.type)
    }

    @Test
    fun `round trip all types`() {
        val types = listOf("INT", "LONG", "FLOAT", "BOOLEAN", "DP", "SP", "COLOR")
        val literals = types.mapIndexed { i, t ->
            "g-$i" to PersistedLiteral(value = i, type = t, sourceHash = i * 100)
        }.toMap()
        val snap = LiveStateSnapshot(
            literals = mapOf("com.x.Kt" to literals),
        )
        val decoded = LiveStateJsonCodec.decode(LiveStateJsonCodec.encode(snap))!!
        val out = decoded.literals["com.x.Kt"]!!
        for ((i, t) in types.withIndex()) {
            val literal = out["g-$i"]!!
            assertEquals(t, literal.type)
            assertEquals(i, literal.value)
        }
    }

    @Test
    fun `round trip preferences`() {
        val snap = LiveStateSnapshot(
            deviceProfile = "Pixel 7 Pro",
            theme = "Dark",
            debugEnabled = true,
            displayMode = "GALLERY",
        )
        val decoded = LiveStateJsonCodec.decode(LiveStateJsonCodec.encode(snap))!!
        assertEquals("Pixel 7 Pro", decoded.deviceProfile)
        assertEquals("Dark", decoded.theme)
        assertEquals(true, decoded.debugEnabled)
        assertEquals("GALLERY", decoded.displayMode)
    }

    @Test
    fun `round trip null preferences`() {
        val snap = LiveStateSnapshot(
            deviceProfile = null,
            theme = null,
            debugEnabled = false,
            displayMode = null,
        )
        val decoded = LiveStateJsonCodec.decode(LiveStateJsonCodec.encode(snap))!!
        assertNull(decoded.deviceProfile)
        assertNull(decoded.theme)
        assertEquals(false, decoded.debugEnabled)
        assertNull(decoded.displayMode)
    }

    // ---------- 损坏 / 不匹配 schema ----------

    @Test
    fun `decode invalid version returns null`() {
        val json = """{"version": 999, "literals": {}}"""
        assertNull(LiveStateJsonCodec.decode(json))
    }

    @Test
    fun `decode missing version returns null`() {
        val json = """{"literals": {}}"""
        assertNull(LiveStateJsonCodec.decode(json))
    }

    @Test
    fun `decode malformed json returns null`() {
        val json = """{not even close to json"""
        assertNull(LiveStateJsonCodec.decode(json))
    }

    @Test
    fun `decode empty string returns null`() {
        assertNull(LiveStateJsonCodec.decode(""))
    }

    @Test
    fun `decode literal missing required field skips entry`() {
        // value 字段缺失 → 跳过该 literal
        val json = """
            {
              "version": 1,
              "literals": {
                "com.x.Kt": {
                  "g1": { "type": "INT", "sourceHash": "0x00000001" }
                }
              }
            }
        """.trimIndent()
        val decoded = LiveStateJsonCodec.decode(json)
        assertNotNull(decoded)
        val literals = decoded!!.literals["com.x.Kt"]
        assertTrue(literals == null || literals.isEmpty())
    }

    // ---------- 数字格式 ----------

    @Test
    fun `format int always 8 hex digits`() {
        // 已知: formatInt 用 "0x%08X".format(v)
        val snap = LiveStateSnapshot(
            literals = mapOf("c" to mapOf("g" to PersistedLiteral(value = 1, type = "INT", sourceHash = 0))),
        )
        val json = LiveStateJsonCodec.encode(snap)
        // 1 编码为 0x00000001 (8 hex digits)
        assertTrue("Expected 0x00000001 in: $json", json.contains("0x00000001"))
    }

    @Test
    fun `parse int handles hex and decimal`() {
        val hexJson = """{"version": 1, "literals": {"c": {"g": {"value": "0x0000002A", "type": "INT", "sourceHash": "0x00000000"}}}}"""
        val decJson = """{"version": 1, "literals": {"c": {"g": {"value": "42", "type": "INT", "sourceHash": "0x00000000"}}}}"""
        assertEquals(42, LiveStateJsonCodec.decode(hexJson)!!.literals["c"]!!["g"]!!.value)
        assertEquals(42, LiveStateJsonCodec.decode(decJson)!!.literals["c"]!!["g"]!!.value)
    }

    // ---------- schema 版本 ----------

    @Test
    fun `schema version is 1`() {
        assertEquals(1, LiveStateJsonCodec.SCHEMA_VERSION)
    }

    // ---------- 特殊字符 ----------

    @Test
    fun `escape special chars in string fields`() {
        val snap = LiveStateSnapshot(
            deviceProfile = "Pixel \"7\"\nNew",
        )
        val json = LiveStateJsonCodec.encode(snap)
        // 反斜杠 + 引号应被转义
        assertTrue("expected escaped quote: $json", json.contains("\\\""))
        assertTrue("expected escaped newline: $json", json.contains("\\n"))
        val decoded = LiveStateJsonCodec.decode(json)
        assertEquals("Pixel \"7\"\nNew", decoded!!.deviceProfile)
    }

    // ---------- nowIso ----------

    @Test
    fun `nowIso returns ISO 8601 UTC`() {
        val ts = LiveStateJsonCodec.nowIso()
        // 2026-06-14T10:00:00Z 格式
        assertTrue("format: $ts", ts.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")))
    }
}
