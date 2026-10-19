package com.zerostudio.ide.treesitter.tsh

import java.io.Serializable

/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 1. 完整映射 Tree-sitter SCM 的 S-expression、量词、锚点、谓词与否定字段语法。
 * 2. 支持单规则多 Capture 独立样式与优先级分配。
 * 3. 增强支持序列化（Serializable），为后续 `.tshc` 二进制冷启缓存提供基础。
 */

enum class TshTokenType {
    METADATA, BEHAVIORS, HIGHLIGHTS,
    IDENTIFIER, STRING, NUMBER,
    LBRACE, RBRACE,         // { }
    LBRACKET, RBRACKET,     // [ ]
    COLON, ARROW,           // : ->
    TAG,                    // @keyword
    RAW_SCM_PATTERN,        // 纯净 S-Expression
    EOF, ERROR
}

data class TshSourcePosition(val line: Int, val column: Int) : Serializable

data class TshToken(
    val type: TshTokenType,
    val value: String,
    val position: TshSourcePosition
)

data class TshMetadata(
    val name: String,
    val extensions: List<String>,
    val scope: String,
    val version: String
) : Serializable

data class TshBehaviors(
    val lineComment: String? = null,
    val blockComment: Pair<String, String>? = null,
    val brackets: List<Pair<String, String>> = emptyList(),
    val autoClose: List<Pair<String, String>> = emptyList()
) : Serializable

data class SingleCaptureStyle(
    val colorHex: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val priority: Int = 100,
    val action: String? = null,
    val tooltip: String? = null
) : Serializable

data class TshRule(
    val scmPatternWithPredicates: String,
    val captureStyles: Map<String, SingleCaptureStyle>,
    val position: TshSourcePosition
) : Serializable

data class TshDocument(
    val metadata: TshMetadata,
    val behaviors: TshBehaviors,
    val rules: List<TshRule>
) : Serializable

/**
 * 编译产物包（可直接序列化为 .tshc 二进制文件）
 */
data class TshCompiledResult(
    val standardScmQuery: String,      // 编译输出的 100% 符合 Tree-sitter 官方 C 核心标准的 SCM 查询
    val flatStyleTable: IntArray,       // 每项 4 个 int：[RGBA, StyleFlags, Priority, ActionId]
    val captureCount: Int,
    val actionIdMap: Map<Int, String>,  // ActionId -> 动作描述标识
    val behaviors: TshBehaviors,
    val metadata: TshMetadata
) : Serializable