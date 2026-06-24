package android.zero.studio.editor.language.html.ast

data class HtmlAstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<HtmlAstNode> = emptyList(),
)
