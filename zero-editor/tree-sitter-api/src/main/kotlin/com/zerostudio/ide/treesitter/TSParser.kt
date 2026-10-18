package com.zerostudio.ide.treesitter

import java.io.Closeable
import java.nio.ByteBuffer

/**
 * @author android_zero
 *
 * 变更说明：
 * TSParser 主解析器封装，支持直接 ByteBuffer 零拷贝解析。
 */
class TSParser : Closeable {
    private var ptr: Long = nativeNew()

    var language: TSLanguage? = null
        set(value) {
            field = value
            if (value != null) {
                nativeSetLanguage(ptr, value.ptr)
            }
        }

    fun reset() = nativeReset(ptr)

    fun parseString(oldTree: TSTree?, source: String): TSTree? {
        val res = nativeParseString(ptr, oldTree?.ptr ?: 0L, source)
        return if (res != 0L) TSTree(res) else null
    }

    fun parseDirectBuffer(oldTree: TSTree?, directBuffer: ByteBuffer, length: Int): TSTree? {
        val res = nativeParseDirectBuffer(ptr, oldTree?.ptr ?: 0L, directBuffer, length)
        return if (res != 0L) TSTree(res) else null
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
        @JvmStatic private external fun nativeSetLanguage(pPtr: Long, lPtr: Long): Boolean
        @JvmStatic private external fun nativeReset(pPtr: Long)
        @JvmStatic private external fun nativeParseString(pPtr: Long, otPtr: Long, src: String): Long
        @JvmStatic private external fun nativeParseDirectBuffer(pPtr: Long, otPtr: Long, buf: ByteBuffer, len: Int): Long
    }
}
