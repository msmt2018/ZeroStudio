package android.zero.studio.editor.language.css3.ast

data class Css3AstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<Css3AstNode> = emptyList(),
)
