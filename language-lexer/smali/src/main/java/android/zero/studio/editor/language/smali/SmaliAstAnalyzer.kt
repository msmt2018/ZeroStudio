package android.zero.studio.editor.language.smali

import android.zero.studio.editor.language.smali.ast.SmaliAstParser
import android.zero.studio.editor.language.smali.ast.SmaliAstQuery
import android.zero.studio.editor.language.smali.ast.SmaliAstResult
import android.zero.studio.editor.language.smali.ast.SmaliSymbolResolver

/** Smali 综合分析器（解析 + 符号 + 常见查询）。 */
object SmaliAstAnalyzer {

    fun analyze(code: String): SmaliAstResult {
        val root = SmaliAstParser.parseToNode(code)
        val symbols = SmaliSymbolResolver.parseSymbols(code)
        val methods = SmaliAstQuery.byType(root, "MethodAltContext")
        val fields = SmaliAstQuery.byType(root, "FieldAltContext")
        return SmaliAstResult(root, symbols, methods.size, fields.size)
    }
}
