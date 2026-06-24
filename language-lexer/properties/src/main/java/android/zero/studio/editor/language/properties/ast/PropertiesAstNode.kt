package android.zero.studio.editor.language.properties.ast

data class PropertiesAstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<PropertiesAstNode> = emptyList(),
)
