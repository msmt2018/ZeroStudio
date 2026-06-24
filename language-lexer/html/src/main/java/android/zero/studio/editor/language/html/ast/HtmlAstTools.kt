package android.zero.studio.editor.language.html.ast

import com.itsaky.androidide.lexers.html.HTMLLexer
import com.itsaky.androidide.lexers.html.HTMLParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object HtmlAstTools {

  fun parse(code: String): ParseTree {
    val lexer = HTMLLexer(CharStreams.fromString(code))
    val parser = HTMLParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): HtmlAstNode = build(parse(code))

  private fun build(tree: ParseTree): HtmlAstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return HtmlAstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: HtmlAstNode, type: String): List<HtmlAstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: HtmlAstNode): List<HtmlAstNode> = buildList {
    fun walk(n: HtmlAstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
