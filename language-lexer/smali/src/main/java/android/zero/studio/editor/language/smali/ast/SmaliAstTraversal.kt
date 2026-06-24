package android.zero.studio.editor.language.smali.ast

object SmaliAstTraversal {

    fun walk(root: SmaliAstNode, visitor: (SmaliAstNode) -> Unit) {
        visitor(root)
        root.children.forEach { walk(it, visitor) }
    }

    fun flatten(root: SmaliAstNode): List<SmaliAstNode> = buildList { walk(root) { add(it) } }
}
