package com.zerostudio.ide.treesitter

import java.io.Closeable
import java.nio.ByteBuffer

/**
 * @author android_zero
 *
 * 变更说明：
 * TSTreeCursor：极速树遍历器，用于轻量化 AST 下钻。
 */
class TSTreeCursor(node: TSNode) : Closeable {
    private var ptr: Long = nativeNew(node.context, node.ptrs)

    fun gotoFirstChild(): Boolean = nativeGotoFirstChild(ptr)
    fun gotoNextSibling(): Boolean = nativeGotoNextSibling(ptr)
    fun gotoParent(): Boolean = nativeGotoParent(ptr)

    fun getCurrentNode(): TSNode {
        val outContext = IntArray(4)
        val outPtrs = LongArray(2)
        nativeCurrentNode(ptr, outContext, outPtrs)
        return TSNode(outContext, outPtrs)
    }

    override fun close() {
        if (ptr != 0L) {
            nativeDelete(ptr)
            ptr = 0L
        }
    }

    companion object {
        @JvmStatic private external fun nativeNew(c: IntArray, p: LongArray): Long
        @JvmStatic private external fun nativeDelete(ptr: Long)
        @JvmStatic private external fun nativeGotoFirstChild(ptr: Long): Boolean
        @JvmStatic private external fun nativeGotoNextSibling(ptr: Long): Boolean
        @JvmStatic private external fun nativeGotoParent(ptr: Long): Boolean
        @JvmStatic private external fun nativeCurrentNode(ptr: Long, oc: IntArray, op: LongArray)
    }
}