package android.zero.studio.editor.language.javascript.ast

data class JavascriptAstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<JavascriptAstNode> = emptyList(),
)
