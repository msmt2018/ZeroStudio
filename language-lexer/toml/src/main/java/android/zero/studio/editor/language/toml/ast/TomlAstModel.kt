package android.zero.studio.editor.language.toml.ast

import android.zero.studio.symbol.SymbolInfo

/** TOML AST 节点模型。 */
data class TomlAstNode(
    val type: String,
    val text: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val children: List<TomlAstNode> = emptyList(),
)

/** TOML 文档分析结果。 */
data class TomlAstResult(
    val root: TomlAstNode,
    val symbols: List<SymbolInfo>,
)
