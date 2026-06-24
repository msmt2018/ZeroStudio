package android.zero.studio.editor.language.protobuf3.ast

data class Protobuf3AstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<Protobuf3AstNode> = emptyList(),
)
