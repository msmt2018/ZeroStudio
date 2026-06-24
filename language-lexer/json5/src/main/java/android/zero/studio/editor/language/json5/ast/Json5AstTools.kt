package android.zero.studio.editor.language.json5.ast

import com.itsaky.androidide.lexers.json5.JSON5Lexer
import com.itsaky.androidide.lexers.json5.JSON5Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object Json5AstTools {

  fun parse(code: String): ParseTree {
    val lexer = JSON5Lexer(CharStreams.fromString(code))
    val parser = JSON5Parser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): Json5AstNode = build(parse(code))

  private fun build(tree: ParseTree): Json5AstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return Json5AstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: Json5AstNode, type: String): List<Json5AstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: Json5AstNode): List<Json5AstNode> = buildList {
    fun walk(n: Json5AstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
