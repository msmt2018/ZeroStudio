package com.zerostudio.ide.treesitter

import java.io.Closeable


/**
 * @author android_zero
 *
 * 变更说明：
 * 管理语法树 TSTree，支持获取变化范围与就地增量修改。
 */
class TSTree internal constructor(var ptr: Long) : Closeable {

    fun getRootNode(): TSNode {
        val context = IntArray(4)
        val ptrs = LongArray(2)
        nativeGetRootNode(ptr, context, ptrs)
        return TSNode(context, ptrs)
    }

    fun edit(edit: TSInputEdit) {
        nativeEdit(
            ptr,
            edit.startByte, edit.oldEndByte, edit.newEndByte,
            edit.startPoint.row, edit.startPoint.column,
            edit.oldEndPoint.row, edit.oldEndPoint.column,
            edit.newEndPoint.row, edit.newEndPoint.column
        )
    }

    fun getChangedRanges(newTree: TSTree): List<TSRange> {
        val raw = nativeGetChangedRanges(ptr, newTree.ptr) ?: return emptyList()
        val list = ArrayList<TSRange>(raw.size / 6)
        for (i in 0 until raw.size step 6) {
            list.add(
                TSRange(
                    startByte = raw[i],
                    endByte = raw[i + 1],
                    startPoint = TSPoint(raw[i + 2], raw[i + 3]),
                    endPoint = TSPoint(raw[i + 4], raw[i + 5])
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
        @JvmStatic private external fun nativeDelete(ptr: Long)
        @JvmStatic private external fun nativeGetRootNode(ptr: Long, c: IntArray, p: LongArray)
        @JvmStatic private external fun nativeEdit(
            ptr: Long,
            sb: Int, oeb: Int, neb: Int,
            sr: Int, sc: Int,
            oer: Int, oec: Int,
            ner: Int, nec: Int
        )
        @JvmStatic private external fun nativeGetChangedRanges(oldPtr: Long, newPtr: Long): IntArray?
    }
}