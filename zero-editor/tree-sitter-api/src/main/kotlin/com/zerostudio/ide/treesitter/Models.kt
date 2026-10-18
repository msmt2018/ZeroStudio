package com.zerostudio.ide.treesitter

/**
 * @author android_zero
 *
 * 变更说明：
 * 映射 Tree-sitter 基础行列与点范围数据结构，完全零反射、内存友好。
 */
data class TSPoint(val row: Int, val column: Int)

data class TSRange(
    val startByte: Int,
    val endByte: Int,
    val startPoint: TSPoint,
    val endPoint: TSPoint
)

data class TSInputEdit(
    val startByte: Int,
    val oldEndByte: Int,
    val newEndByte: Int,
    val startPoint: TSPoint,
    val oldEndPoint: TSPoint,
    val newEndPoint: TSPoint
)

data class CodeSymbolTag(
    val startByte: Int,
    val endByte: Int,
    val nameStartByte: Int,
    val nameEndByte: Int,
    val lineStartByte: Int,
    val lineEndByte: Int,
    val startPoint: TSPoint,
    val syntaxTypeId: Int,
    val isDefinition: Boolean
)