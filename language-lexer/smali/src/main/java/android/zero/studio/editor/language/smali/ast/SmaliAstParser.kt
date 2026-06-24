package android.zero.studio.editor.language.smali.ast

import com.itsaky.androidide.lexers.smali.SmaliLexer
import com.itsaky.androidide.lexers.smali.SmaliParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

object SmaliAstParser {

    fun parse(code: String): ParseTree {
        val lexer = SmaliLexer(CharStreams.fromString(code))
        val parser = SmaliParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        return parser.smali()
    }

    fun parseToNode(code: String): SmaliAstNode = buildNode(parse(code))

    private fun buildNode(tree: ParseTree): SmaliAstNode {
        val ctx = tree as? ParserRuleContext
        val children = (0 until tree.childCount).map { buildNode(tree.getChild(it)) }
        return SmaliAstNode(
            type = tree.javaClass.simpleName,
            text = tree.text.orEmpty(),
            startLine = (ctx?.start?.line ?: 1) - 1,
            startColumn = ctx?.start?.charPositionInLine ?: 0,
            endLine = (ctx?.stop?.line ?: ctx?.start?.line ?: 1) - 1,
            endColumn = ((ctx?.stop?.charPositionInLine ?: 0) + (ctx?.stop?.text?.length ?: 0)).coerceAtLeast(0),
            children = children,
        )
    }
}
