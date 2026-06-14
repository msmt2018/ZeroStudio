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

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * v2.2 P4 持久化 JSON 编解码.
 *
 * 手写 JSON, 不引依赖 (e.g. kotlinx.serialization).
 */
object LiveStateJsonCodec {

    const val SCHEMA_VERSION = 1

    private val ISO_8601_FORMAT: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun nowIso(): String = ISO_8601_FORMAT.get()!!.format(Date())

    fun encode(snapshot: LiveStateSnapshot): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": ").append(SCHEMA_VERSION).append(",\n")
        sb.append("  \"lastUpdated\": \"").append(escape(snapshot.lastUpdated)).append("\",\n")
        sb.append("  \"literals\": {\n")
        val classEntries = snapshot.literals.entries.toList()
        for ((ci, classEntry) in classEntries.withIndex()) {
            sb.append("    \"").append(escape(classEntry.key)).append("\": {\n")
            val groupEntries = classEntry.value.entries.toList()
            for ((gi, groupEntry) in groupEntries.withIndex()) {
                val (groupKey, literal) = groupEntry
                sb.append("      \"").append(escape(groupKey)).append("\": {\n")
                sb.append("        \"value\": \"").append(formatInt(literal.value)).append("\",\n")
                sb.append("        \"type\": \"").append(escape(literal.type)).append("\",\n")
                sb.append("        \"sourceHash\": \"").append(formatInt(literal.sourceHash)).append("\",\n")
                sb.append("        \"lastModified\": \"").append(escape(literal.lastModified)).append("\"")
                if (literal.pairedValue != null) {
                    sb.append(",\n        \"pairedValue\": \"").append(formatInt(literal.pairedValue)).append("\"")
                }
                sb.append("\n      }")
                if (gi < groupEntries.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("    }")
            if (ci < classEntries.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  },\n")
        sb.append("  \"deviceProfile\": \"").append(escape(snapshot.deviceProfile ?: "")).append("\",\n")
        sb.append("  \"theme\": \"").append(escape(snapshot.theme ?: "")).append("\",\n")
        sb.append("  \"debugEnabled\": ").append(snapshot.debugEnabled).append(",\n")
        sb.append("  \"displayMode\": \"").append(escape(snapshot.displayMode ?: "")).append("\"\n")
        sb.append("}\n")
        return sb.toString()
    }

    fun decode(json: String): LiveStateSnapshot? {
        return try {
            val parser = JsonParser(json)
            val root = parser.parseObject() ?: return null
            val version = root["version"] as? Long ?: return null
            if (version != SCHEMA_VERSION.toLong()) return null
            val lastUpdated = (root["lastUpdated"] as? String).orEmpty()
            val literalsRaw = root["literals"] as? Map<*, *>
            val literals = LinkedHashMap<String, LinkedHashMap<String, PersistedLiteral>>()
            if (literalsRaw != null) {
                for ((className, groupsRaw) in literalsRaw) {
                    val classNameStr = className as? String ?: continue
                    val groupsMap = groupsRaw as? Map<*, *> ?: continue
                    val classLiterals = LinkedHashMap<String, PersistedLiteral>()
                    for ((groupKey, literalRaw) in groupsMap) {
                        val groupKeyStr = groupKey as? String ?: continue
                        val literalMap = literalRaw as? Map<*, *> ?: continue
                        val value = parseInt(literalMap["value"] as? String) ?: continue
                        val type = (literalMap["type"] as? String) ?: continue
                        val sourceHash = parseInt(literalMap["sourceHash"] as? String) ?: 0
                        val lastModified = (literalMap["lastModified"] as? String).orEmpty()
                        val pairedValue = (literalMap["pairedValue"] as? String)?.let { parseInt(it) }
                        classLiterals[groupKeyStr] = PersistedLiteral(
                            value = value,
                            pairedValue = pairedValue,
                            type = type,
                            sourceHash = sourceHash,
                            lastModified = lastModified,
                        )
                    }
                    literals[classNameStr] = classLiterals
                }
            }
            LiveStateSnapshot(
                version = version.toInt(),
                lastUpdated = lastUpdated,
                literals = literals,
                deviceProfile = (root["deviceProfile"] as? String)?.takeIf { it.isNotEmpty() },
                theme = (root["theme"] as? String)?.takeIf { it.isNotEmpty() },
                debugEnabled = (root["debugEnabled"] as? Boolean) ?: false,
                displayMode = (root["displayMode"] as? String)?.takeIf { it.isNotEmpty() },
            )
        } catch (e: Throwable) {
            null
        }
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length + 4)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '/' -> sb.append("\\/")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                else -> if (c.code < 0x20) {
                    sb.append("\\u%04x".format(c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }

    private fun formatInt(v: Int): String = "0x%08X".format(v)

    private fun parseInt(s: String?): Int? {
        if (s == null) return null
        return try {
            val trimmed = s.trim()
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                trimmed.substring(2).toLong(16).toInt()
            } else {
                trimmed.toInt()
            }
        } catch (e: Throwable) {
            null
        }
    }
}

data class PersistedLiteral(
    val value: Int,
    val pairedValue: Int? = null,
    val type: String,
    val sourceHash: Int = 0,
    val lastModified: String = "",
)

data class LiveStateSnapshot(
    val version: Int = LiveStateJsonCodec.SCHEMA_VERSION,
    val lastUpdated: String = "",
    val literals: Map<String, Map<String, PersistedLiteral>> = emptyMap(),
    val deviceProfile: String? = null,
    val theme: String? = null,
    val debugEnabled: Boolean = false,
    val displayMode: String? = null,
)

internal class JsonParser(private val src: String) {
    private var pos = 0

    fun parseObject(): Map<String, Any?>? {
        skipWs()
        if (peek() != '{') return null
        pos++
        val map = LinkedHashMap<String, Any?>()
        skipWs()
        if (peek() == '}') { pos++; return map }
        while (true) {
            skipWs()
            val key = parseString() ?: return null
            skipWs()
            expectColon()
            skipWs()
            val value = parseValue()
            map[key] = value
            skipWs()
            when (val c = peek()) {
                ',' -> { pos++; continue }
                '}' -> { pos++; return map }
                else -> error("Unexpected char '" + c + "' at " + pos)
            }
        }
    }

    private fun parseValue(): Any? {
        skipWs()
        return when (val c = peek()) {
            '"' -> parseString()
            '{' -> parseObject()
            '[' -> parseArray()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            '-', in '0'..'9' -> parseNumber()
            else -> error("Unexpected char '" + c + "' at " + pos)
        }
    }

    private fun parseString(): String? {
        if (peek() != '"') return null
        pos++
        val sb = StringBuilder()
        while (pos < src.length) {
            val c = src[pos++]
            if (c == '"') return sb.toString()
            if (c == '\\') {
                if (pos >= src.length) error("Unterminated escape at " + pos)
                when (val esc = src[pos++]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    'u' -> {
                        if (pos + 4 > src.length) error("Bad unicode escape at " + pos)
                        val hex = src.substring(pos, pos + 4)
                        pos += 4
                        sb.append(hex.toInt(16).toChar())
                    }
                    else -> error("Bad escape \\" + esc + " at " + pos)
                }
            } else {
                sb.append(c)
            }
        }
        error("Unterminated string at " + pos)
    }

    private fun parseArray(): List<Any?>? {
        pos++
        val list = mutableListOf<Any?>()
        skipWs()
        if (peek() == ']') { pos++; return list }
        while (true) {
            skipWs()
            list.add(parseValue())
            skipWs()
            when (val c = peek()) {
                ',' -> { pos++; continue }
                ']' -> { pos++; return list }
                else -> error("Unexpected char '" + c + "' at " + pos)
            }
        }
    }

    private fun parseBoolean(): Boolean {
        if (src.startsWith("true", pos)) { pos += 4; return true }
        if (src.startsWith("false", pos)) { pos += 5; return false }
        error("Expected boolean at " + pos)
    }

    private fun parseNull(): Any? {
        if (src.startsWith("null", pos)) { pos += 4; return null }
        error("Expected null at " + pos)
    }

    private fun parseNumber(): Long {
        val start = pos
        if (peek() == '-') pos++
        while (pos < src.length && src[pos].isDigit()) pos++
        val text = src.substring(start, pos)
        return text.toLong()
    }

    private fun skipWs() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun peek(): Char = if (pos < src.length) src[pos] else ' '

    private fun expectColon() {
        if (peek() != ':') error("Expected ':' at " + pos + ", got '" + peek() + "'")
        pos++
    }
}
