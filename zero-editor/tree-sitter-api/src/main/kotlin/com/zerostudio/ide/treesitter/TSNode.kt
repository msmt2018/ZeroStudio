package com.zerostudio.ide.treesitter

import java.io.Closeable

/**
 * @author android_zero
 *
 * 变更说明：
 * TSNode 采用数组传递上下文，避免在大量遍历时产生零碎的 JNI 引用。
 */
class TSNode internal constructor(
    internal val context: IntArray,
    internal val ptrs: LongArray
) {
    val type: String? get() = nativeGetType(context, ptrs)
    val symbol: Int get() = nativeGetSymbol(context, ptrs)
    val startByte: Int get() = nativeGetStartByte(context, ptrs)
    val endByte: Int get() = nativeGetEndByte(context, ptrs)
    val isNamed: Boolean get() = nativeIsNamed(context, ptrs)
    val hasError: Boolean get() = nativeHasError(context, ptrs)
    val childCount: Int get() = nativeGetChildCount(context, ptrs)

    val startPoint: TSPoint
        get() {
            val pt = IntArray(2)
            nativeGetStartPoint(context, ptrs, pt)
            return TSPoint(pt[0], pt[1])
        }

    val endPoint: TSPoint
        get() {
            val pt = IntArray(2)
            nativeGetEndPoint(context, ptrs, pt)
            return TSPoint(pt[0], pt[1])
        }

    fun getChild(index: Int): TSNode {
        val outContext = IntArray(4)
        val outPtrs = LongArray(2)
        nativeGetChild(context, ptrs, index, outContext, outPtrs)
        return TSNode(outContext, outPtrs)
    }

    fun getChildByFieldName(name: String): TSNode {
        val outContext = IntArray(4)
        val outPtrs = LongArray(2)
        nativeGetChildByFieldName(context, ptrs, name, outContext, outPtrs)
        return TSNode(outContext, outPtrs)
    }

    override fun toString(): String = nativeToString(context, ptrs) ?: ""

    companion object {
        @JvmStatic private external fun nativeGetType(c: IntArray, p: LongArray): String?
        @JvmStatic private external fun nativeGetSymbol(c: IntArray, p: LongArray): Int
        @JvmStatic private external fun nativeGetStartByte(c: IntArray, p: LongArray): Int
        @JvmStatic private external fun nativeGetEndByte(c: IntArray, p: LongArray): Int
        @JvmStatic private external fun nativeGetStartPoint(c: IntArray, p: LongArray, out: IntArray)
        @JvmStatic private external fun nativeGetEndPoint(c: IntArray, p: LongArray, out: IntArray)
        @JvmStatic private external fun nativeIsNamed(c: IntArray, p: LongArray): Boolean
        @JvmStatic private external fun nativeHasError(c: IntArray, p: LongArray): Boolean
        @JvmStatic private external fun nativeGetChildCount(c: IntArray, p: LongArray): Int
        @JvmStatic private external fun nativeGetChild(c: IntArray, p: LongArray, idx: Int, oc: IntArray, op: LongArray)
        @JvmStatic private external fun nativeGetChildByFieldName(c: IntArray, p: LongArray, name: String, oc: IntArray, op: LongArray)
        @JvmStatic private external fun nativeToString(c: IntArray, p: LongArray): String?
    }
}
