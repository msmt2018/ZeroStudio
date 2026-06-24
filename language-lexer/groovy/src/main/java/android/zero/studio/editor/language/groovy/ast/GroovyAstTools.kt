package android.zero.studio.editor.language.groovy.ast

import com.itsaky.androidide.lexers.groovy.GroovyLexer
import org.antlr.v4.runtime.CharStreams

object GroovyAstTools {
  fun lex(code: String): List<GroovyTokenNode> {
    val lexer = GroovyLexer(CharStreams.fromString(code))
    val out = mutableListOf<GroovyTokenNode>()
    while (true) {
      val token = lexer.nextToken()
      if (token.type == -1) break
      out += GroovyTokenNode(token.type, token.text ?: "", token.line - 1, token.charPositionInLine)
    }
    return out
  }

  fun query(tokens: List<GroovyTokenNode>, textPrefix: String): List<GroovyTokenNode> =
    tokens.filter { it.text.startsWith(textPrefix) }
}
