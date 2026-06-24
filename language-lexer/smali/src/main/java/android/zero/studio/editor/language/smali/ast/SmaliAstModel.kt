package android.zero.studio.editor.language.smali.ast

import android.zero.studio.symbol.SymbolInfo

data class SmaliAstNode(
    val type: String,
    val text: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val children: List<SmaliAstNode> = emptyList(),
)

data class SmaliAstResult(
    val root: SmaliAstNode,
    val symbols: List<SymbolInfo>,
    val methodCount: Int,
    val fieldCount: Int,
)
