package android.zero.studio.editor.language.properties.ast

import com.itsaky.androidide.lexers.properties.PropertiesLexer
import com.itsaky.androidide.lexers.properties.PropertiesParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object PropertiesAstTools {

  fun parse(code: String): ParseTree {
    val lexer = PropertiesLexer(CharStreams.fromString(code))
    val parser = PropertiesParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): PropertiesAstNode = build(parse(code))

  private fun build(tree: ParseTree): PropertiesAstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return PropertiesAstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: PropertiesAstNode, type: String): List<PropertiesAstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: PropertiesAstNode): List<PropertiesAstNode> = buildList {
    fun walk(n: PropertiesAstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
