package android.zero.studio.editor.language.toml.ast

/** TOML AST 遍历工具。 */
object TomlAstTraversal {

    fun depthFirst(root: TomlAstNode, visitor: (TomlAstNode) -> Unit) {
        visitor(root)
        root.children.forEach { child -> depthFirst(child, visitor) }
    }

    fun flatten(root: TomlAstNode): List<TomlAstNode> {
        val list = mutableListOf<TomlAstNode>()
        depthFirst(root) { node -> list.add(node) }
        return list
    }
}
