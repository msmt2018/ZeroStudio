package android.zero.studio.editor.language.ecmascript.ast

import com.itsaky.androidide.lexers.ecmascript.ECMAScriptLexer
import com.itsaky.androidide.lexers.ecmascript.ECMAScriptParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object EcmascriptAstTools {

  fun parse(code: String): ParseTree {
    val lexer = ECMAScriptLexer(CharStreams.fromString(code))
    val parser = ECMAScriptParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): EcmascriptAstNode = build(parse(code))

  private fun build(tree: ParseTree): EcmascriptAstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return EcmascriptAstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: EcmascriptAstNode, type: String): List<EcmascriptAstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: EcmascriptAstNode): List<EcmascriptAstNode> = buildList {
    fun walk(n: EcmascriptAstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
