package android.zero.studio.editor.language.kotlin.ast

import com.itsaky.androidide.lexers.kotlin2.KotlinLexer
import com.itsaky.androidide.lexers.kotlin2.KotlinParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object KotlinAstTools {

  fun parse(code: String): ParseTree {
    val lexer = KotlinLexer(CharStreams.fromString(code))
    val parser = KotlinParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): KotlinAstNode = build(parse(code))

  private fun build(tree: ParseTree): KotlinAstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return KotlinAstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: KotlinAstNode, type: String): List<KotlinAstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: KotlinAstNode): List<KotlinAstNode> = buildList {
    fun walk(n: KotlinAstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
