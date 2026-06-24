package android.zero.studio.editor.language.cmake.ast

import com.itsaky.androidide.lexers.cmake.CMakeLexer
import com.itsaky.androidide.lexers.cmake.CMakeParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object CmakeAstTools {

  fun parse(code: String): ParseTree {
    val lexer = CMakeLexer(CharStreams.fromString(code))
    val parser = CMakeParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): CmakeAstNode = build(parse(code))

  private fun build(tree: ParseTree): CmakeAstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return CmakeAstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: CmakeAstNode, type: String): List<CmakeAstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: CmakeAstNode): List<CmakeAstNode> = buildList {
    fun walk(n: CmakeAstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
