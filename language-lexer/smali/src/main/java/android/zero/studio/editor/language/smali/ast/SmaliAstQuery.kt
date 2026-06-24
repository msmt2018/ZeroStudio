package android.zero.studio.editor.language.smali.ast

object SmaliAstQuery {

    fun byType(root: SmaliAstNode, type: String): List<SmaliAstNode> {
        return SmaliAstTraversal.flatten(root).filter { it.type == type }
    }

    fun findContaining(root: SmaliAstNode, line: Int, column: Int): SmaliAstNode? {
        return SmaliAstTraversal.flatten(root).firstOrNull {
            line in it.startLine..it.endLine && column >= it.startColumn
        }
    }
}
