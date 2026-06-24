package android.zero.studio.editor.language.kotlinformal.ast

data class KotlinformalAstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<KotlinformalAstNode> = emptyList(),
)
