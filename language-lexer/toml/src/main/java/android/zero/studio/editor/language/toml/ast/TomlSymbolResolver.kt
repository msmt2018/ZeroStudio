package android.zero.studio.editor.language.toml.ast

import android.zero.studio.symbol.SymbolInfo
import android.zero.studio.symbol.SymbolType
import com.itsaky.androidide.lexers.toml.TomlLexer
import com.itsaky.androidide.lexers.toml.TomlParser
import com.itsaky.androidide.lexers.toml.TomlParserBaseVisitor
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree

/**
 * TOML 文档符号解析器。
 * 
 * <p>基于 ANTLR 生成的 [TomlParser] 解析源代码并构建 AST 语法树。
 * 提供对文本中定义的 Table (`[table]`)、Array Table (`[[array]]`) 以及键值对的符号抽取。
 * 
 * <p><b>工作流程</b>:
 * CharStreams -> TomlLexer -> CommonTokenStream -> TomlParser -> parser.document() 
 * -> 交由 [TomlSymbolVisitor] 遍历收集所有符号 -> 返回 SymbolInfo 列表用于代码大纲(Outline)和定义跳转。
 * 
 * @author android_zero
 */
object TomlSymbolResolver {

    /**
     * 解析 TOML 源代码并提取全部结构化符号。
     *
     * @param code TOML 源码文本
     * @return 包含解析结果的 [SymbolInfo] 列表
     */
    fun parseSymbols(code: String): List<SymbolInfo> {
        if (code.isBlank()) return emptyList()

        return try {
            val lexer = TomlLexer(CharStreams.fromString(code))
            val tokens = CommonTokenStream(lexer)
            val parser = TomlParser(tokens)
            
            // 移除默认错误监听器，防止在控制台大量输出语法错误日志
            parser.removeErrorListeners()
            
            val documentTree = parser.document()
            val visitor = TomlSymbolVisitor()
            visitor.visit(documentTree)
            
            visitor.getSymbols()
        } catch (e: Exception) {
            // 解析错误容错，不中断编辑器正常工作
            emptyList()
        }
    }

    /**
     * 内部访问者实现，负责从 AST 节点中检索表名和键名。
     */
    private class TomlSymbolVisitor : TomlParserBaseVisitor<Void?>() {
        private val symbols = mutableListOf<SymbolInfo>()
        private var currentTableName: String = ""

        fun getSymbols(): List<SymbolInfo> = symbols

        override fun visitStandard_table(ctx: TomlParser.Standard_tableContext): Void? {
            val keyCtx = ctx.key()
            if (keyCtx != null) {
                currentTableName = keyCtx.text
                val line = keyCtx.start.line - 1
                symbols.add(SymbolInfo(currentTableName, "Table", line, SymbolType.CLASS))
            }
            return super.visitStandard_table(ctx)
        }

        override fun visitArray_table(ctx: TomlParser.Array_tableContext): Void? {
            val keyCtx = ctx.key()
            if (keyCtx != null) {
                currentTableName = keyCtx.text
                val line = keyCtx.start.line - 1
                symbols.add(SymbolInfo(currentTableName, "Array Table", line, SymbolType.CLASS))
            }
            return super.visitArray_table(ctx)
        }

        override fun visitKey_value(ctx: TomlParser.Key_valueContext): Void? {
            val keyCtx = ctx.key()
            if (keyCtx != null) {
                val keyName = keyCtx.text
                val line = keyCtx.start.line - 1
                val parentInfo = if (currentTableName.isNotEmpty()) " in [$currentTableName]" else ""
                symbols.add(SymbolInfo(keyName, "Property$parentInfo", line, SymbolType.FIELD))
            }
            return super.visitKey_value(ctx)
        }
    }
}