package com.zerostudio.ide.treesitter

/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 1. 增量编辑事件载体，可直接对接编辑器 UI 组件（如 EditText, Sora Editor, Rosemoe）。
 * 2. 携带 Tree-sitter 所需的起止字节索引与 (Row, Column) 坐标。
 *
 * 方法流程脑图：
 * Editor TextWatcher ──> 构建 TextEditEvent ──> NativeHighlightEngine.applyEdit()
 */
data class TextEditEvent(
    val startByte: Int,
    val oldEndByte: Int,
    val newEndByte: Int,
    val startRow: Int,
    val startColumn: Int,
    val oldEndRow: Int,
    val oldEndColumn: Int,
    val newEndRow: Int,
    val newEndColumn: Int,
    val insertedText: String
)