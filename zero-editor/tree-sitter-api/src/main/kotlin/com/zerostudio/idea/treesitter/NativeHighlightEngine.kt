package com.zerostudio.idea.treesitter

import java.io.Closeable

/**
 * @author android_zero
 *
 * 变更说明：
 * 增加了对 TSH 编译出的标准 SCM 查询与多 Capture 样式元数据表的设置。
 */
class NativeHighlightEngine : Closeable {

    private var nativeEnginePtr: Long = nativeCreate()

    init {
        check(nativeEnginePtr != 0L) { "NativeHighlightEngine 堆初始化失败！" }
    }

    fun setLanguageFromSharedLib(soPath: String, constructorName: String): Boolean {
        checkNotClosed()
        return nativeSetLanguageFromSharedLib(nativeEnginePtr, soPath, constructorName)
    }

    /**
     * 设置 SCM 查询（既可以是原始 .scm，也可以是 .tsh 编译出的 standardScmQuery）。
     */
    fun setQuery(queryStr: String): Boolean {
        checkNotClosed()
        val errorInfo = IntArray(2)
        val ok = nativeSetQuery(nativeEnginePtr, queryStr, errorInfo)
        if (!ok) {
            throw IllegalArgumentException("Tree-sitter Query 语法错误: 字节偏移=${errorInfo[0]}, 错误码=${errorInfo[1]}")
        }
        return true
    }

    /**
     * 绑定每个 Capture 对应的样式表 [PackedStyle, Priority, ActionId]
     */
    fun setCaptureStyleTable(styleTable: IntArray, entryCount: Int) {
        checkNotClosed()
        nativeSetCaptureStyleTable(nativeEnginePtr, styleTable, entryCount)
    }

    fun setText(fullText: String) {
        checkNotClosed()
        nativeSetText(nativeEnginePtr, fullText)
    }

    fun applyEdit(event: TextEditEvent) {
        checkNotClosed()
        nativeApplyEdit(
            nativeEnginePtr,
            event.startByte, event.oldEndByte, event.newEndByte,
            event.startRow, event.startColumn,
            event.oldEndRow, event.oldEndColumn,
            event.newEndRow, event.newEndColumn,
            event.insertedText
        )
    }

    fun computeViewportHighlight(startLine: Int, endLine: Int): HighlightSpanArray {
        checkNotClosed()
        val raw = nativeComputeViewportHighlight(nativeEnginePtr, startLine, endLine) ?: IntArray(0)
        return HighlightSpanArray(raw)
    }

    fun getCaptureName(captureId: Int): String? {
        checkNotClosed()
        return nativeGetCaptureName(nativeEnginePtr, captureId)
    }

    fun hasSyntaxError(): Boolean {
        checkNotClosed()
        return nativeHasSyntaxError(nativeEnginePtr)
    }

    private fun checkNotClosed() {
        check(nativeEnginePtr != 0L) { "NativeHighlightEngine 已经被释放，无法调用！" }
    }

    override fun close() {
        if (nativeEnginePtr != 0L) {
            nativeDestroy(nativeEnginePtr)
            nativeEnginePtr = 0L
        }
    }

    companion object {
        init {
            System.loadLibrary("zerostudio_treesitter")
        }

        @JvmStatic external fun nativeGetEngineVersion(): String
        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(enginePtr: Long)
        @JvmStatic private external fun nativeSetLanguageFromSharedLib(enginePtr: Long, libPath: String, funcName: String): Boolean
        @JvmStatic private external fun nativeSetQuery(enginePtr: Long, queryStr: String, outError: IntArray): Boolean
        @JvmStatic private external fun nativeSetCaptureStyleTable(enginePtr: Long, styleTable: IntArray, entryCount: Int)
        @JvmStatic private external fun nativeSetText(enginePtr: Long, textStr: String)
        @JvmStatic private external fun nativeApplyEdit(
            enginePtr: Long,
            startByte: Int, oldEndByte: Int, newEndByte: Int,
            startRow: Int, startCol: Int,
            oldEndRow: Int, oldEndCol: Int,
            newEndRow: Int, newEndCol: Int,
            insertedText: String
        )
        @JvmStatic private external fun nativeComputeViewportHighlight(enginePtr: Long, startLine: Int, endLine: Int): IntArray?
        @JvmStatic private external fun nativeGetCaptureName(enginePtr: Long, captureId: Int): String?
        @JvmStatic private external fun nativeHasSyntaxError(enginePtr: Long): Boolean
    }
}