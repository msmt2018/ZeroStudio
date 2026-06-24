package android.zero.studio.editor.language.protobuf3.ast

import com.itsaky.androidide.lexers.protobuf3.Protobuf3Lexer
import com.itsaky.androidide.lexers.protobuf3.Protobuf3Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object Protobuf3AstTools {

  fun parse(code: String): ParseTree {
    val lexer = Protobuf3Lexer(CharStreams.fromString(code))
    val parser = Protobuf3Parser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): Protobuf3AstNode = build(parse(code))

  private fun build(tree: ParseTree): Protobuf3AstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return Protobuf3AstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: Protobuf3AstNode, type: String): List<Protobuf3AstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: Protobuf3AstNode): List<Protobuf3AstNode> = buildList {
    fun walk(n: Protobuf3AstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
