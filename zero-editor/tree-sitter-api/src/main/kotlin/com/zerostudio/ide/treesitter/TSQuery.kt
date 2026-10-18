package com.zerostudio.ide.treesitter

import java.io.Closeable

/**
 * @author android_zero
 *
 * 变更说明：
 * 封装 S-expression 查询结构。
 */
class TSQuery(language: TSLanguage, source: String) : Closeable {
    internal var ptr: Long

    init {
        val errData = IntArray(2)
        ptr = nativeNew(language.ptr, source, errData)
        if (ptr == 0L) {
            throw IllegalArgumentException("Query error: offset=${errData[0]}, type=${errData[1]}")
        }
    }

    fun getCaptureNameForId(id: Int): String? = nativeGetCaptureNameForId(ptr, id)
    val captureCount: Int get() = nativeGetCaptureCount(ptr)

    override fun close() {
        if (ptr != 0L) {
            nativeDelete(ptr)
            ptr = 0L
        }
    }

    companion object {
        @JvmStatic private external fun nativeNew(langPtr: Long, src: String, outErr: IntArray): Long
        @JvmStatic private external fun nativeDelete(ptr: Long)
        @JvmStatic private external fun nativeGetCaptureNameForId(ptr: Long, index: Int): String?
        @JvmStatic private external fun nativeGetCaptureCount(ptr: Long): Int
    }
}
