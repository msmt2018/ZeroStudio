package android.zero.studio.editor.language.csv.ast

data class CsvAstNode(
  val type: String,
  val text: String,
  val startLine: Int,
  val endLine: Int,
  val children: List<CsvAstNode> = emptyList(),
)
