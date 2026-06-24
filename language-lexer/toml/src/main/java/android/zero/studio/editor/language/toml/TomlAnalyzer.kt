package android.zero.studio.editor.language.toml

import com.itsaky.androidide.editor.language.incremental.BaseIncrementalAnalyzeManager
import com.itsaky.androidide.editor.language.incremental.IncrementalToken
import com.itsaky.androidide.editor.language.incremental.LineState
import com.itsaky.androidide.lexers.toml.TomlLexer
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager.LineTokenizeResult
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.TextStyle

/**
 * TOML 语言的增量语法分析器。
 * 
 * <p>该类负责在用户编辑文档时，增量地对文本进行词法分析，并将 [TomlLexer] 生成的 Token 
 * 转换为编辑器支持的 [Span] 高亮区块。
 * 
 * <p><b>工作流程</b>:
 * Editor Text Changed -> 触发 tokenizeLine -> 调用 TomlLexer 扫描变更行 -> 生成 IncrementalToken 列表 -> 调用 generateSpans 映射颜色 -> 渲染到屏幕。
 * 
 * @author android_zero
 */
class TomlAnalyzer : BaseIncrementalAnalyzeManager(TomlLexer::class.java) {

    /**
     * 定义多行 Token 的起始和结束标记。
     * 对于 TOML 而言，主要是多行基本字符串和多行字面量字符串。
     */
    override fun getMultilineTokenStartEndTypes(): Array<IntArray> {
        // TOML 使用 """ 或 ''' 作为多行字符串标记
        val start = intArrayOf(TomlLexer.ML_BASIC_STRING, TomlLexer.ML_LITERAL_STRING)
        val end = intArrayOf(TomlLexer.ML_BASIC_STRING, TomlLexer.ML_LITERAL_STRING)
        return arrayOf(start, end)
    }

    /**
     * 定义代码块折叠和缩进匹配的符号对。
     */
    override fun getCodeBlockTokens(): IntArray {
        return intArrayOf(TomlLexer.L_BRACE, TomlLexer.R_BRACE, TomlLexer.L_BRACKET, TomlLexer.R_BRACKET)
    }

    /**
     * 为给定的 Token 列表生成对应的高亮 Span 列表。
     *
     * @param tokens 包含词法状态和增量 Token 列表的结果集
     * @return 映射后的高亮 [Span] 列表
     */
    override fun generateSpans(tokens: LineTokenizeResult<LineState, IncrementalToken>): List<Span> {
        val spans = mutableListOf<Span>()
        var first = true

        for (token in tokens.tokens) {
            val type = token.type
            val offset = token.startIndex

            // 跳过初始空白符的高亮，保持默认
            if (first && type == TomlLexer.WS) {
                spans.add(SpanFactory.obtain(offset, TextStyle.makeStyle(SchemeAndroidIDE.TEXT_NORMAL)))
                first = false
                continue
            }

            when (type) {
                TomlLexer.COMMENT -> 
                    handleLineCommentSpan(token, spans, offset)

                TomlLexer.UNQUOTED_KEY,
                TomlLexer.INLINE_TABLE_KEY_UNQUOTED -> 
                    spans.add(SpanFactory.obtain(offset, SchemeAndroidIDE.forKeyword()))

                TomlLexer.BOOLEAN, 
                TomlLexer.DEC_INT, 
                TomlLexer.HEX_INT, 
                TomlLexer.OCT_INT, 
                TomlLexer.BIN_INT, 
                TomlLexer.FLOAT, 
                TomlLexer.INF, 
                TomlLexer.NAN, 
                TomlLexer.OFFSET_DATE_TIME, 
                TomlLexer.LOCAL_DATE_TIME, 
                TomlLexer.LOCAL_DATE, 
                TomlLexer.LOCAL_TIME -> 
                    spans.add(SpanFactory.obtain(offset, TextStyle.makeStyle(SchemeAndroidIDE.LITERAL)))

                TomlLexer.BASIC_STRING, 
                TomlLexer.LITERAL_STRING, 
                TomlLexer.ML_BASIC_STRING, 
                TomlLexer.ML_LITERAL_STRING,
                TomlLexer.INLINE_TABLE_KEY_BASIC_STRING,
                TomlLexer.INLINE_TABLE_KEY_LITERAL_STRING -> 
                    spans.add(SpanFactory.obtain(offset, SchemeAndroidIDE.forString()))

                TomlLexer.L_BRACKET, 
                TomlLexer.R_BRACKET, 
                TomlLexer.DOUBLE_L_BRACKET, 
                TomlLexer.DOUBLE_R_BRACKET, 
                TomlLexer.L_BRACE, 
                TomlLexer.R_BRACE, 
                TomlLexer.EQUALS, 
                TomlLexer.INLINE_TABLE_EQUALS,
                TomlLexer.DOT, 
                TomlLexer.INLINE_TABLE_KEY_DOT,
                TomlLexer.COMMA,
                TomlLexer.INLINE_TABLE_COMMA -> 
                    spans.add(SpanFactory.obtain(offset, TextStyle.makeStyle(SchemeAndroidIDE.OPERATOR)))

                else -> 
                    spans.add(SpanFactory.obtain(offset, TextStyle.makeStyle(SchemeAndroidIDE.TEXT_NORMAL)))
            }
        }
        return spans
    }

    /**
     * 处理多行解析中断时的不完整 Token。
     */
    override fun handleIncompleteToken(token: IncrementalToken) {
        token.type = TomlLexer.ML_BASIC_STRING
    }
}