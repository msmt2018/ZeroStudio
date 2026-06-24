package android.zero.studio.editor.language.json.ast

data class JsonAstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<JsonAstNode> = emptyList(),
)
