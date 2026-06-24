package android.zero.studio.editor.language.toml.ast

import com.itsaky.androidide.lexers.toml.TomlLexer
import com.itsaky.androidide.lexers.toml.TomlParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree

/** TOML AST 解析器。 */
object TomlAstParser {

    fun parse(code: String): ParseTree {
        val lexer = TomlLexer(CharStreams.fromString(code))
        val parser = TomlParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        return parser.document()
    }

    fun parseToNode(code: String): TomlAstNode {
        return buildNode(parse(code))
    }

    private fun buildNode(tree: ParseTree): TomlAstNode {
        val ctx = tree as? ParserRuleContext
        val children = (0 until tree.childCount).map { index -> buildNode(tree.getChild(index)) }
        return TomlAstNode(
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
