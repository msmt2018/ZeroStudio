package com.zerostudio.ide.treesitter

import java.io.Closeable

/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 1. 对接底层 `ts_jni_bridge.cpp`，完全管理 C++ `TSHighlighterEngine` 原生指针。
 * 2. 粗粒度网关设计：仅暴露出高层所需的方法（全量设置文本、增量推送编辑、视口高亮提取）。
 * 3. 继承 Closeable 规范，严格保证 Native 资源的受控释放。
 *
 * 使用教程：
 * ```kotlin
 * val engine = NativeHighlightEngine()
 * engine.setLanguageFromSharedLib("/path/to/libtree-sitter-java.so", "tree_sitter_java")
 * engine.setHighlightQuery("(keyword) @keyword")
 * engine.setText("public class Main {}")
 * val spans = engine.computeViewportHighlight(0, 10)
 * spans.forEachSpan { start, end, capId, _ ->
 *     val name = engine.getCaptureName(capId)
 * }
 * engine.close()
 * ```
 */
class NativeHighlightEngine : Closeable {

    private var nativeEnginePtr: Long = nativeCreate()

    init {
        check(nativeEnginePtr != 0L) { "NativeHighlightEngine 堆初始化失败！" }
    }

    /**
     * 从外部共享库（.so 文件）加载并绑定编程语言。
     */
    fun setLanguageFromSharedLib(soPath: String, constructorName: String): Boolean {
        checkNotClosed()
        return nativeSetLanguageFromSharedLib(nativeEnginePtr, soPath, constructorName)
    }

    /**
     * 编译并设定高亮 S-expression 查询。
     */
    fun setHighlightQuery(queryStr: String): Boolean {
        checkNotClosed()
        val errorInfo = IntArray(2)
        val ok = nativeSetHighlightQuery(nativeEnginePtr, queryStr, errorInfo)
        if (!ok) {
            throw IllegalArgumentException("Tree-sitter Query 语法错误: 字节偏移=${errorInfo[0]}, 错误类型=${errorInfo[1]}")
        }
        return true
    }

    /**
     * 全量设置文本（新建文档或首次打开时使用）。
     */
    fun setText(fullText: String) {
        checkNotClosed()
        nativeSetText(nativeEnginePtr, fullText)
    }

    /**
     * 极速提交增量编辑：C++ 内部即刻原地完成文本更新与 AST 局部重构。
     */
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

    /**
     * 针对视口当前显示的行范围提取高亮标记。
     */
    fun computeViewportHighlight(startLine: Int, endLine: Int): HighlightSpanArray {
        checkNotClosed()
        val raw = nativeComputeViewportHighlight(nativeEnginePtr, startLine, endLine) ?: IntArray(0)
        return HighlightSpanArray(raw)
    }

    /**
     * 根据 captureId 查询捕获角色名称。
     */
    fun getCaptureName(captureId: Int): String? {
        checkNotClosed()
        return nativeGetCaptureName(nativeEnginePtr, captureId)
    }

    /**
     * 探测当前语法树是否包含语法错误。
     */
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

        @JvmStatic
        external fun nativeGetEngineVersion(): String

        @JvmStatic
        private external fun nativeCreate(): Long

        @JvmStatic
        private external fun nativeDestroy(enginePtr: Long)

        @JvmStatic
        private external fun nativeSetLanguageFromSharedLib(
            enginePtr: Long,
            libPath: String,
            funcName: String
        ): Boolean

        @JvmStatic
        private external fun nativeSetHighlightQuery(
            enginePtr: Long,
            queryStr: String,
            outError: IntArray
        ): Boolean

        @JvmStatic
        private external fun nativeSetText(enginePtr: Long, textStr: String)

        @JvmStatic
        private external fun nativeApplyEdit(
            enginePtr: Long,
            startByte: Int, oldEndByte: Int, newEndByte: Int,
            startRow: Int, startCol: Int,
            oldEndRow: Int, oldEndCol: Int,
            newEndRow: Int, newEndCol: Int,
            insertedText: String
        )

        @JvmStatic
        private external fun nativeComputeViewportHighlight(
            enginePtr: Long,
            startLine: Int,
            endLine: Int
        ): IntArray?

        @JvmStatic
        private external fun nativeGetCaptureName(enginePtr: Long, captureId: Int): String?

        @JvmStatic
        private external fun nativeHasSyntaxError(enginePtr: Long): Boolean
    }
}