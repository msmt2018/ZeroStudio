package android.zero.studio.editor.language.kotlinformal.ast

import com.itsaky.androidide.lexers.kotlinformal.KotlinLexer
import com.itsaky.androidide.lexers.kotlinformal.KotlinParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object KotlinformalAstTools {

  fun parse(code: String): ParseTree {
    val lexer = KotlinLexer(CharStreams.fromString(code))
    val parser = KotlinParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    return parser.javaClass.methods.first { it.parameterCount == 0 && it.returnType.simpleName.endsWith("Context") }
      .invoke(parser) as ParseTree
  }

  fun toNode(code: String): KotlinformalAstNode = build(parse(code))

  private fun build(tree: ParseTree): KotlinformalAstNode {
    val ctx = tree as? ParserRuleContext
    val children = (0 until tree.childCount).map { build(tree.getChild(it)) }
    return KotlinformalAstNode(
      type = tree.javaClass.simpleName,
      text = tree.text.orEmpty(),
      startLine = (ctx?.start?.line ?: 1) - 1,
      endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
      children = children,
    )
  }

  fun queryByType(root: KotlinformalAstNode, type: String): List<KotlinformalAstNode> =
    flatten(root).filter { it.type == type }

  fun flatten(root: KotlinformalAstNode): List<KotlinformalAstNode> = buildList {
    fun walk(n: KotlinformalAstNode) {
      add(n)
      n.children.forEach(::walk)
    }
    walk(root)
  }
}
