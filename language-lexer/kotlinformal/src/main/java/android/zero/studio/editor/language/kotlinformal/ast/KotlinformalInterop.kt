package android.zero.studio.editor.language.kotlinformal.ast

import android.zero.studio.editor.language.kotlin.ast.KotlinAstExchange
import android.zero.studio.editor.language.kotlin.ast.KotlinAstExchangeNode

/** kotlinformal <-> kotlin AST interop helpers. */
object KotlinformalInterop {

  fun exportFormal(root: KotlinformalAstNode): KotlinAstExchangeNode {
    return KotlinAstExchangeNode(
        type = root.type,
        text = root.text,
        startLine = root.startLine,
        endLine = root.endLine,
        children = root.children.map(::exportFormal),
    )
  }

  fun queryWithKotlinEngine(root: KotlinformalAstNode, type: String): List<KotlinAstExchangeNode> {
    return KotlinAstExchange.queryByType(exportFormal(root), type)
  }
}
