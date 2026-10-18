package com.zerostudio.ide.treesitter

import java.io.Closeable

/**
 * @author android_zero
 *
 * 变更说明：
 * TSQueryCursor：支持视口范围剪裁（PointRange），仅执行可见区域的高亮模式匹配。
 */
class TSQueryCursor : Closeable {
    private var ptr: Long = nativeNew()

    data class CaptureMatch(
        val matchId: Int,
        val patternIndex: Int,
        val captureIndex: Int,
        val node: TSNode
    )

    fun exec(query: TSQuery, node: TSNode) = nativeExec(ptr, query.ptr, node.context, node.ptrs)

    fun setPointRange(startRow: Int, startCol: Int, endRow: Int, endCol: Int) {
        nativeSetPointRange(ptr, startRow, startCol, endRow, endCol)
    }

    fun nextCapture(): CaptureMatch? {
        val meta = IntArray(3)
        val nodeContext = IntArray(4)
        val nodePtrs = LongArray(2)
        if (nativeNextCapture(ptr, meta, nodeContext, nodePtrs)) {
            return CaptureMatch(meta[0], meta[1], meta[2], TSNode(nodeContext, nodePtrs))
        }
        return null
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
        @JvmStatic private external fun nativeExec(ptr: Long, qPtr: Long, c: IntArray, p: LongArray)
        @JvmStatic private external fun nativeSetPointRange(ptr: Long, sr: Int, sc: Int, er: Int, ec: Int)
        @JvmStatic private external fun nativeNextCapture(ptr: Long, meta: IntArray, nc: IntArray, np: LongArray): Boolean
    }
}

/**
 * @author android_zero
 *
 * 变更说明：
 * TSLookaheadIterator：在语法错误或特定状态下探寻有效语法符号，是实现 LSP 智能代码自动补全的核心探针。
 */
class TSLookaheadIterator(language: TSLanguage, stateId: Int) : Closeable {
    private var ptr: Long = nativeNew(language.ptr, stateId)

    init {
        if (ptr == 0L) {
            throw IllegalArgumentException("Invalid stateId: $stateId for language.")
        }
    }

    fun next(): Boolean = nativeNext(ptr)
    val currentSymbol: Int get() = nativeCurrentSymbol(ptr)
    val currentSymbolName: String? get() = nativeCurrentSymbolName(ptr)

    override fun close() {
        if (ptr != 0L) {
            nativeDelete(ptr)
            ptr = 0L
        }
    }

    companion object {
        @JvmStatic private external fun nativeNew(langPtr: Long, state: Int): Long
        @JvmStatic private external fun nativeDelete(ptr: Long)
        @JvmStatic private external fun nativeNext(ptr: Long): Boolean
        @JvmStatic private external fun nativeCurrentSymbol(ptr: Long): Int
        @JvmStatic private external fun nativeCurrentSymbolName(ptr: Long): String?
    }
}