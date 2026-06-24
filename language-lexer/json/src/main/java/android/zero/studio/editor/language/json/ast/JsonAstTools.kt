package android.zero.studio.editor.language.json.ast

import com.itsaky.androidide.lexers.JSON.JSONLexer
import com.itsaky.androidide.lexers.JSON.JSONParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object JsonAstTools {

  fun parse(code: String): ParseTree {
    val lexer = JSONLexer(CharStreams.fromString(code))
    val parser = JSONParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): JsonAstNode = build(parse(code))

  private fun build(tree: ParseTree): JsonAstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return JsonAstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: JsonAstNode, type: String): List<JsonAstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: JsonAstNode): List<JsonAstNode> = buildList {
    fun walk(n: JsonAstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
