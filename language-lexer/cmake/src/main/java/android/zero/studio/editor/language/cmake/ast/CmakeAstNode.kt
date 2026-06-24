package android.zero.studio.editor.language.cmake.ast

data class CmakeAstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<CmakeAstNode> = emptyList(),
)
