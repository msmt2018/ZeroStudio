package android.zero.studio.editor.language.ecmascript.ast

data class EcmascriptAstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<EcmascriptAstNode> = emptyList(),
)
