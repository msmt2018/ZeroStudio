package com.zerostudio.ide.treesitter.tsh

/**
 * @author android_zero
 *
 * 变更说明与设计架构：
 * 完整映射 Tree-sitter SCM 的模式、量词、锚点、谓词与否定字段语法，
 * 并支持单规则内多 Capture 独立样式的定义。
 */

enum class TshTokenType {
    METADATA, BEHAVIORS, HIGHLIGHTS,
    IDENTIFIER, STRING, NUMBER,
    LBRACE, RBRACE,         // { }
    LBRACKET, RBRACKET,     // [ ]
    LPAREN, RPAREN,         // ( )
    COLON, ARROW,           // : ->
    TAG,                    // @keyword
    RAW_SCM_PATTERN,        // 包含完整 S-expression
    EOF, ERROR
}

data class TshSourcePosition(val line: Int, val column: Int)

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
)

data class TshBehaviors(
    val lineComment: String? = null,
    val blockComment: Pair<String, String>? = null,
    val brackets: List<Pair<String, String>> = emptyList(),
    val autoClose: List<Pair<String, String>> = emptyList()
)

data class SingleCaptureStyle(
    val colorHex: String? = null,
    val bgColorHex: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val priority: Int = 100,
    val action: String? = null,
    val tooltip: String? = null
)

data class TshRule(
    val scmPatternWithPredicates: String,
    val captureStyles: Map<String, SingleCaptureStyle>,
    val position: TshSourcePosition
)

data class TshDocument(
    val metadata: TshMetadata,
    val behaviors: TshBehaviors,
    val rules: List<TshRule>
)

data class TshCompiledResult(
    val standardScmQuery: String,      // 编译输出的 100% 符合 Tree-sitter 官方标准的 SCM
    val flatStyleTable: IntArray,       // 格式: [PackedStyle, Priority, ActionId, ...]
    val captureCount: Int,
    val actionIdMap: Map<Int, String>,
    val behaviors: TshBehaviors,
    val metadata: TshMetadata
)