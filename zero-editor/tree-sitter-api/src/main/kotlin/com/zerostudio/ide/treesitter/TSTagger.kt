package com.zerostudio.ide.treesitter

import java.io.Closeable

/**
 * @author android_zero
 *
 * 变更说明：
 * 适配 `tags/tags.h`：提取代码符号大纲与定义（用于 LSP DocumentSymbol、Go to Definition）。
 *
 * 流程脑图：
 * Source Code + tags.scm ──> TSTagger.tag() ──> List<CodeSymbolTag>
 */
class TSTagger : Closeable {
    private var ptr: Long = nativeNew()

    fun addLanguage(
        scopeName: String,
        language: TSLanguage,
        tagsQuery: String,
        localsQuery: String? = null
    ) {
        val err = nativeAddLanguage(ptr, scopeName, language.ptr, tagsQuery, localsQuery)
        if (err != 0) {
            throw IllegalArgumentException("Failed to add language to tagger: code=$err")
        }
    }

    fun tag(scopeName: String, sourceCode: String): List<CodeSymbolTag> {
        val raw = nativeTag(ptr, scopeName, sourceCode) ?: return emptyList()
        val list = ArrayList<CodeSymbolTag>(raw.size / 10)
        for (i in 0 until raw.size step 10) {
            list.add(
                CodeSymbolTag(
                    startByte = raw[i],
                    endByte = raw[i + 1],
                    nameStartByte = raw[i + 2],
                    nameEndByte = raw[i + 3],
                    lineStartByte = raw[i + 4],
                    lineEndByte = raw[i + 5],
                    startPoint = TSPoint(raw[i + 6], raw[i + 7]),
                    syntaxTypeId = raw[i + 8],
                    isDefinition = raw[i + 9] == 1
                )
            )
        }
        return list
    }

    override fun close() {
        if (ptr != 0L) {
            nativeDelete(ptr)
            ptr = 0L
        }
    }

    companion object {
        @JvmStatic private external fun nativeNew(): Long
        @JvmStatic private external fun nativeDelete(ptr: Long)
        @JvmStatic private external fun nativeAddLanguage(
            ptr: Long, scope: String, lPtr: Long, tq: String, lq: String?
        ): Int
        @JvmStatic private external fun nativeTag(ptr: Long, scope: String, src: String): IntArray?
    }
}