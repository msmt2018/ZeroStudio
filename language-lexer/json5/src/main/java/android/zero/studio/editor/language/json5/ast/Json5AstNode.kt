package android.zero.studio.editor.language.json5.ast

data class Json5AstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<Json5AstNode> = emptyList(),
)
