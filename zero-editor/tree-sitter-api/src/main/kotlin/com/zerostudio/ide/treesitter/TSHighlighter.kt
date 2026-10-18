package com.zerostudio.ide.treesitter

import java.io.Closeable

/**
 * @author android_zero
 *
 * 变更说明：
 * 适配 `highlight/highlight.h`：官方语法着色管线，可直接生成带 CSS 类名的 HTML 代码段。
 */
class TSHighlighter(
    highlightNames: Array<String>,
    attributeStrings: Array<String>
) : Closeable {
    private var ptr: Long = nativeNew(highlightNames, attributeStrings, highlightNames.size)

    fun addLanguage(
        languageName: String,
        scopeName: String,
        injectionRegex: String?,
        language: TSLanguage,
        highlightQuery: String,
        injectionQuery: String? = null,
        localsQuery: String? = null
    ) {
        val err = nativeAddLanguage(
            ptr, languageName, scopeName, injectionRegex, language.ptr,
            highlightQuery, injectionQuery, localsQuery
        )
        if (err != 0) {
            throw IllegalArgumentException("Failed to add language to highlighter: code=$err")
        }
    }

    fun highlightToHtml(scopeName: String, sourceCode: String): ByteArray? {
        return nativeHighlightToHtml(ptr, scopeName, sourceCode)
    }

    override fun close() {
        if (ptr != 0L) {
            nativeDelete(ptr)
            ptr = 0L
        }
    }

    companion object {
        @JvmStatic private external fun nativeNew(names: Array<String>, attrs: Array<String>, count: Int): Long
        @JvmStatic private external fun nativeDelete(ptr: Long)
        @JvmStatic private external fun nativeAddLanguage(
            ptr: Long, lName: String, sName: String, injRegex: String?,
            lPtr: Long, hlQ: String, injQ: String?, locQ: String?
        ): Int
        @JvmStatic private external fun nativeHighlightToHtml(ptr: Long, scope: String, src: String): ByteArray?
    }
}