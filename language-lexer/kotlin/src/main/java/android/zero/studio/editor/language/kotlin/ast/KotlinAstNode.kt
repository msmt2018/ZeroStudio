package android.zero.studio.editor.language.kotlin.ast

data class KotlinAstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<KotlinAstNode> = emptyList(),
)
