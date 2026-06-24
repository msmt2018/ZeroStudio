package android.zero.studio.editor.language.css3.ast

import com.itsaky.androidide.lexers.css3.css3Lexer
import com.itsaky.androidide.lexers.css3.css3Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object Css3AstTools {

  fun parse(code: String): ParseTree {
    val lexer = css3Lexer(CharStreams.fromString(code))
    val parser = css3Parser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): Css3AstNode = build(parse(code))

  private fun build(tree: ParseTree): Css3AstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return Css3AstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: Css3AstNode, type: String): List<Css3AstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: Css3AstNode): List<Css3AstNode> = buildList {
    fun walk(n: Css3AstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
