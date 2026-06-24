package android.zero.studio.editor.language.csv.ast

import com.itsaky.androidide.lexers.csv.CSVLexer
import com.itsaky.androidide.lexers.csv.CSVParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object CsvAstTools {

  fun parse(code: String): ParseTree {
    val lexer = CSVLexer(CharStreams.fromString(code))
    val parser = CSVParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): CsvAstNode = build(parse(code))

  private fun build(tree: ParseTree): CsvAstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return CsvAstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: CsvAstNode, type: String): List<CsvAstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: CsvAstNode): List<CsvAstNode> = buildList {
    fun walk(n: CsvAstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
