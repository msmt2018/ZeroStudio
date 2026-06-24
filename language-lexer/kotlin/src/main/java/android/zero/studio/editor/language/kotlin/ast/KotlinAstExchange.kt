package android.zero.studio.editor.language.kotlin.ast

/** Shared DTO for exchanging AST data between kotlin & kotlinformal toolchains. */
data class KotlinAstExchangeNode(
    val type: String,
    val text: String,
    val startLine: Int,
    val endLine: Int,
    val children: List<KotlinAstExchangeNode> = emptyList(),
)

object KotlinAstExchange {

  fun export(root: KotlinAstNode): KotlinAstExchangeNode {
    return KotlinAstExchangeNode(
        type = root.type,
        text = root.text,
        startLine = root.startLine,
        endLine = root.endLine,
        children = root.children.map(::export),
    )
  }

  fun queryByType(root: KotlinAstExchangeNode, type: String): List<KotlinAstExchangeNode> {
    return flatten(root).filter { it.type == type }
  }

  fun flatten(root: KotlinAstExchangeNode): List<KotlinAstExchangeNode> = buildList {
    fun walk(node: KotlinAstExchangeNode) {
      add(node)
      node.children.forEach(::walk)
    }
    walk(root)
  }
}
