package android.zero.studio.editor.language.toml.ast

/** TOML AST 查询器。 */
object TomlAstQuery {

    fun byType(root: TomlAstNode, type: String): List<TomlAstNode> {
        return TomlAstTraversal.flatten(root).filter { it.type == type }
    }

    fun firstAtLine(root: TomlAstNode, line: Int): TomlAstNode? {
        return TomlAstTraversal.flatten(root)
            .firstOrNull { line in it.startLine..it.endLine }
    }
}
